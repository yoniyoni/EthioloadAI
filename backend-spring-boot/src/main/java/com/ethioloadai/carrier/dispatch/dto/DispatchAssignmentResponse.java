package com.ethioloadai.carrier.dispatch.dto;

import com.ethioloadai.carrier.dispatch.entity.DispatchAssignment;
import com.ethioloadai.carrier.dispatch.entity.DispatchStatus;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Phase 6 — Response DTO for a dispatch assignment.
 *
 * <p>Returned to the carrier dispatcher and to the assigned driver.
 * Sensitive fields (agreedAmount) are included because the driver needs to
 * know the commercial amount they are accepting responsibility for.
 */
@Getter
@Builder
public class DispatchAssignmentResponse {

    private Long id;
    private Long freightId;
    private Long carrierOfferId;
    private Long carrierCompanyId;
    private Long driverUserId;
    private Long vehicleId;
    private Long assignedBy;
    private DispatchStatus status;

    /** The agreed commercial amount — immutable, sourced from winning CarrierOffer. */
    private BigDecimal agreedAmount;

    private String notes;
    private String rejectionReason;

    private LocalDateTime assignedAt;
    private LocalDateTime respondedAt;
    private LocalDateTime cancelledAt;

    /** Set when status == DRIVER_ACCEPTED. */
    private Long bookingId;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // ── Factory ───────────────────────────────────────────────────────────────

    public static DispatchAssignmentResponse from(DispatchAssignment d) {
        return DispatchAssignmentResponse.builder()
                .id(d.getId())
                .freightId(d.getFreightId())
                .carrierOfferId(d.getCarrierOfferId())
                .carrierCompanyId(d.getCarrierCompanyId())
                .driverUserId(d.getDriverUserId())
                .vehicleId(d.getVehicleId())
                .assignedBy(d.getAssignedBy())
                .status(d.getStatus())
                .agreedAmount(d.getAgreedAmount())
                .notes(d.getNotes())
                .rejectionReason(d.getRejectionReason())
                .assignedAt(d.getAssignedAt())
                .respondedAt(d.getRespondedAt())
                .cancelledAt(d.getCancelledAt())
                .bookingId(d.getBookingId())
                .createdAt(d.getCreatedAt())
                .updatedAt(d.getUpdatedAt())
                .build();
    }
}
