package com.ethioloadai.settlement.entity;

import com.ethioloadai.booking.entity.Booking;
import com.ethioloadai.carrier.company.entity.CarrierCompany;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Phase 7 — Carrier Company financial settlement for a completed, verified delivery.
 *
 * <h2>Commercial invariants</h2>
 * <ul>
 *   <li>{@code grossAmount} = {@code booking.estimatedPrice} = CarrierOffer.agreedAmount</li>
 *   <li>{@code commissionAmount} = grossAmount × commissionRate</li>
 *   <li>{@code netAmount} = grossAmount − commissionAmount</li>
 *   <li>All amounts are server-calculated. No client-supplied values accepted.</li>
 *   <li>Settlement recipient is always the {@link CarrierCompany}, not the individual driver.</li>
 *   <li>One settlement per Booking (DB unique constraint).</li>
 * </ul>
 *
 * <h2>Idempotency</h2>
 * <ul>
 *   <li>Unique constraint on {@code booking_id} prevents duplicate rows under concurrency.</li>
 *   <li>{@code idempotencyKey} guards against duplicate payment gateway callbacks.</li>
 *   <li>{@code version} for optimistic locking during status transitions.</li>
 * </ul>
 *
 * <h2>Lifecycle</h2>
 * <pre>
 *   PENDING → PROCESSING → PAID
 *                       → FAILED   (retry allowed; uses same amounts)
 *   PENDING → BLOCKED              (active dispute)
 *   BLOCKED → PENDING              (dispute resolved)
 *   PENDING/FAILED → CANCELLED     (admin decision)
 * </pre>
 */
@Entity
@Table(name = "carrier_settlements")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CarrierSettlement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ── Core relationships ────────────────────────────────────────────────────

    /** One settlement per booking — unique constraint enforced in DB. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id", nullable = false, unique = true, updatable = false)
    private Booking booking;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "carrier_company_id", nullable = false, updatable = false)
    private CarrierCompany carrierCompany;

    /** The proof of delivery that triggered this settlement. */
    @Column(name = "proof_of_delivery_id", updatable = false)
    private Long proofOfDeliveryId;

    // ── Amounts — ALL server-calculated; NEVER from client ───────────────────

    /** Total freight amount in ETB. = booking.estimatedPrice = CarrierOffer.agreedAmount. */
    @Column(name = "gross_amount", nullable = false, precision = 15, scale = 2, updatable = false)
    private BigDecimal grossAmount;

    /** Platform commission rate applied (e.g. 0.1000 for 10%). */
    @Column(name = "commission_rate", nullable = false, precision = 5, scale = 4, updatable = false)
    private BigDecimal commissionRate;

    /** Platform commission amount in ETB. = grossAmount × commissionRate. */
    @Column(name = "commission_amount", nullable = false, precision = 15, scale = 2, updatable = false)
    private BigDecimal commissionAmount;

    /** Carrier net payout in ETB. = grossAmount − commissionAmount. */
    @Column(name = "net_amount", nullable = false, precision = 15, scale = 2, updatable = false)
    private BigDecimal netAmount;

    /** ISO 4217 currency code. Always ETB for this platform. */
    @Column(name = "currency", nullable = false, length = 3)
    @Builder.Default
    private String currency = "ETB";

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private SettlementStatus status = SettlementStatus.PENDING;

    // ── Payment gateway ───────────────────────────────────────────────────────

    /** External payment reference (bank transfer ref, Telebirr code, etc.). */
    @Column(name = "payment_reference", length = 255)
    private String paymentReference;

    /** External transaction ID from the payment provider. */
    @Column(name = "external_tx_id", length = 255)
    private String externalTxId;

    // ── Timestamps ────────────────────────────────────────────────────────────

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @Column(name = "failed_at")
    private LocalDateTime failedAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // ── Failure / retry ───────────────────────────────────────────────────────

    @Column(name = "failure_reason", columnDefinition = "TEXT")
    private String failureReason;

    @Column(name = "retry_count", nullable = false)
    @Builder.Default
    private Integer retryCount = 0;

    // ── Idempotency ───────────────────────────────────────────────────────────

    /** Unique key used to guard against duplicate payment gateway callbacks. */
    @Column(name = "idempotency_key", length = 255, unique = true)
    private String idempotencyKey;

    /** Optimistic locking for concurrent state transitions. */
    @Version
    @Column(name = "version", nullable = false)
    @Builder.Default
    private Long version = 0L;

    // ── Enum ──────────────────────────────────────────────────────────────────

    public enum SettlementStatus {
        /** Settlement created; awaiting processing. */
        PENDING,
        /** Payment is being processed (external gateway in flight). */
        PROCESSING,
        /** Carrier has been paid. Terminal. */
        PAID,
        /** Payment processing failed. May be retried. */
        FAILED,
        /** Blocked by an active delivery dispute. */
        BLOCKED,
        /** Admin-cancelled. Terminal. */
        CANCELLED;

        public boolean isTerminal() {
            return this == PAID || this == CANCELLED;
        }

        public boolean isRetryable() {
            return this == FAILED || this == PENDING;
        }
    }
}
