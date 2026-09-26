package com.ethioloadai.settlement.dto;

import com.ethioloadai.settlement.entity.CarrierSettlement;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * API response for a CarrierSettlement.
 *
 * <p>SECURITY: All amounts are read-only. No writable financial fields are exposed.
 */
public record CarrierSettlementResponse(
        Long id,
        Long bookingId,
        Long carrierCompanyId,
        String carrierCompanyName,
        Long proofOfDeliveryId,
        BigDecimal grossAmount,
        BigDecimal commissionRate,
        BigDecimal commissionAmount,
        BigDecimal netAmount,
        String currency,
        String status,
        String paymentReference,
        String externalTxId,
        LocalDateTime createdAt,
        LocalDateTime processedAt,
        LocalDateTime paidAt,
        LocalDateTime failedAt,
        LocalDateTime updatedAt,
        String failureReason,
        Integer retryCount
) {
    public static CarrierSettlementResponse from(CarrierSettlement s) {
        return new CarrierSettlementResponse(
                s.getId(),
                s.getBooking().getId(),
                s.getCarrierCompany().getId(),
                s.getCarrierCompany().getCompanyName(),
                s.getProofOfDeliveryId(),
                s.getGrossAmount(),
                s.getCommissionRate(),
                s.getCommissionAmount(),
                s.getNetAmount(),
                s.getCurrency(),
                s.getStatus().name(),
                s.getPaymentReference(),
                s.getExternalTxId(),
                s.getCreatedAt(),
                s.getProcessedAt(),
                s.getPaidAt(),
                s.getFailedAt(),
                s.getUpdatedAt(),
                s.getFailureReason(),
                s.getRetryCount()
        );
    }
}
