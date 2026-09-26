package com.ethioloadai.carrier.dispatch.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Phase 6 — Summary of a freight shipment that has been commercially awarded to
 * the authenticated carrier company, for display on the carrier dispatch portal.
 */
@Getter
@Builder
public class AwardedFreightSummary {

    private Long freightId;
    private String pickupLocation;
    private String destination;
    private String materialType;
    private BigDecimal weightTons;

    /** The agreed commercial amount from the winning CarrierOffer. */
    private BigDecimal agreedAmount;

    private Long carrierOfferId;

    /** Current dispatch status, or null if not yet dispatched. */
    private DispatchSummary activeDispatch;

    private LocalDateTime awardedAt;

    @Getter
    @Builder
    public static class DispatchSummary {
        private Long dispatchId;
        private String status;
        private Long driverUserId;
        private Long vehicleId;
        private LocalDateTime assignedAt;
        private Long bookingId;
    }
}
