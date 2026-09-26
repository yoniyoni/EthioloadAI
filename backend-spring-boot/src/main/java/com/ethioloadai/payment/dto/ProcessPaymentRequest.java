package com.ethioloadai.payment.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Client-supplied fields for initiating a payment.
 *
 * <p>SECURITY: amount, commissionAmount, driverNetAmount and paymentStatus
 * are intentionally absent — those are server-calculated and never accepted
 * from client input.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProcessPaymentRequest {

    @NotNull(message = "bookingId is required")
    @Positive(message = "bookingId must be positive")
    private Long bookingId;

    /**
     * Payment method label.
     * Accepted values: cash, bank_transfer, telebirr, cbe_birr, chapa,
     * awash_bank, dashen_bank, in_app.
     * Defaults to 'cash' if omitted or unrecognised.
     */
    private String paymentMethod;

    /**
     * Optional external transaction reference (bank ref, mobile-money code, etc.).
     * Free-form string; max 255 chars enforced by DB column.
     */
    private String transactionRef;
}
