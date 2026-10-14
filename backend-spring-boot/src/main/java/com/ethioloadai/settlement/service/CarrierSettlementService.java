package com.ethioloadai.settlement.service;

import com.ethioloadai.settlement.dto.CarrierSettlementResponse;
import com.ethioloadai.user.entity.User;

import java.util.List;

/**
 * Phase 7 — Carrier company settlement lifecycle.
 */
public interface CarrierSettlementService {

    /**
     * Create a settlement for a verified delivery.
     * Called internally by ProofOfDeliveryService after proof is VERIFIED.
     * Idempotent — returns existing settlement if one already exists.
     *
     * @param bookingId        the booking being settled
     * @param proofOfDeliveryId the proof that triggered settlement
     */
    CarrierSettlementResponse createSettlement(Long bookingId, Long proofOfDeliveryId);

    /**
     * Retrieve a settlement by ID.
     * Caller must be the carrier member, the shipper, or ADMIN.
     */
    CarrierSettlementResponse getSettlement(Long settlementId, User caller);

    /**
     * Retrieve the settlement for a specific booking.
     */
    CarrierSettlementResponse getSettlementByBookingId(Long bookingId, User caller);

    /**
     * Carrier's financial history — all settlements for their company.
     * Caller must be an active member of the carrier company.
     */
    List<CarrierSettlementResponse> getCarrierSettlements(Long carrierCompanyId, User caller);

    /**
     * Admin: all settlements across the platform.
     */
    List<CarrierSettlementResponse> getAllSettlements(User admin);

    /**
     * Mark settlement PROCESSING (external payment initiated).
     * Admin only.
     */
    CarrierSettlementResponse markProcessing(Long settlementId, User admin);

    /**
     * Mark settlement PAID (external payment confirmed).
     * Admin only.
     */
    CarrierSettlementResponse markPaid(Long settlementId, String externalTxId,
                                       String paymentReference, User admin);

    /**
     * Mark settlement FAILED. Admin only.
     * Preserves verified delivery state — failure is non-destructive.
     */
    CarrierSettlementResponse markFailed(Long settlementId, String reason, User admin);

    /**
     * Retry a FAILED settlement.
     * Reuses same commercial amounts — does NOT recalculate.
     * Admin only.
     */
    CarrierSettlementResponse retrySettlement(Long settlementId, User admin);

    /**
     * Block settlement due to open dispute.
     */
    CarrierSettlementResponse blockSettlement(Long settlementId, String reason, User admin);

    /**
     * Unblock settlement after dispute resolution.
     */
    CarrierSettlementResponse unblockSettlement(Long settlementId, User admin);
}
