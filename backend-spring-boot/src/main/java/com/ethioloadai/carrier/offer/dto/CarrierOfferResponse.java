package com.ethioloadai.carrier.offer.dto;

import com.ethioloadai.carrier.offer.entity.CarrierOffer;
import com.ethioloadai.carrier.offer.entity.CarrierAssignmentStatus;

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
    Long proposedDriverUserId,
    String proposedDriverName,
    Long proposedVehicleId,
    String proposedVehiclePlate,
    String proposedVehicleType,
    CarrierOffer.PricingType pricingType,
    BigDecimal currentAmount,
    CarrierOffer.OfferStatus status,
    CarrierAssignmentStatus assignmentStatus,
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
            o.getProposedDriver() != null ? o.getProposedDriver().getId() : null,
            o.getProposedDriver() != null ? o.getProposedDriver().getFullName() : null,
            o.getProposedVehicle() != null ? o.getProposedVehicle().getId() : null,
            o.getProposedVehicle() != null ? o.getProposedVehicle().getPlateNumber() : null,
            o.getProposedVehicle() != null ? o.getProposedVehicle().getTruckType() : null,
            o.getPricingType(),
            o.getCurrentAmount(),
            o.getStatus(),
            o.getAssignmentStatus(),
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

    /** Summary view: no negotiation history or marketplace-private locations. */
    public static CarrierOfferResponse summary(CarrierOffer o) {
        return new CarrierOfferResponse(
            o.getId(),
            o.getFreight() != null ? o.getFreight().getId() : null,
            null,
            null,
            o.getCarrierCompany() != null ? o.getCarrierCompany().getId() : null,
            o.getCarrierCompany() != null ? o.getCarrierCompany().getCompanyName() : null,
            null, null, // submittedBy hidden in summary
            null, null, null, null, null, // hide the competing carrier's proposed pairing
            o.getPricingType(),
            o.getCurrentAmount(),
            o.getStatus(),
            o.getAssignmentStatus(),
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

    /** Carrier's own offer view; route details become available only after award. */
    public static CarrierOfferResponse forCarrier(CarrierOffer o, boolean includeOperationalRoute) {
        CarrierOfferResponse response = from(o);
        if (includeOperationalRoute && o.getStatus() == CarrierOffer.OfferStatus.ACCEPTED) {
            return response;
        }
        return new CarrierOfferResponse(
                response.id(),
                response.freightId(),
                null,
                null,
                response.carrierCompanyId(),
                response.carrierCompanyName(),
                response.submittedById(),
                response.submittedByName(),
                response.proposedDriverUserId(),
                response.proposedDriverName(),
                response.proposedVehicleId(),
                response.proposedVehiclePlate(),
                response.proposedVehicleType(),
                response.pricingType(),
                response.currentAmount(),
                response.status(),
                response.assignmentStatus(),
                response.nextActor(),
                response.agreedAmount(),
                response.acceptedAt(),
                response.rejectedAt(),
                response.withdrawnAt(),
                response.createdAt(),
                response.updatedAt(),
                response.negotiationHistory());
    }

    public CarrierOfferResponse withoutFinancials() {
        return new CarrierOfferResponse(
                id,
                freightId,
                freightPickupLocation,
                freightDestination,
                carrierCompanyId,
                carrierCompanyName,
                submittedById,
                submittedByName,
                proposedDriverUserId,
                proposedDriverName,
                proposedVehicleId,
                proposedVehiclePlate,
                proposedVehicleType,
                pricingType,
                null,
                status,
                assignmentStatus,
                nextActor,
                null,
                acceptedAt,
                rejectedAt,
                withdrawnAt,
                createdAt,
                updatedAt,
                negotiationHistory == null
                        ? List.of()
                        : negotiationHistory.stream()
                                .map(NegotiationEntryResponse::withoutAmount)
                                .toList());
    }
}
