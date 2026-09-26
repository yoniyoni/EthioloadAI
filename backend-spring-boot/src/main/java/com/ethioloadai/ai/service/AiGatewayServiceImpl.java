package com.ethioloadai.ai.service;

import com.ethioloadai.ai.dto.*;
import com.ethioloadai.freight.entity.Freight;
import com.ethioloadai.freight.repository.FreightRepository;
import com.ethioloadai.setting.dto.PricingSettingsResponse;
import com.ethioloadai.setting.service.PlatformSettingService;
import com.ethioloadai.vehicle.entity.Vehicle;
import com.ethioloadai.vehicle.repository.VehicleRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.*;

@Service
@RequiredArgsConstructor
public class AiGatewayServiceImpl implements AiGatewayService {
    private static final Logger log = LoggerFactory.getLogger(AiGatewayServiceImpl.class);

    private final VehicleRepository vehicleRepository;
    private final FreightRepository freightRepository;
    private final PlatformSettingService platformSettingService;

    @Value("${ai.engine.url:http://localhost:8000}")
    private String aiEngineUrl;

    private final RestTemplate restTemplate = new RestTemplateBuilder()
            .setConnectTimeout(Duration.ofMillis(1500))
            .setReadTimeout(Duration.ofMillis(2000))
            .build();

    // ── Road Distances from Addis Ababa (km) ──
    private static final Map<String, Integer> DIST_FROM_AA = Map.ofEntries(
            Map.entry("adama", 99),
            Map.entry("asella", 175),
            Map.entry("awash", 225),
            Map.entry("bahir dar", 565),
            Map.entry("bishoftu", 50),
            Map.entry("bale robe", 430),
            Map.entry("debre birhan", 130),
            Map.entry("debre markos", 300),
            Map.entry("dessie", 401),
            Map.entry("kombolcha", 395),
            Map.entry("dilla", 367),
            Map.entry("dire dawa", 515),
            Map.entry("gambela", 769),
            Map.entry("goba", 445),
            Map.entry("gondar", 738),
            Map.entry("harar", 526),
            Map.entry("hawassa", 275),
            Map.entry("humera", 950),
            Map.entry("jijiga", 633),
            Map.entry("jimma", 346),
            Map.entry("kebri dahar", 840),
            Map.entry("mekele", 783),
            Map.entry("moyale", 770),
            Map.entry("nekemte", 331),
            Map.entry("shashemene", 250),
            Map.entry("shire", 900),
            Map.entry("axum", 1020),
            Map.entry("sodo", 370),
            Map.entry("woldia", 521),
            Map.entry("arba minch", 505)
    );

