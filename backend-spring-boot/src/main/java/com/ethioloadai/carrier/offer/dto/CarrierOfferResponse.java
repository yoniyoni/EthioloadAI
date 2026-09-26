package com.ethioloadai.carrier.offer.dto;

import com.ethioloadai.carrier.offer.entity.CarrierOffer;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Phase 5 — Full carrier offer response including negotiation history.
 *
 * <p>The {@code negotiationHistory} list is included when the caller is
 * authorized to see it (freight owner, the carrier company member, or admin).
 * Other carriers see a summary view without the history — enforced at the
 * service layer, not here.
 */
public record CarrierOfferResponse(
    Long id,
    Long freightId,
    String freightPickupLocation,
    String freightDestination,
    Long carrierCompanyId,
    String carrierCompanyName,
    Long submittedById,
    String submittedByName,
    CarrierOffer.PricingType pricingType,
    BigDecimal currentAmount,
    CarrierOffer.OfferStatus status,
    CarrierOffer.ActorType nextActor,
    BigDecimal agreedAmount,
    LocalDateTime acceptedAt,
    LocalDateTime rejectedAt,
    LocalDateTime withdrawnAt,
    LocalDateTime createdAt,
    LocalDateTime updatedAt,
    List<NegotiationEntryResponse> negotiationHistory
) {
    /** Build from entity; includes full history. */
    public static CarrierOfferResponse from(CarrierOffer o) {
        List<NegotiationEntryResponse> history = o.getNegotiationHistory() == null
            ? List.of()
            : o.getNegotiationHistory().stream()
                .map(NegotiationEntryResponse::from)
                .toList();

        return new CarrierOfferResponse(
            o.getId(),
            o.getFreight() != null ? o.getFreight().getId() : null,
            o.getFreight() != null ? o.getFreight().getPickupLocation() : null,
            o.getFreight() != null ? o.getFreight().getDestination() : null,
            o.getCarrierCompany() != null ? o.getCarrierCompany().getId() : null,
            o.getCarrierCompany() != null ? o.getCarrierCompany().getCompanyName() : null,
            o.getSubmittedBy() != null ? o.getSubmittedBy().getId() : null,
            o.getSubmittedBy() != null ? o.getSubmittedBy().getFullName() : null,
            o.getPricingType(),
            o.getCurrentAmount(),
            o.getStatus(),
            o.getNextActor(),
            o.getAgreedAmount(),
            o.getAcceptedAt(),
            o.getRejectedAt(),
            o.getWithdrawnAt(),
            o.getCreatedAt(),
            o.getUpdatedAt(),
            history
        );
    }

    /** Summary view: no negotiation history (for public/other-carrier views). */
    public static CarrierOfferResponse summary(CarrierOffer o) {
        return new CarrierOfferResponse(
            o.getId(),
            o.getFreight() != null ? o.getFreight().getId() : null,
            o.getFreight() != null ? o.getFreight().getPickupLocation() : null,
            o.getFreight() != null ? o.getFreight().getDestination() : null,
            o.getCarrierCompany() != null ? o.getCarrierCompany().getId() : null,
            o.getCarrierCompany() != null ? o.getCarrierCompany().getCompanyName() : null,
            null, null, // submittedBy hidden in summary
            o.getPricingType(),
            o.getCurrentAmount(),
            o.getStatus(),
            o.getNextActor(),
            o.getAgreedAmount(),
            o.getAcceptedAt(),
            o.getRejectedAt(),
            o.getWithdrawnAt(),
            o.getCreatedAt(),
            o.getUpdatedAt(),
            List.of()
        );
    }
}
