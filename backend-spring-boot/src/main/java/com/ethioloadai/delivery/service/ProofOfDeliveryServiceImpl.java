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
import com.ethioloadai.settlement.service.SettlementOutboxService;
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
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.Locale;
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
 *   <li>Settlement creation is queued transactionally and retried independently.</li>
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
    private final SettlementOutboxService settlementOutboxService;
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
        ProofOfDelivery existing = proofRepo.findByTrip_IdForUpdate(tripId).orElse(null);
        if (existing != null && existing.getStatus() == ProofStatus.VERIFIED) {
            throw ProofOfDeliveryException.alreadyVerified(existing.getId());
        }

        // 5. Validate file
        validateFile(file);
        validateMetadata(recipientName, note);

        // 6. Store the new object before changing the proof row. Storage is not transactional:
        //    rollback removes the new object; commit removes the previous object.
        String storageKey = "proof/" + tripId + "/" + UUID.randomUUID() + "/" + sanitizeFilename(file.getOriginalFilename());
        registerStorageCompensation(storageKey, existing == null ? null : existing.getStorageKey());
        try {
            documentStorage.put(storageKey, file);
        } catch (IOException e) {
            throw new RuntimeException("Failed to store proof of delivery file: " + e.getMessage(), e);
        }

        // 7. Replace the single-per-trip row only after the new object has been stored.
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

        if (existing != null) {
            proofRepo.delete(existing);
            proofRepo.flush();
            log.info("submitProof: replaced previous {} proof for trip {}", existing.getStatus(), tripId);
        }

        try {
            proof = existing == null ? proofRepo.save(proof) : proofRepo.saveAndFlush(proof);
        } catch (DataIntegrityViolationException ex) {
            if (existing != null) {
                throw ex;
            }
            // Race condition: another concurrent submit beat us; return existing
            deleteQuietly(storageKey);
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

    private void validateMetadata(String recipientName, String note) {
        if (recipientName != null && recipientName.length() > 150) {
            throw ProofOfDeliveryException.invalidMetadata(
                    "Recipient name must not exceed 150 characters");
        }
        if (note != null && note.length() > 1000) {
            throw ProofOfDeliveryException.invalidMetadata(
                    "Proof of delivery note must not exceed 1000 characters");
        }
    }

    private void registerStorageCompensation(String newKey, String previousKey) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException("Proof submission requires an active transaction");
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_COMMITTED) {
                    if (previousKey != null) {
                        deleteQuietly(previousKey);
                    }
                } else if (status == STATUS_ROLLED_BACK) {
                    deleteQuietly(newKey);
                } else {
                    log.warn("submitProof: transaction outcome unknown; retaining proof storage objects for reconciliation");
                }
            }
        });
    }

    private void deleteQuietly(String storageKey) {
        try {
            documentStorage.delete(storageKey);
        } catch (RuntimeException e) {
            log.warn("submitProof: storage cleanup failed; reconciliation required");
        }
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

        ProofOfDelivery proof = proofRepo.findByIdForUpdate(proofId)
                .orElseThrow(() -> ProofOfDeliveryException.notFound(proofId));

        // Only shipper or ADMIN may verify/reject
        authorizeVerification(proof, caller);

        String action = request.action() != null ? request.action().trim().toUpperCase() : "";
        if (proof.getStatus() == ProofStatus.VERIFIED) {
            if ("VERIFY".equals(action)) {
                settlementOutboxService.enqueueVerifiedDelivery(
                        proof.getBooking().getId(), proof.getId());
            }
            log.info("verifyOrRejectProof: proof {} already VERIFIED — idempotent", proofId);
            return ProofOfDeliveryResponse.from(proof);
        }

        if (proof.getStatus() != ProofStatus.SUBMITTED) {
            throw ProofOfDeliveryException.invalidState(
                    "Proof must be in SUBMITTED status to verify/reject (current: " + proof.getStatus() + ")");
        }

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
        Long carrierId = proof.getBooking().getCarrierCompanyId();
        Long driverId = proof.getUploadedBy().getId();

        log.info("handleVerify: proof {} VERIFIED — completing trip {}", proof.getId(), tripId);

        // Complete trip + update all statuses
        tripLifecycleService.completeTripAfterVerification(tripId);

        // Persist settlement work atomically; processing runs after this transaction commits.
        if (carrierId != null) {
            settlementOutboxService.enqueueVerifiedDelivery(bookingId, proof.getId());
        }

        // Notify driver
        try {
            notificationService.recordNotification(driverId, "DELIVERY_VERIFIED",
                    Map.of("tripId", tripId, "bookingId", bookingId,
                           "message", "Your delivery has been verified by the shipper."));
        } catch (Exception e) { log.warn("Notification failed (non-fatal): {}", e.getMessage()); }

        log.info("handleVerify: trip {} completed; settlement work queued for booking {}", tripId, bookingId);
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
        if (ct == null) {
            throw ProofOfDeliveryException.invalidFileType(ct);
        }
        String normalizedContentType = ct.toLowerCase(Locale.ROOT);
        if (!ALLOWED_CONTENT_TYPES.contains(normalizedContentType)) {
            throw ProofOfDeliveryException.invalidFileType(ct);
        }
        try (InputStream input = file.getInputStream()) {
            byte[] header = input.readNBytes(12);
            boolean signatureMatches = switch (normalizedContentType) {
                case "image/jpeg", "image/jpg" -> header.length >= 3
                        && (header[0] & 0xff) == 0xff
                        && (header[1] & 0xff) == 0xd8
                        && (header[2] & 0xff) == 0xff;
                case "image/png" -> header.length >= 8
                        && (header[0] & 0xff) == 0x89
                        && header[1] == 0x50
                        && header[2] == 0x4e
                        && header[3] == 0x47
                        && header[4] == 0x0d
                        && header[5] == 0x0a
                        && header[6] == 0x1a
                        && header[7] == 0x0a;
                case "image/webp" -> header.length >= 12
                        && header[0] == 'R'
                        && header[1] == 'I'
                        && header[2] == 'F'
                        && header[3] == 'F'
                        && header[8] == 'W'
                        && header[9] == 'E'
                        && header[10] == 'B'
                        && header[11] == 'P';
                case "application/pdf" -> header.length >= 5
                        && header[0] == '%'
                        && header[1] == 'P'
                        && header[2] == 'D'
                        && header[3] == 'F'
                        && header[4] == '-';
                default -> false;
            };
            if (!signatureMatches) {
                throw new ProofOfDeliveryException.FileValidationException(
                        "File content does not match its declared type");
            }
        } catch (IOException e) {
            throw new ProofOfDeliveryException.FileValidationException("Unable to read file content");
        }
    }

    private String sanitizeFilename(String name) {
        if (name == null || name.isBlank()) return "proof";
        // Remove path separators and dangerous characters
        return name.replaceAll("[^a-zA-Z0-9._-]", "_").substring(
                Math.max(0, name.lastIndexOf('/') + 1));
    }
}
