package com.ethioloadai.dispute.service;

import com.ethioloadai.booking.entity.Booking;
import com.ethioloadai.booking.repository.BookingRepository;
import com.ethioloadai.dispute.dto.CreateDisputeRequest;
import com.ethioloadai.dispute.dto.DisputeListResponse;
import com.ethioloadai.dispute.dto.DisputeResponse;
import com.ethioloadai.dispute.dto.ResolveDisputeRequest;
import com.ethioloadai.dispute.entity.Dispute;
import com.ethioloadai.dispute.entity.DisputeStatus;
import com.ethioloadai.dispute.exception.DisputeException.*;
import com.ethioloadai.dispute.repository.DisputeRepository;
import com.ethioloadai.freight.entity.Freight;
import com.ethioloadai.freight.repository.FreightRepository;
import com.ethioloadai.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class DisputeServiceImpl implements DisputeService {

    private static final Logger log = LoggerFactory.getLogger(DisputeServiceImpl.class);

    private final DisputeRepository disputeRepository;
    private final FreightRepository freightRepository;
    private final BookingRepository bookingRepository;

    @Override
    @Transactional
    public DisputeResponse createDispute(CreateDisputeRequest request, User caller) {
        if (caller == null) {
            throw new UnauthorizedDisputeAccessException("User must be authenticated to file a dispute");
        }

        if (caller.getRole() == User.Role.ADMIN) {
            throw new UnauthorizedDisputeAccessException("Administrators cannot file disputes; only freight shippers or assigned drivers may file");
        }

        if (request.freightId() == null) {
            throw new InvalidDisputeOperationException("Freight ID must not be null");
        }

        Freight freight = freightRepository.findById(request.freightId())
                .orElseThrow(() -> new DisputeNotFoundException("Freight not found with ID: " + request.freightId()));

        Optional<Booking> activeBooking = bookingRepository
                .findFirstByCargoRequest_IdAndBookingStatusNotOrderByIdDesc(
                        request.freightId(), Booking.BookingStatus.CANCELLED);

        // Verify participant ownership (shipper or matched/booked driver)
        boolean isShipper = freight.getShipper() != null && freight.getShipper().getId().equals(caller.getId());
        boolean isMatchedDriver = freight.getMatchedDriver() != null && freight.getMatchedDriver().getId().equals(caller.getId());
        boolean isBookedDriver = activeBooking.isPresent() && activeBooking.get().getDriver() != null &&
                activeBooking.get().getDriver().getId().equals(caller.getId());

        if (!isShipper && !isMatchedDriver && !isBookedDriver) {
            log.warn("Unauthorized dispute creation attempt: freightId={} callerId={}", request.freightId(), caller.getId());
            throw new UnauthorizedDisputeAccessException("Only the freight shipper or assigned driver may file a dispute for this shipment");
        }

        // Prevent duplicate active dispute (OPEN or UNDER_REVIEW)
        boolean hasActiveDispute = disputeRepository.existsByFreight_IdAndStatusIn(
                request.freightId(),
                List.of(DisputeStatus.OPEN, DisputeStatus.UNDER_REVIEW)
        );
        if (hasActiveDispute) {
            throw new DuplicateDisputeException("An active dispute is already open for freight #" + request.freightId());
        }

        Dispute dispute = Dispute.builder()
                .freight(freight)
                .booking(activeBooking.orElse(null))
                .reportedBy(caller)
                .reason(request.reason().trim())
                .description(request.description() != null ? request.description().trim() : null)
                .status(DisputeStatus.OPEN)
                .build();

        Dispute saved = disputeRepository.save(dispute);
        log.info("Dispute filed: id={} freightId={} reportedBy={}", saved.getId(), request.freightId(), caller.getId());
        return DisputeResponse.from(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public DisputeListResponse getDisputesByFreightId(Long freightId, User caller) {
        if (caller == null) {
            throw new UnauthorizedDisputeAccessException("User must be authenticated to view disputes");
        }

        if (freightId == null) {
            throw new DisputeNotFoundException("Freight ID must not be null");
        }

        Freight freight = freightRepository.findById(freightId)
                .orElseThrow(() -> new DisputeNotFoundException("Freight not found with ID: " + freightId));

        Optional<Booking> activeBooking = bookingRepository
                .findFirstByCargoRequest_IdAndBookingStatusNotOrderByIdDesc(
                        freightId, Booking.BookingStatus.CANCELLED);

        // Verify authorization: ADMIN, shipper, or driver
        if (caller.getRole() != User.Role.ADMIN) {
            boolean isShipper = freight.getShipper() != null && freight.getShipper().getId().equals(caller.getId());
            boolean isMatchedDriver = freight.getMatchedDriver() != null && freight.getMatchedDriver().getId().equals(caller.getId());
            boolean isBookedDriver = activeBooking.isPresent() && activeBooking.get().getDriver() != null &&
                    activeBooking.get().getDriver().getId().equals(caller.getId());

            if (!isShipper && !isMatchedDriver && !isBookedDriver) {
                log.warn("Unauthorized dispute view attempt: freightId={} callerId={}", freightId, caller.getId());
                throw new UnauthorizedDisputeAccessException("You are not authorized to view disputes for this freight");
            }
        }

        List<Dispute> disputes = disputeRepository.findByFreight_IdOrderByCreatedAtDesc(freightId);
        List<DisputeResponse> responses = disputes.stream().map(DisputeResponse::from).toList();
        return DisputeListResponse.of(responses);
    }

    @Override
    @Transactional(readOnly = true)
    public DisputeListResponse getAllDisputes(User caller) {
        if (caller == null || caller.getRole() != User.Role.ADMIN) {
            throw new UnauthorizedDisputeAccessException("Only administrators may list all disputes");
        }

        List<Dispute> disputes = disputeRepository.findAllByOrderByCreatedAtDesc();
        List<DisputeResponse> responses = disputes.stream().map(DisputeResponse::from).toList();
        return DisputeListResponse.of(responses);
    }

    @Override
    @Transactional
    public DisputeResponse resolveDispute(Long disputeId, ResolveDisputeRequest request, User caller) {
        if (caller == null || caller.getRole() != User.Role.ADMIN) {
            throw new UnauthorizedDisputeAccessException("Only administrators may resolve disputes");
        }

        if (disputeId == null) {
            throw new DisputeNotFoundException("Dispute ID must not be null");
        }

        Dispute dispute = disputeRepository.findById(disputeId)
                .orElseThrow(() -> new DisputeNotFoundException("Dispute not found with ID: " + disputeId));

        if (dispute.getStatus() == DisputeStatus.RESOLVED || dispute.getStatus() == DisputeStatus.CLOSED) {
            throw new DisputeAlreadyResolvedException("Dispute #" + disputeId + " is already " + dispute.getStatus().getValue());
        }

        String rawResolution = request.resolution() != null ? request.resolution().trim().toLowerCase() : "";
        switch (rawResolution) {
            case "investigating" -> {
                dispute.setStatus(DisputeStatus.UNDER_REVIEW);
                dispute.setResolution("investigating");
                dispute.setAdminNotes(request.adminNotes());
            }
            case "release", "refund", "split" -> {
                dispute.setStatus(DisputeStatus.RESOLVED);
                dispute.setResolution(rawResolution);
                dispute.setAdminNotes(request.adminNotes());
                dispute.setRefundAmount(request.refundAmount());
                dispute.setResolvedAt(LocalDateTime.now());
            }
            default -> throw new InvalidDisputeOperationException(
                    "Invalid resolution: '" + request.resolution() + "'. Allowed values: investigating, release, refund, split"
            );
        }

        Dispute updated = disputeRepository.save(dispute);
        log.info("Dispute resolved: id={} status={} resolution={} adminId={}", updated.getId(), updated.getStatus(), updated.getResolution(), caller.getId());
        return DisputeResponse.from(updated);
    }

}
