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
 * <p>Company-facing projections redact financial fields for dispatchers.
 * The existing assigned-driver projection retains its established response shape.
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
        return fromCompany(d, true);
    }

    public static DispatchAssignmentResponse fromCompany(
            DispatchAssignment d, boolean includeFinancial) {
        return DispatchAssignmentResponse.builder()
                .id(d.getId())
                .freightId(d.getFreightId())
                .carrierOfferId(d.getCarrierOfferId())
                .carrierCompanyId(d.getCarrierCompanyId())
                .driverUserId(d.getDriverUserId())
                .vehicleId(d.getVehicleId())
                .assignedBy(d.getAssignedBy())
                .status(d.getStatus())
                .agreedAmount(includeFinancial ? d.getAgreedAmount() : null)
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
