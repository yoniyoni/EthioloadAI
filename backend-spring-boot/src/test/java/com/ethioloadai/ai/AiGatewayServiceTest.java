package com.ethioloadai.ai;

import com.ethioloadai.ai.dto.AiAssistantResponse;
import com.ethioloadai.ai.dto.AiBackhaulResponse;
import com.ethioloadai.ai.dto.AiPricePredictionResponse;
import com.ethioloadai.ai.dto.AiRecommendationResponse;
import com.ethioloadai.ai.service.AiGatewayService;
import com.ethioloadai.ai.service.AiGatewayServiceImpl;
import com.ethioloadai.freight.repository.FreightRepository;
import com.ethioloadai.setting.dto.PricingSettingsResponse;
import com.ethioloadai.setting.service.PlatformSettingService;
import com.ethioloadai.vehicle.entity.Vehicle;
import com.ethioloadai.vehicle.repository.VehicleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AiGatewayServiceTest {

    private VehicleRepository vehicleRepository;
    private FreightRepository freightRepository;
    private PlatformSettingService platformSettingService;
    private AiGatewayService aiGatewayService;

    @BeforeEach
    void setUp() {
        vehicleRepository = mock(VehicleRepository.class);
        freightRepository = mock(FreightRepository.class);
        platformSettingService = mock(PlatformSettingService.class);

        when(platformSettingService.getPricingSettings()).thenReturn(
                PricingSettingsResponse.builder().rateMin(18.0).rateMax(28.0).build()
        );

        aiGatewayService = new AiGatewayServiceImpl(
                vehicleRepository,
                freightRepository,
                platformSettingService
        );
    }

    @Test
    @DisplayName("predictPrice fallback computes distance-based price in ETB")
    void testPredictPriceFallback() {
        Map<String, Object> params = Map.of(
                "pickup_location", "Addis Ababa",
                "destination", "Adama",
                "weight", 10.0,
                "urgency_level", "normal"
        );

        AiPricePredictionResponse response = aiGatewayService.predictPrice(params);

        assertNotNull(response);
        assertEquals("ETB", response.getCurrency());
        assertEquals(99, response.getDistanceKm()); // Addis to Adama = 99km
        assertTrue(response.getPriceMin() > 0);
        assertTrue(response.getPriceMax() >= response.getPriceMin());
        assertTrue(response.getEstimatedPrice() >= response.getPriceMin());
        assertEquals("local_estimate", response.getSource());
    }

    @Test
    @DisplayName("recommendTruck fallback returns available vehicles scored")
    void testRecommendTruckFallback() {
        Vehicle v1 = new Vehicle();
        v1.setId(10L);
        v1.setPlateNumber("3-A12345");
        v1.setCapacity(25.0);
        v1.setAvailabilityStatus("available");
        v1.setUserId(5L);

        when(vehicleRepository.findByAvailabilityStatus("available")).thenReturn(List.of(v1));

        AiRecommendationResponse response = aiGatewayService.recommendTruck(Map.of("weight", 15.0));

        assertNotNull(response);
        assertNotNull(response.getRecommendedTrucks());
        assertEquals(1, response.getRecommendedTrucks().size());
        assertEquals(10L, response.getRecommendedTrucks().get(0).get("truck_id"));
        assertEquals("3-A12345", response.getRecommendedTrucks().get(0).get("plate_number"));
        assertNotNull(response.getMatches()); // Alias matches
    }

    @Test
    @DisplayName("predictEmptyReturn fallback returns risk classification")
    void testPredictEmptyReturnFallback() {
        Map<String, Object> params = Map.of("destination", "Adama");
        Map<String, Object> response = aiGatewayService.predictEmptyReturn(params);

        assertNotNull(response);
        assertEquals("Adama", response.get("destination"));
        assertEquals("Low", response.get("risk_level"));
        assertEquals("local_estimate", response.get("source"));
    }

    @Test
    @DisplayName("chatAssistant returns intelligent responses by domain intent")
    void testChatAssistant() {
        AiAssistantResponse priceHelp = aiGatewayService.chatAssistant("What is the price for freight to Adama?", Map.of());
        assertNotNull(priceHelp.getResponse());
        assertEquals("pricing_query", priceHelp.getResponse().getIntent());

        AiAssistantResponse truckHelp = aiGatewayService.chatAssistant("I need a heavy truck for my cargo", Map.of());
        assertEquals("truck_search", truckHelp.getResponse().getIntent());
    }
}
