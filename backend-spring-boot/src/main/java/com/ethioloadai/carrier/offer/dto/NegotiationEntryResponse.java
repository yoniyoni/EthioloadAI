package com.ethioloadai.carrier.offer.dto;

import com.ethioloadai.carrier.offer.entity.CarrierOffer;
import com.ethioloadai.carrier.offer.entity.OfferNegotiationEntry;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Phase 5 — Immutable negotiation history entry for the API response.
 */
public record NegotiationEntryResponse(
    Long id,
    CarrierOffer.ActorType actorType,
    Long actorUserId,
    OfferNegotiationEntry.NegotiationAction action,
    BigDecimal amount,
    String note,
    LocalDateTime createdAt
) {
    public static NegotiationEntryResponse from(OfferNegotiationEntry e) {
        return new NegotiationEntryResponse(
            e.getId(),
            e.getActorType(),
            e.getActorUserId(),
            e.getAction(),
            e.getAmount(),
            e.getNote(),
            e.getCreatedAt()
        );
    }
}
