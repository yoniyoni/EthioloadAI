package com.ethioloadai.carrier.offer.entity;

import com.ethioloadai.carrier.company.entity.CarrierCompany;
import com.ethioloadai.freight.entity.Freight;
import com.ethioloadai.user.entity.User;
import com.ethioloadai.vehicle.entity.Vehicle;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Phase 5 — Carrier company commercial offer on a freight shipment.
 *
 * <p>A {@code CarrierOffer} represents the carrier company as the commercial
 * actor, replacing the individual-driver {@code Bid} in the carrier workflow.
 *
 * <h2>Lifecycle</h2>
 * <pre>
 *   PENDING → NEGOTIATING → ACCEPTED
 *                         → REJECTED
 *                         → WITHDRAWN
 *   PENDING / NEGOTIATING → SUPERSEDED  (when another offer is accepted)
 * </pre>
 *
 * <h2>Concurrency</h2>
 * <p>Optimistic locking via {@code @Version} prevents two concurrent
 * acceptance requests from both succeeding on the same offer.
 *
 * <h2>Security invariants</h2>
 * <ul>
 *   <li>{@code carrierCompanyId} and {@code submittedBy} are always set
 *       server-side from the authenticated JWT — never from request body.</li>
 *   <li>{@code agreedAmount} is frozen at acceptance and never changed.</li>
 * </ul>
 */
@Entity
@Table(name = "carrier_offers")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CarrierOffer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "freight_id", nullable = false, updatable = false)
    private Freight freight;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "carrier_company_id", nullable = false, updatable = false)
    private CarrierCompany carrierCompany;

    /** User (CARRIER_OWNER or DISPATCHER) who submitted the offer. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "submitted_by", nullable = false, updatable = false)
    private User submittedBy;

    /** Driver and vehicle proposed together with the carrier's application. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "proposed_driver_user_id")
    private User proposedDriver;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "proposed_vehicle_id")
    private Vehicle proposedVehicle;

    @Enumerated(EnumType.STRING)
    @Column(name = "pricing_type", nullable = false, length = 20, updatable = false)
    private PricingType pricingType;

    /**
     * Denormalized current negotiated amount.
     * Updated atomically with a {@link OfferNegotiationEntry} insertion.
     */
    @Column(name = "current_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal currentAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private OfferStatus status = OfferStatus.PENDING;

    /**
     * Whose turn it is to respond. {@code null} in terminal states.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "next_actor", length = 10)
    private ActorType nextActor;

    @Column(name = "accepted_at")
    private LocalDateTime acceptedAt;

    @Column(name = "rejected_at")
    private LocalDateTime rejectedAt;

    @Column(name = "withdrawn_at")
    private LocalDateTime withdrawnAt;

    /**
     * The agreed price frozen at acceptance. Never changes after acceptance.
     */
    @Column(name = "agreed_amount", precision = 15, scale = 2)
    private BigDecimal agreedAmount;

    /** Optimistic locking — prevents concurrent double-acceptance. */
    @Version
    @Column(name = "version", nullable = false)
    @Builder.Default
    private Long version = 0L;

    @OneToMany(mappedBy = "carrierOffer", fetch = FetchType.LAZY,
               cascade = CascadeType.ALL, orphanRemoval = false)
    @OrderBy("createdAt ASC")
    @Builder.Default
    private List<OfferNegotiationEntry> negotiationHistory = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // ── Enums ─────────────────────────────────────────────────────────────────

    public enum PricingType {
        FIXED,
        NEGOTIABLE
    }

    public enum OfferStatus {
        PENDING,
        NEGOTIATING,
        ACCEPTED,
        REJECTED,
        WITHDRAWN,
        SUPERSEDED;

        /** True when no further state changes are possible. */
        public boolean isTerminal() {
            return this == ACCEPTED || this == REJECTED
                    || this == WITHDRAWN || this == SUPERSEDED;
        }

        /** True when counter-offers are still possible. */
        public boolean isActive() {
            return this == PENDING || this == NEGOTIATING;
        }
    }

    public enum ActorType {
        SHIPPER,
        CARRIER
    }
}
