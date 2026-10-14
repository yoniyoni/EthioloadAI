package com.ethioloadai.carrier.dispatch.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Phase 6 — Carrier internal dispatch assignment.
 *
 * <p>Created by a CARRIER_OWNER, FLEET_MANAGER, or DISPATCHER after the carrier
 * company wins commercial award (Phase 5). Records which specific driver and
 * vehicle from the winning company will perform the shipment.
 *
 * <h2>Security invariants</h2>
 * <ul>
 *   <li>{@code carrierCompanyId}, {@code freightId}, {@code carrierOfferId},
 *       {@code agreedAmount}, and {@code assignedBy} are ALL set server-side.</li>
 *   <li>{@code driverUserId} and {@code vehicleId} must belong to {@code carrierCompanyId}.</li>
 *   <li>{@code agreedAmount} is immutable after creation.</li>
 * </ul>
 *
 * <h2>Lifecycle</h2>
 * <pre>
 *   ASSIGNED → DRIVER_ACCEPTED (→ Booking created)
 *   ASSIGNED → DRIVER_REJECTED (→ carrier may redispatch)
 *   ASSIGNED → CANCELLED       (→ carrier operator cancelled)
 *   ASSIGNED → SUPERSEDED      (→ replaced by new dispatch)
 * </pre>
 */
@Entity
@Table(name = "dispatch_assignments")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DispatchAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ── Core relationships ────────────────────────────────────────────────────

    /** The freight shipment being dispatched. */
    @Column(name = "freight_id", nullable = false, updatable = false)
    private Long freightId;

    /** The winning CarrierOffer from Phase 5. */
    @Column(name = "carrier_offer_id", nullable = false, updatable = false)
    private Long carrierOfferId;

    /** The winning carrier company. Server-derived — never trusted from client. */
    @Column(name = "carrier_company_id", nullable = false, updatable = false)
    private Long carrierCompanyId;

    /** The selected driver's User.id — must belong to carrierCompanyId. */
    @Column(name = "driver_user_id", nullable = false, updatable = false)
    private Long driverUserId;

    /** The selected vehicle — must belong to carrierCompanyId. */
    @Column(name = "vehicle_id", nullable = false, updatable = false)
    private Long vehicleId;

    /** The dispatcher/fleet manager/owner who created this assignment. */
    @Column(name = "assigned_by", nullable = false, updatable = false)
    private Long assignedBy;

    // ── Status & lifecycle ────────────────────────────────────────────────────

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private DispatchStatus status = DispatchStatus.ASSIGNED;

    /**
     * The agreed commercial price copied from {@code CarrierOffer.agreedAmount} at
     * dispatch creation. Immutable — the driver/dispatcher CANNOT change this.
     */
    @Column(name = "agreed_amount", nullable = false, precision = 15, scale = 2, updatable = false)
    private BigDecimal agreedAmount;

    /** Optional note from the dispatcher (route instructions, etc.). */
    @Column(name = "notes", length = 1000)
    private String notes;

    /** Optional reason provided by the driver when rejecting. */
    @Column(name = "rejection_reason", length = 1000)
    private String rejectionReason;

    // ── Timestamps ────────────────────────────────────────────────────────────

    @Column(name = "assigned_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime assignedAt = LocalDateTime.now();

    /** Set when the driver responds (DRIVER_ACCEPTED or DRIVER_REJECTED). */
    @Column(name = "responded_at")
    private LocalDateTime respondedAt;

    /** Set when a carrier operator cancels an ASSIGNED dispatch. */
    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    /**
     * Set after the driver accepts and a Booking is atomically created.
     * FK to bookings.id — enforces the unique booking-per-dispatch invariant.
     */
    @Column(name = "booking_id")
    private Long bookingId;

    /** Optimistic locking — prevents concurrent double-acceptance race conditions. */
    @Version
    @Column(name = "version", nullable = false)
    @Builder.Default
    private Long version = 0L;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
