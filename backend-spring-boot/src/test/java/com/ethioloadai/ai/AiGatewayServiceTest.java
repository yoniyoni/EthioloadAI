package com.ethioloadai.ai;

import com.ethioloadai.ai.dto.AiAssistantResponse;
import com.ethioloadai.ai.dto.AiBackhaulResponse;
import com.ethioloadai.ai.dto.AiPricePredictionResponse;
import com.ethioloadai.ai.dto.AiRecommendationResponse;
import com.ethioloadai.ai.service.AiGatewayService;
import com.ethioloadai.ai.service.AiGatewayServiceImpl;
import com.ethioloadai.freight.entity.Freight;
import com.ethioloadai.freight.repository.FreightRepository;
import com.ethioloadai.setting.dto.PricingSettingsResponse;
import com.ethioloadai.setting.service.PlatformSettingService;
import com.ethioloadai.vehicle.entity.Vehicle;
import com.ethioloadai.vehicle.repository.VehicleRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
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
        ReflectionTestUtils.setField(aiGatewayService, "aiEngineEnabled", false);
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
    @DisplayName("disabled AI integration makes no HTTP requests and uses local fallbacks")
    void disabledAiIntegrationUsesFallbacksWithoutHttpRequests() {
        RestTemplate httpClient = mock(RestTemplate.class);
        ReflectionTestUtils.setField(aiGatewayService, "restTemplate", httpClient);
        Vehicle eligibleVehicle = vehicle(10L, 5L, "3-A12345", "Addis Ababa");
        when(vehicleRepository.findByAvailabilityStatusAndApprovalStatus(
                "available", Vehicle.VehicleApprovalStatus.APPROVED))
                .thenReturn(List.of(eligibleVehicle));
        when(freightRepository.findByStatus(Freight.FreightStatus.POSTED))
                .thenReturn(List.of());

        AiPricePredictionResponse price = aiGatewayService.predictPrice(Map.of(
                "pickup_location", "Addis Ababa",
                "destination", "Adama",
                "weight", 10.0));
        AiRecommendationResponse recommendations = aiGatewayService.recommendTruck(Map.of("weight", 15.0));
        AiBackhaulResponse backhaul = aiGatewayService.backhaulOpportunities(Map.of());
        Map<String, Object> emptyReturn = aiGatewayService.predictEmptyReturn(
                Map.of("destination", "Adama"));
        Map<String, Object> optimizedRoute = aiGatewayService.optimizeRoute(Map.of(
                "origin", "Addis Ababa", "destination", "Adama"));

        assertThat(price.getSource()).isEqualTo("local_estimate");
        assertThat(recommendations.getRecommendedTrucks()).hasSize(1);
        assertThat(backhaul.getOpportunities()).isEmpty();
        assertThat(emptyReturn.get("source")).isEqualTo("local_estimate");
        assertThat(optimizedRoute.get("estimated_distance_km")).isEqualTo(99);
        verifyNoInteractions(httpClient);
    }

    @Test
    @DisplayName("recommendTruck fallback returns available vehicles scored")
    void testRecommendTruckFallback() {
        Vehicle v1 = vehicle(10L, 5L, "3-A12345", "Addis Ababa");

        when(vehicleRepository.findByAvailabilityStatusAndApprovalStatus(
                "available", Vehicle.VehicleApprovalStatus.APPROVED)).thenReturn(List.of(v1));

        AiRecommendationResponse response = aiGatewayService.recommendTruck(Map.of("weight", 15.0));

        assertNotNull(response);
        assertNotNull(response.getRecommendedTrucks());
        assertEquals(1, response.getRecommendedTrucks().size());
        assertEquals(10L, response.getRecommendedTrucks().get(0).get("truck_id"));
        assertEquals("3-A12345", response.getRecommendedTrucks().get(0).get("plate_number"));
        assertNotNull(response.getMatches()); // Alias matches
    }

    @Test
    @DisplayName("recommendTruck sends authoritative eligible vehicles to the AI engine")
    void recommendTruckReplacesCallerFleetWithApprovedAvailableVehicles() throws Exception {
        Vehicle eligibleVehicle = vehicle(10L, 5L, "3-A12345", "Addis Ababa");
        when(vehicleRepository.findByAvailabilityStatusAndApprovalStatus(
                "available", Vehicle.VehicleApprovalStatus.APPROVED)).thenReturn(List.of(eligibleVehicle));

        AtomicReference<String> receivedBody = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/ai/recommend-truck", exchange -> {
            receivedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] response = "{\"recommended_trucks\":[]}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            try (var output = exchange.getResponseBody()) {
                output.write(response);
            }
        });
        server.start();
        ReflectionTestUtils.setField(aiGatewayService, "aiEngineEnabled", true);
        ReflectionTestUtils.setField(aiGatewayService, "aiEngineUrl",
                "http://127.0.0.1:" + server.getAddress().getPort());

        try {
            aiGatewayService.recommendTruck(Map.of(
                    "pickup_location", "Addis Ababa",
                    "destination", "Adama",
                    "truck_fleet", List.of(Map.of("truck_id", 999, "plate_number", "CALLER-SUPPLIED"))
            ));
        } finally {
            server.stop(0);
        }

        JsonNode request = new ObjectMapper().readTree(receivedBody.get());
        assertThat(request.path("truck_fleet")).hasSize(1);
        assertThat(request.path("truck_fleet").get(0).path("truck_id").asLong()).isEqualTo(10L);
        assertThat(request.path("truck_fleet").get(0).path("plate_number").asText()).isEqualTo("3-A12345");
        assertThat(request.toString()).doesNotContain("CALLER-SUPPLIED");
        verify(vehicleRepository).findByAvailabilityStatusAndApprovalStatus(
                "available", Vehicle.VehicleApprovalStatus.APPROVED);
    }

    @Test
    @DisplayName("recommendTruck returns no candidates instead of demo or unapproved vehicles")
    void recommendTruckWithNoEligibleVehiclesReturnsEmptyList() {
        when(vehicleRepository.findByAvailabilityStatusAndApprovalStatus(
                "available", Vehicle.VehicleApprovalStatus.APPROVED)).thenReturn(List.of());

        AiRecommendationResponse response = aiGatewayService.recommendTruck(Map.of());

        assertThat(response.getRecommendedTrucks()).isEmpty();
        verify(vehicleRepository).findByAvailabilityStatusAndApprovalStatus(
                "available", Vehicle.VehicleApprovalStatus.APPROVED);
        verifyNoMoreInteractions(vehicleRepository);
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

    private Vehicle vehicle(Long id, Long userId, String plateNumber, String currentCity) {
        return Vehicle.builder()
                .id(id)
                .userId(userId)
                .plateNumber(plateNumber)
                .capacity(25.0)
                .currentCity(currentCity)
                .availabilityStatus("available")
                .approvalStatus(Vehicle.VehicleApprovalStatus.APPROVED)
                .build();
    }
}
