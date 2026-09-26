package com.ethioloadai.ai.controller;

import com.ethioloadai.ai.dto.*;
import com.ethioloadai.ai.service.AiGatewayService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/ai")
@RequiredArgsConstructor
@Tag(name = "AI Gateway", description = "AI Proxy & Fallback Gateway endpoints")
public class AiGatewayController {
    private static final Logger log = LoggerFactory.getLogger(AiGatewayController.class);

    private final AiGatewayService aiGatewayService;

    @GetMapping("/price-prediction")
    @Operation(summary = "Price prediction (GET variant)")
    public ResponseEntity<AiPricePredictionResponse> predictPriceGet(@RequestParam Map<String, Object> queryParams) {
        log.info("GET /api/ai/price-prediction with params: {}", queryParams);
        return ResponseEntity.ok(aiGatewayService.predictPrice(queryParams));
    }

    @PostMapping("/predict-price")
    @Operation(summary = "Price prediction (POST variant)")
    public ResponseEntity<AiPricePredictionResponse> predictPricePost(@RequestBody(required = false) Map<String, Object> body) {
        log.info("POST /api/ai/predict-price with body: {}", body);
        return ResponseEntity.ok(aiGatewayService.predictPrice(body != null ? body : Map.of()));
    }

    @GetMapping({"/vehicle-recommendation", "/driver-recommendations"})
    @Operation(summary = "Vehicle recommendation (GET variant)")
    public ResponseEntity<AiRecommendationResponse> recommendTruckGet(@RequestParam Map<String, Object> queryParams) {
        log.info("GET /api/ai/vehicle-recommendation with params: {}", queryParams);
        return ResponseEntity.ok(aiGatewayService.recommendTruck(queryParams));
    }

    @PostMapping("/recommend-truck")
    @Operation(summary = "Truck recommendation (POST variant)")
    public ResponseEntity<AiRecommendationResponse> recommendTruckPost(@RequestBody(required = false) Map<String, Object> body) {
        log.info("POST /api/ai/recommend-truck with body: {}", body);
        return ResponseEntity.ok(aiGatewayService.recommendTruck(body != null ? body : Map.of()));
    }

    @PostMapping("/backhaul-opportunities")
    @Operation(summary = "Backhaul opportunities")
    public ResponseEntity<AiBackhaulResponse> backhaulOpportunities(@RequestBody(required = false) Map<String, Object> body) {
        log.info("POST /api/ai/backhaul-opportunities with body: {}", body);
        return ResponseEntity.ok(aiGatewayService.backhaulOpportunities(body != null ? body : Map.of()));
    }

    @PostMapping("/predict-empty-return")
    @Operation(summary = "Predict empty return probability")
    public ResponseEntity<Map<String, Object>> predictEmptyReturn(@RequestBody(required = false) Map<String, Object> body) {
        log.info("POST /api/ai/predict-empty-return with body: {}", body);
        return ResponseEntity.ok(aiGatewayService.predictEmptyReturn(body != null ? body : Map.of()));
    }

    @PostMapping("/optimize-route")
    @Operation(summary = "Optimize trip route")
    public ResponseEntity<Map<String, Object>> optimizeRoute(@RequestBody(required = false) Map<String, Object> body) {
        log.info("POST /api/ai/optimize-route with body: {}", body);
        return ResponseEntity.ok(aiGatewayService.optimizeRoute(body != null ? body : Map.of()));
    }

    @PostMapping("/freight-assistant")
    @Operation(summary = "Freight AI Assistant chat")
    public ResponseEntity<AiAssistantResponse> freightAssistant(@RequestBody(required = false) Map<String, Object> body) {
        log.info("POST /api/ai/freight-assistant with body: {}", body);
        String message = body != null ? String.valueOf(body.getOrDefault("message", body.getOrDefault("prompt", ""))) : "";
        Map<String, Object> context = body != null && body.get("context") instanceof Map ? (Map<String, Object>) body.get("context") : Map.of();
        return ResponseEntity.ok(aiGatewayService.chatAssistant(message, context));
    }
}
