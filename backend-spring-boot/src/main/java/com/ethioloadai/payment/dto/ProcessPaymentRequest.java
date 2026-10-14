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
     * Only cash is currently supported. Other labels do not represent active
     * integrations and are rejected until a trusted verification path exists.
     * Defaults to 'cash' if omitted.
     */
    private String paymentMethod;

    /**
     * Optional descriptive reference. It is not independently verified and is
     * never accepted as proof that funds were received.
     */
    private String transactionRef;
}
