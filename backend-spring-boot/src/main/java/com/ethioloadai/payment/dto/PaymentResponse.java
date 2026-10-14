package com.ethioloadai.payment.dto;

import com.ethioloadai.payment.entity.Payment;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Read-only payment response DTO.
 *
 * <p>Exposes only safe, read-appropriate fields. Internal audit state, JPA
 * entity references, and sensitive relationship IDs are deliberately omitted
 * or expressed as plain identifiers (not nested objects) to avoid mass
 * assignment risks and unnecessary data leakage.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentResponse {

    private Long    id;

    // Booking context
    private Long    bookingId;

    // All monetary amounts — server-computed, never from client
    /** Gross payable amount (= booking.estimatedPrice). */
    private BigDecimal amount;

    /** Platform commission deducted. */
    private BigDecimal commissionAmount;

    /** Amount remitted to driver / fleet. */
    private BigDecimal driverNetAmount;

    // Payment metadata
    private String  paymentMethod;
    private String  paymentStatus;

    /** External transaction reference supplied by the caller. May be null. */
    private String  transactionRef;

    /** ID of the user who initiated the payment or recorded its manual settlement. */
    private Long    paidByUserId;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // ── Factory ───────────────────────────────────────────────────────────────

    public static PaymentResponse from(Payment p) {
        return PaymentResponse.builder()
            .id(p.getId())
            .bookingId(p.getBooking() != null ? p.getBooking().getId() : null)
            .amount(p.getAmount())
            .commissionAmount(p.getCommissionAmount())
            .driverNetAmount(p.getDriverNetAmount())
            .paymentMethod(p.getPaymentMethod())
            .paymentStatus(p.getPaymentStatus() != null ? p.getPaymentStatus().name() : null)
            .transactionRef(p.getTransactionRef())
            .paidByUserId(p.getPaidBy() != null ? p.getPaidBy().getId() : null)
            .createdAt(p.getCreatedAt())
            .updatedAt(p.getUpdatedAt())
            .build();
    }
}
