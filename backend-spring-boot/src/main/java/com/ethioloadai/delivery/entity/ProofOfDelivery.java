package com.ethioloadai.delivery.entity;

import com.ethioloadai.booking.entity.Booking;
import com.ethioloadai.freight.entity.Freight;
import com.ethioloadai.trip.entity.Trip;
import com.ethioloadai.user.entity.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * Phase 7 — Proof of Delivery submitted by the assigned driver.
 *
 * <h2>Lifecycle</h2>
 * <pre>
 *   SUBMITTED → VERIFIED  (shipper confirms receipt)
 *   SUBMITTED → REJECTED  (shipper rejects — driver may resubmit after new upload)
 * </pre>
 *
 * <h2>Security invariants</h2>
 * <ul>
 *   <li>{@code storageKey} is NEVER returned in API responses — only a download endpoint.</li>
 *   <li>Only the trip's assigned driver may create a proof row.</li>
 *   <li>Only the freight's shipper (or ADMIN) may verify or reject.</li>
 *   <li>Driver cannot self-verify. Carrier cannot self-verify.</li>
 *   <li>One proof per trip (unique DB constraint on {@code trip_id}).</li>
 * </ul>
 */
@Entity
@Table(name = "proof_of_delivery")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProofOfDelivery {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ── Core relationships ────────────────────────────────────────────────────

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trip_id", nullable = false, updatable = false)
    private Trip trip;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id", nullable = false, updatable = false)
    private Booking booking;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "freight_id", nullable = false, updatable = false)
    private Freight freight;

    /** Driver who uploaded the proof. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "uploaded_by", nullable = false, updatable = false)
    private User uploadedBy;

    // ── File storage (mirrors DriverDocument pattern) ─────────────────────────

    /** Internal storage key / object path. NEVER exposed in API responses. */
    @Column(name = "storage_key", nullable = false, length = 500, updatable = false)
    private String storageKey;

    /** Original filename shown to users (display only). */
    @Column(name = "file_name", nullable = false, length = 255, updatable = false)
    private String fileName;

    @Column(name = "content_type", nullable = false, length = 100, updatable = false)
    private String contentType;

    @Column(name = "file_size", nullable = false, updatable = false)
    private Long fileSize;

    // ── Optional delivery metadata ────────────────────────────────────────────

    @Column(name = "recipient_name", length = 255)
    private String recipientName;

    @Column(name = "note", columnDefinition = "TEXT")
    private String note;

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 40)
    @Builder.Default
    private ProofStatus status = ProofStatus.SUBMITTED;

    // ── Review ────────────────────────────────────────────────────────────────

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by")
    private User reviewedBy;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    // ── Timestamps ────────────────────────────────────────────────────────────

    @Column(name = "uploaded_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime uploadedAt = LocalDateTime.now();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // ── Enum ──────────────────────────────────────────────────────────────────

    public enum ProofStatus {
        /** Driver has submitted the proof; awaiting shipper review. */
        SUBMITTED,
        /** Shipper confirmed delivery. Triggers trip/settlement completion. */
        VERIFIED,
        /** Shipper rejected the proof. Driver may resubmit after new upload. */
        REJECTED;

        public boolean isTerminal() {
            return this == VERIFIED || this == REJECTED;
        }
    }
}
