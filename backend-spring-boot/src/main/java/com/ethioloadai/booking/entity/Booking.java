package com.ethioloadai.booking.entity;

import com.ethioloadai.bid.entity.Bid;
import com.ethioloadai.booking.converter.BookingStatusConverter;
import com.ethioloadai.freight.entity.Freight;
import com.ethioloadai.payment.entity.Payment;
import com.ethioloadai.rating.entity.Rating;
import com.ethioloadai.trip.entity.Trip;
import com.ethioloadai.user.entity.User;
import com.ethioloadai.vehicle.entity.Vehicle;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * A Booking is created atomically when a bid is accepted or a direct booking is made.
 *
 * <p>Financial fields:
 * <ul>
 *   <li>{@code estimatedPrice} — the agreed price; server-calculated from the accepted bid
 *       or from the winning CarrierOffer.agreedAmount (Phase 6).</li>
 *   <li>{@code commissionFee}  — platform's cut (10% standard, 5% for fleet bookings);
 *       server-calculated, NEVER trusted from client input.</li>
 * </ul>
 *
 * <p>{@code bid} is nullable: direct bookings (fleet dispatch, carrier dispatch Phase 6)
 * bypass the bidding flow.
 *
 * <p>Phase 6 adds {@code carrierOfferId}, {@code dispatchAssignmentId}, and
 * {@code carrierCompanyId} for carrier-dispatched bookings.
 */
@Entity
@Table(name = "bookings")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cargo_id", nullable = false)
    private Freight cargoRequest;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vehicle_id", nullable = false)
    private Vehicle vehicle;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "driver_id", nullable = false)
    private User driver;

    /** Nullable — direct bookings do not always originate from a bid. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bid_id")
    private Bid bid;

    // ── Phase 6: Carrier dispatch context ────────────────────────────────────

    /**
     * Phase 6 — The winning CarrierOffer (Phase 5) that led to this booking.
     * Null for legacy bid-acceptance bookings.
     */
    @Column(name = "carrier_offer_id")
    private Long carrierOfferId;

    /**
     * Phase 6 — The DispatchAssignment whose driver acceptance created this booking.
     * Null for legacy bid-acceptance bookings.
     */
    @Column(name = "dispatch_assignment_id")
    private Long dispatchAssignmentId;

    /**
     * Phase 6 — Denormalized carrier company ID for fast carrier-scoped queries.
     * Null for legacy bid-acceptance bookings.
     */
    @Column(name = "carrier_company_id")
    private Long carrierCompanyId;

    @Convert(converter = BookingStatusConverter.class)
    @Column(name = "booking_status", nullable = false, length = 20)
    @Builder.Default
    private BookingStatus bookingStatus = BookingStatus.PENDING;

    /** Agreed price in ETB. Server-calculated from accepted bid or direct price. */
    @Column(name = "estimated_price", precision = 15, scale = 2)
    private BigDecimal estimatedPrice;

    /** Platform commission in ETB. Server-calculated (10% or 5% for fleet). */
    @Column(name = "commission_fee", nullable = false, precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal commissionFee = BigDecimal.ZERO;

    // ── Child relationships (mappedBy — Booking is the owning side via FK) ───
    /** One booking → one trip (when trip starts). */
    @OneToOne(mappedBy = "booking", fetch = FetchType.LAZY)
    private Trip trip;

    /** One booking → one payment (when payment is processed). */
    @OneToOne(mappedBy = "booking", fetch = FetchType.LAZY)
    private Payment payment;

    /** Ratings submitted for this booking (up to 2: one from shipper, one from driver). */
    @OneToMany(mappedBy = "booking", fetch = FetchType.LAZY)
    private java.util.List<Rating> ratings;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // ── Enum ──────────────────────────────────────────────────────────────────

    public enum BookingStatus {
        PENDING,
        ACCEPTED,
        DELIVERED,
        COMPLETED,
        CONFIRMED,
        CANCELLED
    }
}
