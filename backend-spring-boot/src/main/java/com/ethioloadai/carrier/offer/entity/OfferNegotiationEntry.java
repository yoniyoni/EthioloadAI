package com.ethioloadai.carrier.offer.entity;

import com.ethioloadai.user.entity.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Phase 5 — Immutable negotiation history entry for a {@link CarrierOffer}.
 *
 * <p>Every commercial action (offer, counter-offer, accept, reject, withdraw)
 * appends exactly one row. Rows are <strong>never updated or deleted</strong>,
 * providing a full auditable commercial trail.
 *
 * <p>Design notes:
 * <ul>
 *   <li>No {@code @UpdateTimestamp} — the row is immutable after insert.</li>
 *   <li>{@code amount} is NULL only for terminal actions (ACCEPT/REJECT/WITHDRAW)
 *       where the price is already recorded on the parent offer.</li>
 *   <li>{@code actorUserId} is stored as a plain scalar (not FK-navigated) to
 *       keep history self-contained even if the user record changes.</li>
 * </ul>
 */
@Entity
@Table(name = "offer_negotiation_entries")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OfferNegotiationEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "carrier_offer_id", nullable = false, updatable = false)
    private CarrierOffer carrierOffer;

    @Enumerated(EnumType.STRING)
    @Column(name = "actor_type", nullable = false, length = 10, updatable = false)
    private CarrierOffer.ActorType actorType;

    /** User who performed this action. Scalar for immutable audit trail. */
    @Column(name = "actor_user_id", nullable = false, updatable = false)
    private Long actorUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 20, updatable = false)
    private NegotiationAction action;

    /**
     * Amount proposed at this step. Required for OFFER and COUNTER_OFFER.
     * NULL for ACCEPT, REJECT, WITHDRAW (price already on parent offer).
     */
    @Column(name = "amount", precision = 15, scale = 2, updatable = false)
    private BigDecimal amount;

    @Column(name = "note", length = 1000, updatable = false)
    private String note;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // ── Enum ──────────────────────────────────────────────────────────────────

    public enum NegotiationAction {
        OFFER,
        COUNTER_OFFER,
        ACCEPT,
        REJECT,
        WITHDRAW
    }
}
