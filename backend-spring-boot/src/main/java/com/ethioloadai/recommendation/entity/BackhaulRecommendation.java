package com.ethioloadai.recommendation.entity;

import com.ethioloadai.freight.entity.Freight;
import com.ethioloadai.recommendation.converter.BackhaulStatusConverter;
import com.ethioloadai.trip.entity.Trip;
import com.ethioloadai.user.entity.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * An AI-generated recommendation for a driver to pick up return cargo after delivery.
 *
 * <p>Populated asynchronously by a background job when a trip starts.
 * Unique per (trip, driver, cargo_request) to prevent duplicate recommendations
 * from job re-runs.
 *
 * <p>score: 0.000–1.000 — higher means better direction/proximity match.
 * metadata: JSONB bag from the AI engine (direction_match, proximity_score, etc.).
 */
@Entity
@Table(name = "backhaul_recommendations")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BackhaulRecommendation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trip_id", nullable = false)
    private Trip trip;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "driver_id", nullable = false)
    private User driver;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cargo_request_id", nullable = false)
    private Freight cargoRequest;

    /** AI match score: 0.000–1.000. */
    @Column(name = "score", nullable = false, precision = 4, scale = 3)
    @Builder.Default
    private BigDecimal score = BigDecimal.ZERO;

    @Convert(converter = BackhaulStatusConverter.class)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private BackhaulStatus status = BackhaulStatus.PENDING;

    /** AI engine metadata (direction_match, proximity_km, etc.) stored as JSONB. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", nullable = false, columnDefinition = "jsonb")
    @Builder.Default
    private Map<String, Object> metadata = new java.util.HashMap<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // ── Enum ──────────────────────────────────────────────────────────────────

    public enum BackhaulStatus {
        PENDING,
        VIEWED,
        BID_PLACED,
        DISMISSED
    }
}
