package com.ethioloadai.delivery.service;

import com.ethioloadai.booking.entity.Booking;
import com.ethioloadai.carrier.team.service.CompanySecurityService;
import com.ethioloadai.delivery.dto.ProofOfDeliveryResponse;
import com.ethioloadai.delivery.dto.VerifyDeliveryRequest;
import com.ethioloadai.delivery.entity.ProofOfDelivery;
import com.ethioloadai.delivery.entity.ProofOfDelivery.ProofStatus;
import com.ethioloadai.delivery.exception.ProofOfDeliveryException;
import com.ethioloadai.delivery.repository.ProofOfDeliveryRepository;
import com.ethioloadai.document.storage.DocumentStorage;
import com.ethioloadai.freight.entity.Freight;
import com.ethioloadai.notification.service.NotificationStoreService;
import com.ethioloadai.settlement.service.CarrierSettlementService;
import com.ethioloadai.trip.entity.Trip;
import com.ethioloadai.trip.exception.TripNotFoundException;
import com.ethioloadai.trip.repository.TripRepository;
import com.ethioloadai.trip.service.TripLifecycleService;
import com.ethioloadai.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Phase 7 — Proof of Delivery service implementation.
 *
 * <h2>Happy path flow</h2>
 * <ol>
 *   <li>Driver calls {@link #submitProof} (trip must be ARRIVED).</li>
 *   <li>File saved to {@link DocumentStorage}. ProofOfDelivery created (SUBMITTED).</li>
 *   <li>Trip transitions → DELIVERY_PENDING_VERIFICATION.</li>
 *   <li>Shipper notified.</li>
 *   <li>Shipper calls {@link #verifyOrRejectProof} with action=VERIFY.</li>
 *   <li>Proof → VERIFIED. Trip → COMPLETED. Booking → DELIVERED. Freight → DELIVERED.</li>
 *   <li>{@link CarrierSettlementService} creates the settlement atomically.</li>
 * </ol>
 *
 * <h2>Rejection flow</h2>
 * <ol>
 *   <li>Shipper calls with action=REJECT.</li>
 *   <li>Proof → REJECTED. Trip reverts → ARRIVED.</li>
 *   <li>Driver may upload a new proof (previous unique constraint is satisfied because
 *       DB allows a new row once old one is REJECTED — actually the unique constraint
 *       is on trip_id, so we reuse the same row via UPDATE semantics:
 *       the service resets status back to SUBMITTED on resubmission).</li>
 * </ol>
 *
 * <h2>Security</h2>
 * <ul>
 *   <li>storageKey is NEVER returned to any client.</li>
 *   <li>downloadProof verifies authorization before returning bytes.</li>
 *   <li>Only the trip's assigned driver may submit proof.</li>
 *   <li>Only the freight's shipper or ADMIN may verify/reject.</li>
 *   <li>Driver cannot self-verify.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class ProofOfDeliveryServiceImpl implements ProofOfDeliveryService {

    private static final Logger log = LoggerFactory.getLogger(ProofOfDeliveryServiceImpl.class);

    // 10 MB — matches spring.servlet.multipart.max-file-size
    private static final long MAX_FILE_SIZE_BYTES = 10L * 1024 * 1024;
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg", "image/jpg", "image/png", "image/webp", "application/pdf"
    );

    private final ProofOfDeliveryRepository proofRepo;
    private final TripRepository tripRepository;
    private final TripLifecycleService tripLifecycleService;
    private final CarrierSettlementService settlementService;
    private final DocumentStorage documentStorage;
    private final NotificationStoreService notificationService;
    private final CompanySecurityService companySecurityService;

    // ── Submit proof ──────────────────────────────────────────────────────────

    @Override
    @Transactional
    public ProofOfDeliveryResponse submitProof(Long tripId, MultipartFile file,
                                               String recipientName, String note,
                                               User driver) {
        log.info("submitProof: tripId={} driverId={}", tripId, driver.getId());

        // 1. Load and validate trip
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> TripNotFoundException.withId(tripId));

        // 2. IDOR: only the assigned driver may submit
        if (!trip.getDriver().getId().equals(driver.getId())) {
            throw ProofOfDeliveryException.unauthorized(
                    "only the assigned driver may submit proof for this trip");
        }

        // 3. Trip must be ARRIVED
        if (trip.getTripStatus() != Trip.TripStatus.ARRIVED) {
            throw ProofOfDeliveryException.tripNotArrived(tripId);
        }

        // 4. Check if a VERIFIED proof already exists (cannot resubmit after verification)
        proofRepo.findByTrip_Id(tripId).ifPresent(existing -> {
            if (existing.getStatus() == ProofStatus.VERIFIED) {
                throw ProofOfDeliveryException.alreadyVerified(existing.getId());
            }
            // SUBMITTED or REJECTED — delete old storage and record, then resubmit
            if (existing.getStatus() == ProofStatus.SUBMITTED
                    || existing.getStatus() == ProofStatus.REJECTED) {
                try { documentStorage.delete(existing.getStorageKey()); }
                catch (Exception e) {
                    log.warn("submitProof: failed to delete old storage key {}: {}",
                            existing.getStorageKey(), e.getMessage());
                }
                proofRepo.delete(existing);
                proofRepo.flush();
                log.info("submitProof: deleted previous {} proof for trip {}", existing.getStatus(), tripId);
            }
        });

        // 5. Validate file
        validateFile(file);

        // 6. Store file
        String storageKey = "proof/" + tripId + "/" + UUID.randomUUID() + "/" + sanitizeFilename(file.getOriginalFilename());
        try {
            documentStorage.put(storageKey, file);
        } catch (IOException e) {
            throw new RuntimeException("Failed to store proof of delivery file: " + e.getMessage(), e);
        }

        // 7. Create proof record
        Booking booking = trip.getBooking();
        Freight freight = booking.getCargoRequest();

        ProofOfDelivery proof = ProofOfDelivery.builder()
                .trip(trip)
                .booking(booking)
                .freight(freight)
                .uploadedBy(driver)
                .storageKey(storageKey)
                .fileName(file.getOriginalFilename() != null
                        ? file.getOriginalFilename() : "proof")
                .contentType(file.getContentType())
                .fileSize(file.getSize())
                .recipientName(recipientName)
                .note(note)
                .status(ProofStatus.SUBMITTED)
                .uploadedAt(LocalDateTime.now())
                .build();

        try {
            proof = proofRepo.save(proof);
        } catch (DataIntegrityViolationException ex) {
            // Race condition: another concurrent submit beat us; return existing
            documentStorage.delete(storageKey);
            return proofRepo.findByTrip_Id(tripId)
                    .map(p -> ProofOfDeliveryResponse.from(p))
                    .orElseThrow(() -> new RuntimeException("Concurrent proof submission conflict"));
        }

        // 8. Transition trip → DELIVERY_PENDING_VERIFICATION
        tripLifecycleService.markDeliveryPendingVerification(tripId);

        // 9. Notify shipper
        try {
            notificationService.recordNotification(
                    freight.getShipper().getId(),
                    "PROOF_SUBMITTED",
                    Map.of(
                            "tripId", tripId,
                            "bookingId", booking.getId(),
                            "freightId", freight.getId(),
                            "proofId", proof.getId(),
                            "driverId", driver.getId(),
                            "message", "Proof of delivery submitted. Please verify your shipment."
                    ));
        } catch (Exception e) {
            log.warn("submitProof: notification failed (non-fatal): {}", e.getMessage());
        }

        log.info("submitProof: proof {} created for trip {}", proof.getId(), tripId);
        return ProofOfDeliveryResponse.from(proof);
    }

    // ── Download proof ────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public DocumentStorage.StoredDocument downloadProof(Long proofId, User caller) {
        log.info("downloadProof: proofId={} callerId={}", proofId, caller.getId());

        ProofOfDelivery proof = proofRepo.findById(proofId)
                .orElseThrow(() -> ProofOfDeliveryException.notFound(proofId));

        authorizeProofAccess(proof, caller);

        return documentStorage.get(proof.getStorageKey(), proof.getFileName(), proof.getContentType());
    }

    // ── Get proof metadata ────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public ProofOfDeliveryResponse getProofForTrip(Long tripId, User caller) {
        ProofOfDelivery proof = proofRepo.findByTrip_Id(tripId)
                .orElseThrow(() -> ProofOfDeliveryException.notFoundForTrip(tripId));
        authorizeProofAccess(proof, caller);
        return ProofOfDeliveryResponse.from(proof);
    }

    @Override
    @Transactional(readOnly = true)
    public ProofOfDeliveryResponse getProofById(Long proofId, User caller) {
        ProofOfDelivery proof = proofRepo.findById(proofId)
                .orElseThrow(() -> ProofOfDeliveryException.notFound(proofId));
        authorizeProofAccess(proof, caller);
        return ProofOfDeliveryResponse.from(proof);
    }

    // ── Verify / reject ───────────────────────────────────────────────────────

    @Override
    @Transactional
    public ProofOfDeliveryResponse verifyOrRejectProof(Long proofId,
                                                       VerifyDeliveryRequest request,
                                                       User caller) {
        log.info("verifyOrRejectProof: proofId={} action={} callerId={}",
                proofId, request.action(), caller.getId());

        ProofOfDelivery proof = proofRepo.findById(proofId)
                .orElseThrow(() -> ProofOfDeliveryException.notFound(proofId));

        // Only shipper or ADMIN may verify/reject
        authorizeVerification(proof, caller);

        if (proof.getStatus() == ProofStatus.VERIFIED) {
            log.info("verifyOrRejectProof: proof {} already VERIFIED — idempotent", proofId);
            return ProofOfDeliveryResponse.from(proof);
        }

        if (proof.getStatus() != ProofStatus.SUBMITTED) {
            throw ProofOfDeliveryException.invalidState(
                    "Proof must be in SUBMITTED status to verify/reject (current: " + proof.getStatus() + ")");
        }

        String action = request.action() != null ? request.action().trim().toUpperCase() : "";

        return switch (action) {
            case "VERIFY" -> handleVerify(proof, caller);
            case "REJECT" -> handleReject(proof, request, caller);
            default -> throw ProofOfDeliveryException.invalidState(
                    "Invalid action '" + request.action() + "'. Allowed: VERIFY, REJECT");
        };
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private ProofOfDeliveryResponse handleVerify(ProofOfDelivery proof, User reviewer) {
        proof.setStatus(ProofStatus.VERIFIED);
        proof.setReviewedBy(reviewer);
        proof.setReviewedAt(LocalDateTime.now());
        proofRepo.save(proof);

        Long tripId = proof.getTrip().getId();
        Long bookingId = proof.getBooking().getId();
        Long freightId = proof.getFreight().getId();
        Long carrierId = proof.getBooking().getCarrierCompanyId();
        Long driverId = proof.getUploadedBy().getId();

        log.info("handleVerify: proof {} VERIFIED — completing trip {}", proof.getId(), tripId);

        // Complete trip + update all statuses
        tripLifecycleService.completeTripAfterVerification(tripId);

        // Create carrier settlement (idempotent)
        if (carrierId != null) {
            try {
                settlementService.createSettlement(bookingId, proof.getId());
            } catch (Exception e) {
                // Settlement failure must not roll back the verified delivery
                log.error("handleVerify: settlement creation failed for booking {} — delivery stays VERIFIED: {}",
                        bookingId, e.getMessage());
            }
        }

        // Notify driver
        try {
            notificationService.recordNotification(driverId, "DELIVERY_VERIFIED",
                    Map.of("tripId", tripId, "bookingId", bookingId,
                           "message", "Your delivery has been verified by the shipper."));
        } catch (Exception e) { log.warn("Notification failed (non-fatal): {}", e.getMessage()); }

        // Notify carrier (if carrier dispatch booking)
        if (carrierId != null) {
            try {
                notificationService.recordNotification(
                        proof.getBooking().getDriver().getId(),
                        "SETTLEMENT_CREATED",
                        Map.of("bookingId", bookingId, "freightId", freightId,
                               "carrierCompanyId", carrierId,
                               "message", "Settlement has been created for completed delivery."));
            } catch (Exception e) { log.warn("Settlement notification failed (non-fatal): {}", e.getMessage()); }
        }

        log.info("handleVerify: trip {} completed, settlement initiated for booking {}", tripId, bookingId);
        return ProofOfDeliveryResponse.from(proof);
    }

    private ProofOfDeliveryResponse handleReject(ProofOfDelivery proof,
                                                  VerifyDeliveryRequest request,
                                                  User reviewer) {
        if (request.rejectionReason() == null || request.rejectionReason().isBlank()) {
            throw ProofOfDeliveryException.invalidState("rejectionReason is required when rejecting proof");
        }

        proof.setStatus(ProofStatus.REJECTED);
        proof.setReviewedBy(reviewer);
        proof.setReviewedAt(LocalDateTime.now());
        proof.setRejectionReason(request.rejectionReason().trim());
        proofRepo.save(proof);

        // Revert trip to ARRIVED so driver can resubmit
        tripLifecycleService.revertToArrived(proof.getTrip().getId());

        Long driverId = proof.getUploadedBy().getId();
        Long tripId = proof.getTrip().getId();

        // Notify driver
        try {
            notificationService.recordNotification(driverId, "PROOF_REJECTED",
                    Map.of("tripId", tripId, "proofId", proof.getId(),
                           "reason", proof.getRejectionReason(),
                           "message", "Your proof of delivery was rejected. Please resubmit."));
        } catch (Exception e) { log.warn("Notification failed (non-fatal): {}", e.getMessage()); }

        log.info("handleReject: proof {} REJECTED for trip {}, reason: {}",
                proof.getId(), tripId, proof.getRejectionReason());
        return ProofOfDeliveryResponse.from(proof);
    }

    /**
     * Authorization for reading proof metadata or downloading file.
     * Allowed: assigned driver, freight shipper, carrier member, ADMIN.
     */
    private void authorizeProofAccess(ProofOfDelivery proof, User caller) {
        if (caller.getRole() == User.Role.ADMIN) return;

        Long callerId = caller.getId();
        boolean isDriver = proof.getUploadedBy().getId().equals(callerId);
        boolean isShipper = proof.getFreight().getShipper().getId().equals(callerId);
        Long carrierId = proof.getBooking().getCarrierCompanyId();
        boolean isCarrierMember = (carrierId != null)
                && companySecurityService.isActiveMemberOf(callerId, carrierId);

        if (!isDriver && !isShipper && !isCarrierMember) {
            throw ProofOfDeliveryException.unauthorized(
                    "you are not authorized to access this proof of delivery");
        }
    }

    /**
     * Only the freight's SHIPPER or ADMIN may verify/reject.
     * Driver and carrier cannot self-verify.
     */
    private void authorizeVerification(ProofOfDelivery proof, User caller) {
        if (caller.getRole() == User.Role.ADMIN) return;
        if (proof.getFreight().getShipper().getId().equals(caller.getId())) return;
        throw ProofOfDeliveryException.unauthorized(
                "only the shipment's shipper or an administrator may verify or reject proof of delivery");
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw ProofOfDeliveryException.emptyFile();
        }
        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw ProofOfDeliveryException.fileTooLarge(file.getSize(), MAX_FILE_SIZE_BYTES);
        }
        String ct = file.getContentType();
        if (ct == null || !ALLOWED_CONTENT_TYPES.contains(ct.toLowerCase())) {
            throw ProofOfDeliveryException.invalidFileType(ct);
        }
    }

    private String sanitizeFilename(String name) {
        if (name == null || name.isBlank()) return "proof";
        // Remove path separators and dangerous characters
        return name.replaceAll("[^a-zA-Z0-9._-]", "_").substring(
                Math.max(0, name.lastIndexOf('/') + 1));
    }
}
