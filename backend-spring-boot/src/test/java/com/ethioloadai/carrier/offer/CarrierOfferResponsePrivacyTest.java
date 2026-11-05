package com.ethioloadai.carrier.offer;

import com.ethioloadai.carrier.company.entity.CarrierCompany;
import com.ethioloadai.carrier.offer.dto.CarrierOfferResponse;
import com.ethioloadai.carrier.offer.entity.CarrierOffer;
import com.ethioloadai.freight.entity.Freight;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CarrierOfferResponsePrivacyTest {

    @Test
    void carrierOfferViewHidesRouteUntilOfferIsAwarded() {
        CarrierOffer pending = offer(CarrierOffer.OfferStatus.PENDING);

        CarrierOfferResponse response = CarrierOfferResponse.forCarrier(pending, true);

        assertThat(response.freightPickupLocation()).isNull();
        assertThat(response.freightDestination()).isNull();
    }

    @Test
    void acceptedCarrierOfferMayExposeOperationalRoute() {
        CarrierOffer accepted = offer(CarrierOffer.OfferStatus.ACCEPTED);

        CarrierOfferResponse response = CarrierOfferResponse.forCarrier(accepted, true);

        assertThat(response.freightPickupLocation()).isEqualTo("Private pickup");
        assertThat(response.freightDestination()).isEqualTo("Private destination");
    }

    @Test
    void competingOfferSummaryNeverExposesRouteOrProposedPairing() {
        CarrierOffer pending = offer(CarrierOffer.OfferStatus.PENDING);

        CarrierOfferResponse response = CarrierOfferResponse.summary(pending);

        assertThat(response.freightPickupLocation()).isNull();
        assertThat(response.freightDestination()).isNull();
        assertThat(response.proposedDriverUserId()).isNull();
        assertThat(response.proposedVehicleId()).isNull();
    }

    private CarrierOffer offer(CarrierOffer.OfferStatus status) {
        Freight freight = Freight.builder()
                .id(10L)
                .pickupLocation("Private pickup")
                .destination("Private destination")
                .build();
        CarrierCompany company = CarrierCompany.builder().companyName("Carrier").build();
        return CarrierOffer.builder()
                .id(20L)
                .freight(freight)
                .carrierCompany(company)
                .pricingType(CarrierOffer.PricingType.NEGOTIABLE)
                .status(status)
                .build();
    }
}
