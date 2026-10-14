package com.ethioloadai.payment.entity;

import com.ethioloadai.booking.entity.Booking;
import com.ethioloadai.payment.converter.PaymentStatusConverter;
import com.ethioloadai.user.entity.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Records a payment for a completed booking.
 *
 * <p>ALL monetary amounts are server-calculated — the API must never accept
 * amount, commissionAmount, or driverNetAmount from client input.
 *
 * <p>One payment per booking (enforced by unique constraint on booking_id).
 * The service layer must handle DataIntegrityViolationException on duplicate
 * payment attempts.
 *
 * <p>Financial relationships:
 * <pre>
 *   amount            = booking.estimatedPrice
 *   commissionAmount  = amount × commissionRate  (0.10 standard / 0.05 fleet)
 *   driverNetAmount   = amount - commissionAmount
 * </pre>
 */
@Entity
@Table(name = "payments")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** One payment per booking — unique constraint enforced in DB. */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id", nullable = false, unique = true)
    private Booking booking;

    /** Total payment amount in ETB. Server-calculated from booking.estimatedPrice. */
    @Column(name = "amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    /** Platform commission in ETB. Server-calculated. */
    @Column(name = "commission_amount", nullable = false, precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal commissionAmount = BigDecimal.ZERO;

    /** Amount the driver/fleet receives after commission deduction. Server-calculated. */
    @Column(name = "driver_net_amount", nullable = false, precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal driverNetAmount = BigDecimal.ZERO;

    /** The user who initiated the payment or recorded its manual settlement. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "paid_by")
    private User paidBy;

    /** Payment method label: 'cash', 'bank_transfer', etc. Not an enum — extensible. */
    @Column(name = "payment_method", nullable = false, length = 100)
    @Builder.Default
    private String paymentMethod = "cash";

    @Convert(converter = PaymentStatusConverter.class)
    @Column(name = "payment_status", nullable = false, length = 20)
    @Builder.Default
    private PaymentStatus paymentStatus = PaymentStatus.PENDING;

    /** External transaction reference (bank ref, mobile money code, etc.). */
    @Column(name = "transaction_ref", length = 255)
    private String transactionRef;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // ── Enum ──────────────────────────────────────────────────────────────────

    public enum PaymentStatus {
        PENDING,
        PAID,
        FAILED
    }
}