    @Override
    public AiPricePredictionResponse predictPrice(Map<String, Object> params) {
        // Try forwarding to FastAPI ai_engine
        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    aiEngineUrl + "/ai/predict-price",
                    new HttpEntity<>(params),
                    Map.class
            );
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map body = response.getBody();
                return AiPricePredictionResponse.builder()
                        .priceMin(asInt(body.get("price_min"), 25000))
                        .priceMax(asInt(body.get("price_max"), 35000))
                        .estimatedPrice(asInt(body.get("estimated_price"), 30000))
                        .distanceKm(asInt(body.get("distance_km"), 300))
                        .currency("ETB")
                        .confidence(asDouble(body.get("confidence"), 0.88))
                        .source("ai_engine")
                        .build();
            }
        } catch (Exception e) {
            log.debug("AI engine unavailable for predict-price ({}), executing local estimate", e.getMessage());
        }

        // Fallback: Local deterministic Ethiopian price calculation
        return localPriceEstimate(params);
    }

    @Override
    public AiRecommendationResponse recommendTruck(Map<String, Object> params) {
        // Try forwarding to FastAPI
        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    aiEngineUrl + "/ai/recommend-truck",
                    new HttpEntity<>(params),
                    Map.class
            );
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map body = response.getBody();
                List<Map<String, Object>> trucks = (List<Map<String, Object>>) body.get("recommended_trucks");
                if (trucks != null) {
                    return AiRecommendationResponse.builder().recommendedTrucks(trucks).build();
                }
            }
        } catch (Exception e) {
            log.debug("AI engine unavailable for recommend-truck ({}), executing local recommendation", e.getMessage());
        }

        // Fallback: Local scoring using database vehicles
        return localTruckRecommend(params);
    }

    @Override
    public AiBackhaulResponse backhaulOpportunities(Map<String, Object> params) {
        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    aiEngineUrl + "/ai/backhaul-opportunities",
                    new HttpEntity<>(params),
                    Map.class
            );
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map body = response.getBody();
                List<Map<String, Object>> opps = (List<Map<String, Object>>) body.get("opportunities");
                if (opps != null) {
                    return AiBackhaulResponse.builder().opportunities(opps).build();
                }
            }
        } catch (Exception e) {
            log.debug("AI engine unavailable for backhaul-opportunities ({}), executing local query", e.getMessage());
        }

        // Fallback: Query pending freight from database
        List<Freight> pendingLoads = freightRepository.findByStatus(Freight.FreightStatus.POSTED);
        List<Map<String, Object>> opps = new ArrayList<>();
        for (Freight f : pendingLoads) {
            opps.add(Map.of(
                    "cargo_id", f.getId(),
                    "pickup_location", f.getPickupLocation() != null ? f.getPickupLocation() : "Addis Ababa",
                    "destination", f.getDestination() != null ? f.getDestination() : "Adama",
                    "weight", f.getWeightTons() != null ? f.getWeightTons().doubleValue() : 10.0,
                    "price", f.getBudget() != null ? f.getBudget().doubleValue() : 20000.0
            ));
        }
        return AiBackhaulResponse.builder().opportunities(opps).build();
    }

    @Override
    public Map<String, Object> predictEmptyReturn(Map<String, Object> params) {
        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    aiEngineUrl + "/ai/predict-empty-return",
                    new HttpEntity<>(params),
                    Map.class
            );
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return response.getBody();
            }
        } catch (Exception e) {
            log.debug("AI engine unavailable for predict-empty-return ({}), using local probability", e.getMessage());
        }

        String dest = String.valueOf(params.getOrDefault("destination", "Adama")).toLowerCase();
        double prob = dest.contains("addis") ? 0.10 : (dest.contains("adama") ? 0.15 : (dest.contains("hawassa") ? 0.25 : 0.45));
        String risk = prob < 0.25 ? "Low" : (prob < 0.40 ? "Medium" : "High");

        return Map.of(
                "destination", params.getOrDefault("destination", "Adama"),
                "empty_return_probability", prob,
                "risk_level", risk,
                "recommendation", "Consider coordinating backhaul load before return trip.",
                "source", "local_estimate"
        );
    }

    @Override
    public Map<String, Object> optimizeRoute(Map<String, Object> params) {
        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    aiEngineUrl + "/ai/optimize-route",
                    new HttpEntity<>(params),
                    Map.class
            );
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return response.getBody();
            }
        } catch (Exception e) {
            log.debug("AI engine unavailable for optimize-route ({}), returning structured response", e.getMessage());
        }

        return Map.of(
                "origin", params.getOrDefault("origin", "Addis Ababa"),
                "destination", params.getOrDefault("destination", "Adama"),
                "estimated_distance_km", 99,
                "estimated_duration_hours", 1.8,
                "optimized_waypoints", params.getOrDefault("waypoints", List.of())
        );
    }

    @Override
    public AiAssistantResponse chatAssistant(String message, Map<String, Object> context) {
        String msgLower = message != null ? message.toLowerCase() : "";
        String text;
        String intent;

        if (msgLower.contains("price") || msgLower.contains("cost") || msgLower.contains("rate")) {
            text = "Freight rates on standard corridors typically range from 18 to 28 ETB/km/ton. Use our price prediction tool for a real-time quote based on your cargo weight and route.";
            intent = "pricing_query";
        } else if (msgLower.contains("truck") || msgLower.contains("vehicle") || msgLower.contains("driver")) {
            text = "We have active verified drivers and trucks across all major Ethiopian freight corridors. You can post your load to receive bids directly from nearby drivers.";
            intent = "truck_search";
        } else if (msgLower.contains("track") || msgLower.contains("where") || msgLower.contains("status")) {
            text = "You can track your shipments in real-time on the Tracking page using GPS updates from the assigned vehicle.";
            intent = "tracking_query";
        } else {
            text = "Welcome to EthioLoad AI Logistics Assistant. I can assist you with rate estimation, truck matching, route optimization, and shipment tracking across Ethiopia.";
            intent = "general_support";
        }

        return AiAssistantResponse.of(text, intent);
    }

    // ── Local Fallback Helpers ──

    private AiPricePredictionResponse localPriceEstimate(Map<String, Object> params) {
        String from = String.valueOf(params.getOrDefault("pickup_location", params.getOrDefault("from", "Addis Ababa"))).toLowerCase();
        String to = String.valueOf(params.getOrDefault("destination", params.getOrDefault("to", "Adama"))).toLowerCase();
        double weight = asDouble(params.get("weight"), asDouble(params.get("weight_tons"), 10.0));
        if (weight <= 0) weight = 10.0;

        int distKm = computeDistanceKm(from, to);

        PricingSettingsResponse settings = platformSettingService.getPricingSettings();
        double rateMin = settings != null && settings.getRateMin() != null ? settings.getRateMin() : 18.0;
        double rateMax = settings != null && settings.getRateMax() != null ? settings.getRateMax() : 28.0;

        String urgency = String.valueOf(params.getOrDefault("urgency_level", "normal")).toLowerCase();
        double urgencyMul = urgency.contains("express") ? 1.4 : (urgency.contains("high") ? 1.2 : 1.0);

        String material = String.valueOf(params.getOrDefault("material_type", params.getOrDefault("cargo_type", "general"))).toLowerCase();
        double materialMul = material.contains("fragile") ? 1.25 : (material.contains("perish") ? 1.15 : 1.0);

        double baseMin = distKm * rateMin * weight * urgencyMul * materialMul;
        double baseMax = distKm * rateMax * weight * urgencyMul * materialMul;

        int priceMin = (int) (Math.round(baseMin / 500.0) * 500);
        int priceMax = (int) (Math.round(baseMax / 500.0) * 500);
        int estimated = (priceMin + priceMax) / 2;

        return AiPricePredictionResponse.builder()
                .priceMin(priceMin)
                .priceMax(priceMax)
                .estimatedPrice(estimated)
                .distanceKm(distKm)
                .currency("ETB")
                .confidence(0.85)
                .source("local_estimate")
                .build();
    }

    private AiRecommendationResponse localTruckRecommend(Map<String, Object> params) {
        List<Vehicle> availableVehicles = vehicleRepository.findByAvailabilityStatus("available");
        if (availableVehicles.isEmpty()) {
            availableVehicles = vehicleRepository.findAll();
        }

        double weight = asDouble(params.get("weight"), 10.0);
        List<Map<String, Object>> recommendations = new ArrayList<>();

        for (Vehicle v : availableVehicles) {
            double capacity = v.getCapacity() != null ? v.getCapacity().doubleValue() : 20.0;
            double score = Math.min(1.0, Math.max(0.70, (capacity / Math.max(weight, 1.0)) * 0.8));
            recommendations.add(Map.of(
                    "truck_id", v.getId(),
                    "plate_number", v.getPlateNumber() != null ? v.getPlateNumber() : "ET-3-12345",
                    "driver_name", v.getUserId() != null ? "Driver #" + v.getUserId() : "Assigned Driver",
                    "capacity", capacity,
                    "distance_km", 15.0,
                    "estimated_price", 28000,
                    "score", Math.round(score * 100.0) / 100.0
            ));
        }

        return AiRecommendationResponse.builder().recommendedTrucks(recommendations).build();
    }

    private int computeDistanceKm(String cityA, String cityB) {
        Integer dA = null;
        Integer dB = null;
        for (Map.Entry<String, Integer> e : DIST_FROM_AA.entrySet()) {
            if (cityA.contains(e.getKey())) dA = e.getValue();
            if (cityB.contains(e.getKey())) dB = e.getValue();
        }

        if (cityA.contains("addis") && dB != null) return dB;
        if (cityB.contains("addis") && dA != null) return dA;
        if (dA != null && dB != null) return Math.abs(dA - dB) + 20;

        return 300; // Default reasonable intercity estimate
    }

    private int asInt(Object val, int def) {
        if (val instanceof Number n) return n.intValue();
        if (val != null) {
            try { return Integer.parseInt(val.toString()); } catch (Exception ignored) {}
        }
        return def;
    }

    private double asDouble(Object val, double def) {
        if (val instanceof Number n) return n.doubleValue();
        if (val != null) {
            try { return Double.parseDouble(val.toString()); } catch (Exception ignored) {}
        }
        return def;
    }
}
