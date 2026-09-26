package com.ethioloadai.ai;

import com.ethioloadai.ai.controller.AiGatewayController;
import com.ethioloadai.ai.dto.AiAssistantResponse;
import com.ethioloadai.ai.dto.AiPricePredictionResponse;
import com.ethioloadai.ai.dto.AiRecommendationResponse;
import com.ethioloadai.ai.service.AiGatewayService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AiGatewayControllerTest {

    @Test
    @DisplayName("GET and POST /api/ai/price-prediction return 200")
    void testPricePredictionEndpoints() {
        AiGatewayService service = mock(AiGatewayService.class);
        AiGatewayController controller = new AiGatewayController(service);

        AiPricePredictionResponse mockPrice = AiPricePredictionResponse.builder()
                .priceMin(20000)
                .priceMax(30000)
                .estimatedPrice(25000)
                .distanceKm(100)
                .currency("ETB")
                .build();

        when(service.predictPrice(anyMap())).thenReturn(mockPrice);

        ResponseEntity<AiPricePredictionResponse> getResp = controller.predictPriceGet(Map.of("weight", "10"));
        assertEquals(200, getResp.getStatusCode().value());
        assertEquals(25000, getResp.getBody().getEstimatedPrice());

        ResponseEntity<AiPricePredictionResponse> postResp = controller.predictPricePost(Map.of("weight", 10));
        assertEquals(200, postResp.getStatusCode().value());
        assertEquals(25000, postResp.getBody().getEstimatedPrice());
    }

    @Test
    @DisplayName("recommend-truck endpoints return 200")
    void testRecommendTruckEndpoints() {
        AiGatewayService service = mock(AiGatewayService.class);
        AiGatewayController controller = new AiGatewayController(service);

        AiRecommendationResponse mockRec = AiRecommendationResponse.builder()
                .recommendedTrucks(List.of(Map.of("truck_id", 1, "plate_number", "ET-1234")))
                .build();

        when(service.recommendTruck(anyMap())).thenReturn(mockRec);

        ResponseEntity<AiRecommendationResponse> getResp = controller.recommendTruckGet(Map.of());
        assertEquals(200, getResp.getStatusCode().value());
        assertEquals(1, getResp.getBody().getRecommendedTrucks().size());

        ResponseEntity<AiRecommendationResponse> postResp = controller.recommendTruckPost(Map.of());
        assertEquals(200, postResp.getStatusCode().value());
        assertEquals(1, postResp.getBody().getRecommendedTrucks().size());
    }

    @Test
    @DisplayName("POST /api/ai/freight-assistant returns assistant response")
    void testFreightAssistant() {
        AiGatewayService service = mock(AiGatewayService.class);
        AiGatewayController controller = new AiGatewayController(service);

        AiAssistantResponse mockAssistant = AiAssistantResponse.of("Hello!", "greeting");
        when(service.chatAssistant(anyString(), anyMap())).thenReturn(mockAssistant);

        ResponseEntity<AiAssistantResponse> resp = controller.freightAssistant(Map.of("message", "Hi"));
        assertEquals(200, resp.getStatusCode().value());
        assertEquals("Hello!", resp.getBody().getResponse().getText());
    }
}
