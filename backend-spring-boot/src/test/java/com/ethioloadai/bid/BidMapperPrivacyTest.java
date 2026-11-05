package com.ethioloadai.bid;

import com.ethioloadai.bid.dto.BidResponse;
import com.ethioloadai.bid.entity.Bid;
import com.ethioloadai.bid.mapper.BidMapper;
import com.ethioloadai.freight.entity.Freight;
import com.ethioloadai.user.entity.User;
import com.ethioloadai.vehicle.entity.Vehicle;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class BidMapperPrivacyTest {

    private final BidMapper mapper = new BidMapper();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void fixedPriceBidIncludesOnlyMarketplaceSafeCargoSummary() throws Exception {
        BidResponse response = mapper.toResponse(bid(Freight.PriceType.FIXED));
        String json = objectMapper.writeValueAsString(response);

        assertThat(response.getMaterialType()).isEqualTo("grain");
        assertThat(response.getWeightTons()).isEqualByComparingTo("12");
        assertThat(response.getBudget()).isEqualByComparingTo("50000");
        assertPrivateFreightFieldsAbsent(response, json);
    }

    @Test
    void negotiableBidDoesNotExposeFreightBudget() throws Exception {
        BidResponse response = mapper.toResponse(bid(Freight.PriceType.NEGOTIABLE));
        String json = objectMapper.writeValueAsString(response);

        assertThat(response.getBudget()).isNull();
        assertThat(json).doesNotContain("\"budget\"");
        assertPrivateFreightFieldsAbsent(response, json);
    }

    private void assertPrivateFreightFieldsAbsent(BidResponse response, String json) {
        assertThat(response.getPickupLocation()).isNull();
        assertThat(response.getDestination()).isNull();
        assertThat(response.getShipperName()).isNull();
        assertThat(response.getShipperPhone()).isNull();
        assertThat(response.getDistanceKm()).isNull();
        assertThat(json)
                .doesNotContain("\"pickupLocation\"", "\"destination\"", "\"shipperName\"",
                        "\"shipperPhone\"", "\"distanceKm\"")
                .doesNotContain("Private pickup", "Private destination", "Shipper contact");
    }

    private Bid bid(Freight.PriceType priceType) {
        User shipper = User.builder().id(1L).fullName("Shipper contact").phone("555-0101").build();
        User driver = User.builder().id(2L).fullName("Driver").build();
        Vehicle vehicle = Vehicle.builder().id(3L).plateNumber("ABC-123").truckType("Truck").build();
        Freight freight = Freight.builder()
                .id(4L)
                .shipper(shipper)
                .pickupLocation("Private pickup")
                .destination("Private destination")
                .materialType("grain")
                .weightTons(new BigDecimal("12"))
                .priceType(priceType)
                .budget(new BigDecimal("50000"))
                .build();
        return Bid.builder()
                .id(5L)
                .cargoRequest(freight)
                .driver(driver)
                .vehicle(vehicle)
                .amount(new BigDecimal("45000"))
                .status(Bid.BidStatus.PENDING)
                .distanceKm(new BigDecimal("42.5"))
                .build();
    }
}
