package com.ethioloadai.bid.entity;

import com.ethioloadai.bid.converter.BidCounterByConverter;
import com.ethioloadai.bid.converter.BidStatusConverter;
import com.ethioloadai.freight.entity.Freight;
import com.ethioloadai.user.entity.User;
import com.ethioloadai.vehicle.entity.Vehicle;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Represents a driver's bid on a freight/cargo request.
 *
 * <p>Key constraints:
 * <ul>
 *   <li>One bid per vehicle per cargo request (not per driver — a fleet owner
 *       with many vehicles can submit one bid per vehicle).</li>
 *   <li>Monetary fields use BigDecimal — never double/float.</li>
 *   <li>ai_score is informational only (set by the AI engine, not user input).</li>
 * </ul>
 */
@Entity
@Table(name = "bids")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Bid {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Relationships — all LAZY to avoid N+1 queries
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cargo_request_id", nullable = false)
    private Freight cargoRequest;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "driver_id", nullable = false)
    private User driver;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vehicle_id", nullable = false)
    private Vehicle vehicle;

    // Core bid fields
    @Column(name = "amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(name = "note", columnDefinition = "TEXT")
    private String note;

    /** Required for intracity bids — when the driver is available. */
    @Column(name = "available_datetime")
    private LocalDateTime availableDatetime;

    @Convert(converter = BidStatusConverter.class)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private BidStatus status = BidStatus.PENDING;

    /** AI engine recommendation score (0.0000–1.0000). Server-set only. */
    @Column(name = "ai_score", precision = 5, scale = 4)
    private BigDecimal aiScore;

    /** True when this bid is the top-ranked recommendation. Transient in business logic, persisted for caching. */
    @Column(name = "is_recommended", nullable = false)
    @Builder.Default
    private Boolean isRecommended = false;

    /** Haversine distance from vehicle current location to pickup (km). Informational only. */
    @Column(name = "distance_km", precision = 10, scale = 2)
    private BigDecimal distanceKm;

    // Counter-offer fields
    @Column(name = "counter_amount", precision = 15, scale = 2)
    private BigDecimal counterAmount;

    @Column(name = "counter_note", columnDefinition = "TEXT")
    private String counterNote;

    @Convert(converter = BidCounterByConverter.class)
    @Column(name = "counter_by", length = 10)
    private CounterBy counterBy;

    @Column(name = "counter_at")
    private LocalDateTime counterAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // ── Enums ─────────────────────────────────────────────────────────────────

    public enum BidStatus {
        PENDING,
        ACCEPTED,
        REJECTED,
        EXPIRED,
        COUNTERED
    }

    public enum CounterBy {
        SHIPPER,
        DRIVER
    }
}
