package com.ethioloadai.recommendation.entity;

import com.ethioloadai.freight.entity.Freight;
import com.ethioloadai.vehicle.entity.Vehicle;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Persisted AI scoring result for a (cargo_request, vehicle) pair.
 *
 * <p>Populated by the AI engine integration layer. Allows the frontend to
 * retrieve price predictions and truck recommendation scores without re-calling
 * the AI engine on every page load.
 *
 * <p>All scores use DECIMAL(5,4) for 4-decimal precision (0.0000–1.0000).
 * predictedPrice uses DECIMAL(15,2) to handle large ETB amounts.
 */
@Entity
@Table(name = "ai_recommendations")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AIRecommendation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cargo_request_id", nullable = false)
    private Freight cargoRequest;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vehicle_id", nullable = false)
    private Vehicle vehicle;

    /** Overall match score for this vehicle + cargo pair: 0.0000–1.0000. */
    @Column(name = "recommendation_score", nullable = false, precision = 5, scale = 4)
    private BigDecimal recommendationScore;

    /** AI-estimated freight price in ETB. */
    @Column(name = "predicted_price", nullable = false, precision = 15, scale = 2)
    private BigDecimal predictedPrice;

    /** Probability (0.0000–1.0000) that driver finds a return load. */
    @Column(name = "backhaul_probability", nullable = false, precision = 5, scale = 4)
    private BigDecimal backhaulProbability;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
