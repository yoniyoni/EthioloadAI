package com.ethioloadai.trip.service;

import com.ethioloadai.trip.entity.Trip;
import com.ethioloadai.trip.entity.TripStop;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor
public class MultiStopPricingServiceImpl implements MultiStopPricingService {
    private static final Logger log = LoggerFactory.getLogger(MultiStopPricingServiceImpl.class);

    private final MultiStopBusinessRulesService businessRulesService;

    // Pricing constants from Laravel reference
    private static final BigDecimal FUEL_PRICE_PER_LITER = new BigDecimal("55.0"); // ETB
    private static final BigDecimal AVERAGE_FUEL_EFFICIENCY = new BigDecimal("8.5"); // km per liter for trucks
    private static final String CURRENCY = "ETB";

    // Empty return risk by city (from Laravel reference)
    private static final Map<String, BigDecimal> EMPTY_RETURN_RISKS;
    
    static {
        Map<String, BigDecimal> risks = new HashMap<>();
        risks.put("addis_ababa", new BigDecimal("0.10"));
        risks.put("adama", new BigDecimal("0.15"));
        risks.put("bishoftu", new BigDecimal("0.18"));
        risks.put("shashemene", new BigDecimal("0.22"));
        risks.put("hawassa", new BigDecimal("0.25"));
        risks.put("jimma", new BigDecimal("0.30"));
        risks.put("dire_dawa", new BigDecimal("0.35"));
        risks.put("bahir_dar", new BigDecimal("0.38"));
        risks.put("gondar", new BigDecimal("0.42"));
        risks.put("dessie", new BigDecimal("0.45"));
        risks.put("mekele", new BigDecimal("0.50"));
        risks.put("gambela", new BigDecimal("0.75"));
        risks.put("moyale", new BigDecimal("0.80"));
        EMPTY_RETURN_RISKS = Map.copyOf(risks);
    }

    @Override
    public MultiStopPricingResult calculateMultiStopPricing(List<TripStop> stops, BigDecimal baseWeight,
                                                           String material, String urgency) {
        log.debug("Calculating multi-stop pricing for {} stops", stops.size());

        List<StopPricingDetail> stopBreakdown = new ArrayList<>();
        BigDecimal totalBasePrice = BigDecimal.ZERO;
        BigDecimal totalDistance = BigDecimal.ZERO;
        BigDecimal estimatedDuration = BigDecimal.ZERO;

        // Sort stops by order
        List<TripStop> orderedStops = stops.stream()
                .sorted(Comparator.comparing(TripStop::getStopOrder))
                .toList();

        // Calculate pricing for each segment
        for (int i = 0; i < orderedStops.size(); i++) {
            TripStop currentStop = orderedStops.get(i);
            String fromLocation = i == 0 ? "origin" : orderedStops.get(i - 1).getLocationName();
            String toLocation = currentStop.getLocationName();

            MultiStopBusinessRulesService.PricingEstimate estimate = 
                businessRulesService.calculateStopPricing(fromLocation, toLocation, baseWeight, material, urgency);

            StopPricingDetail detail = new StopPricingDetail(
                currentStop.getStopOrder(),
                fromLocation,
                toLocation,
                estimate.distanceKm(),
                estimate.estimatedPrice(),
                currentStop.getAgreedPrice() != null ? currentStop.getAgreedPrice() : estimate.estimatedPrice(),
                estimate.multipliers()
            );

            stopBreakdown.add(detail);
            totalBasePrice = totalBasePrice.add(estimate.estimatedPrice());
            totalDistance = totalDistance.add(estimate.distanceKm());
        }

        // Estimate duration (60 km/h average + stops)
        estimatedDuration = totalDistance.divide(new BigDecimal("60"), RoundingMode.HALF_UP)
                .add(new BigDecimal(stops.size()).multiply(new BigDecimal("0.5"))); // 30min per stop

        // Calculate recommended total (considering empty return risk)
        String finalDestination = orderedStops.get(orderedStops.size() - 1).getLocationName();
        EmptyReturnAnalysis emptyReturnAnalysis = analyzeEmptyReturnRisk(finalDestination);
        BigDecimal recommendedTotal = totalBasePrice.multiply(
            BigDecimal.ONE.add(emptyReturnAnalysis.suggestedMarkup()));

        // Aggregate multipliers
        Map<String, BigDecimal> overallMultipliers = Map.of(
            "empty_return_adjustment", emptyReturnAnalysis.suggestedMarkup(),
            "total_distance_km", totalDistance,
            "estimated_duration_hours", estimatedDuration
        );

        BigDecimal confidence = new BigDecimal("0.85"); // High confidence for multi-stop

        log.debug("Multi-stop pricing complete: {} stops, {}km, {} ETB", 
                 stops.size(), totalDistance, recommendedTotal);

        return new MultiStopPricingResult(
            totalBasePrice,
            totalDistance,
            estimatedDuration,
            stopBreakdown,
            overallMultipliers,
            recommendedTotal,
            CURRENCY,
            confidence
        );
    }

    @Override
    public EmptyReturnAnalysis analyzeEmptyReturnRisk(String finalDestination) {
        String normalizedDestination = normalizeCity(finalDestination);
        BigDecimal probability = EMPTY_RETURN_RISKS.getOrDefault(normalizedDestination, new BigDecimal("0.65"));

        String riskLevel;
        String recommendation;
        BigDecimal suggestedMarkup;

        if (probability.compareTo(new BigDecimal("0.30")) < 0) {
            riskLevel = "Low";
            recommendation = "High availability of backhaul cargo. Proceed with standard pricing.";
            suggestedMarkup = BigDecimal.ZERO;
        } else if (probability.compareTo(new BigDecimal("0.55")) < 0) {
            riskLevel = "Medium";
            recommendation = "Moderate risk of empty return. Consider pre-booking a backhaul or adding 5-10% markup.";
            suggestedMarkup = new BigDecimal("0.075"); // 7.5%
        } else {
            riskLevel = "High";
            int percentChance = probability.multiply(new BigDecimal("100")).intValue();
            recommendation = String.format("%d%% chance of returning empty. Factor round-trip cost (add 15-25%%) or arrange backhaul.", percentChance);
            suggestedMarkup = new BigDecimal("0.20"); // 20%
        }

        BigDecimal estimatedReturnCost = probability.multiply(new BigDecimal("5000")); // Estimate return cost

        log.debug("Empty return analysis for {}: {}% risk, {} markup", 
                 finalDestination, probability.multiply(new BigDecimal("100")), suggestedMarkup);

        return new EmptyReturnAnalysis(
            finalDestination,
            probability,
            riskLevel,
            recommendation,
            suggestedMarkup,
            estimatedReturnCost
        );
    }

    @Override
    public PricingEstimate calculateDistanceBasedPricing(String fromLocation, String toLocation,
                                                        BigDecimal weight, String material, String urgency) {
        MultiStopBusinessRulesService.PricingEstimate businessEstimate = 
            businessRulesService.calculateStopPricing(fromLocation, toLocation, weight, material, urgency);

        Map<String, Object> metadata = Map.of(
            "calculation_method", "distance_based",
            "weight_tons", weight,
            "material_type", material != null ? material : "standard",
            "urgency_level", urgency != null ? urgency : "standard"
        );

        return new PricingEstimate(
            businessEstimate.priceMin(),
            businessEstimate.priceMax(),
            businessEstimate.estimatedPrice(),
            businessEstimate.distanceKm(),
            businessEstimate.currency(),
            businessEstimate.confidence(),
            businessEstimate.source(),
            metadata
        );
    }

    @Override
    public PricingOptimization optimizeMultiStopPricing(Trip trip) {
        if (trip.getTripStops() == null || trip.getTripStops().isEmpty()) {
            return new PricingOptimization(
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, List.of(), "no_stops"
            );
        }

        BigDecimal originalTotal = trip.getTripStops().stream()
                .map(stop -> stop.getAgreedPrice() != null ? stop.getAgreedPrice() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<OptimizationSuggestion> suggestions = new ArrayList<>();

        // Suggest route optimization
        if (trip.getTripStops().size() > 2) {
            suggestions.add(new OptimizationSuggestion(
                "route_optimization",
                "Consider optimizing stop sequence to reduce total distance",
                new BigDecimal("500"), // Estimated savings
                "Route optimization can reduce fuel costs and time"
            ));
        }

        // Suggest consolidation
        if (trip.getTripStops().size() > 3) {
            suggestions.add(new OptimizationSuggestion(
                "cargo_consolidation", 
                "Consolidate similar destinations to reduce stops",
                new BigDecimal("300"),
                "Fewer stops reduce loading/unloading time and costs"
            ));
        }

        // Calculate optimized total (10% potential savings on average)
        BigDecimal potentialSavings = suggestions.stream()
                .map(OptimizationSuggestion::impact)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        BigDecimal optimizedTotal = originalTotal.subtract(potentialSavings);

        log.debug("Pricing optimization for trip {}: {} ETB → {} ETB (savings: {} ETB)", 
                 trip.getId(), originalTotal, optimizedTotal, potentialSavings);

        return new PricingOptimization(
            originalTotal,
            optimizedTotal,
            potentialSavings,
            suggestions,
            "multi_stop_optimization"
        );
    }

    @Override
    public FuelCostAnalysis calculateFuelCosts(List<TripStop> stops, String vehicleType) {
        BigDecimal totalDistance = stops.stream()
                .map(stop -> businessRulesService.calculateDistance("prev", stop.getLocationName()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Adjust fuel efficiency based on vehicle type
        BigDecimal fuelEfficiency = getFuelEfficiencyForVehicle(vehicleType);
        BigDecimal estimatedFuelLiters = totalDistance.divide(fuelEfficiency, 2, RoundingMode.HALF_UP);
        BigDecimal fuelCost = estimatedFuelLiters.multiply(FUEL_PRICE_PER_LITER);

        Map<String, BigDecimal> costBreakdown = Map.of(
            "fuel_liters", estimatedFuelLiters,
            "fuel_price_per_liter", FUEL_PRICE_PER_LITER,
            "total_fuel_cost", fuelCost,
            "cost_per_km", fuelCost.divide(totalDistance, 2, RoundingMode.HALF_UP)
        );

        log.debug("Fuel analysis: {}km, {}L fuel, {} ETB", totalDistance, estimatedFuelLiters, fuelCost);

        return new FuelCostAnalysis(
            totalDistance,
            estimatedFuelLiters,
            fuelCost,
            fuelEfficiency,
            costBreakdown
        );
    }

    @Override
    public DynamicPricingAdjustment getDynamicPricingAdjustments(BigDecimal basePrice, String route, String timeOfDay) {
        Map<String, BigDecimal> factors = new HashMap<>();
        BigDecimal totalAdjustment = BigDecimal.ONE;

        // Time of day adjustment
        BigDecimal timeMultiplier = getTimeOfDayMultiplier(timeOfDay);
        factors.put("time_of_day", timeMultiplier);
        totalAdjustment = totalAdjustment.multiply(timeMultiplier);

        // Route demand adjustment (simplified)
        BigDecimal routeMultiplier = getRouteDemandMultiplier(route);
        factors.put("route_demand", routeMultiplier);
        totalAdjustment = totalAdjustment.multiply(routeMultiplier);

        BigDecimal adjustedPrice = basePrice.multiply(totalAdjustment);
        String reasoning = buildPricingReasoning(factors);

        log.debug("Dynamic pricing: {} ETB → {} ETB (factor: {})", basePrice, adjustedPrice, totalAdjustment);

        return new DynamicPricingAdjustment(
            adjustedPrice,
            totalAdjustment,
            factors,
            reasoning
        );
    }

    // ── Private Helper Methods ───────────────────────────────────────────────

    private String normalizeCity(String city) {
        if (city == null) return "";
        return city.toLowerCase().replace(" ", "_").trim();
    }

    private BigDecimal getFuelEfficiencyForVehicle(String vehicleType) {
        // Vehicle-specific fuel efficiency (km per liter)
        return switch (vehicleType != null ? vehicleType.toLowerCase() : "standard") {
            case "light_truck" -> new BigDecimal("10.0");
            case "medium_truck" -> new BigDecimal("8.5"); 
            case "heavy_truck" -> new BigDecimal("6.5");
            case "trailer" -> new BigDecimal("5.0");
            default -> AVERAGE_FUEL_EFFICIENCY;
        };
    }

    private BigDecimal getTimeOfDayMultiplier(String timeOfDay) {
        return switch (timeOfDay != null ? timeOfDay.toLowerCase() : "standard") {
            case "peak_morning" -> new BigDecimal("1.15"); // 15% premium
            case "peak_evening" -> new BigDecimal("1.10"); // 10% premium
            case "night" -> new BigDecimal("1.20"); // 20% premium for night driving
            case "weekend" -> new BigDecimal("0.95"); // 5% discount
            default -> BigDecimal.ONE;
        };
    }

    private BigDecimal getRouteDemandMultiplier(String route) {
        // High demand routes get premium pricing
        if (route != null && route.toLowerCase().contains("addis")) {
            return new BigDecimal("1.05"); // 5% premium for Addis routes
        }
        return BigDecimal.ONE;
    }

    private String buildPricingReasoning(Map<String, BigDecimal> factors) {
        StringBuilder reasoning = new StringBuilder();
        
        factors.forEach((factor, value) -> {
            if (value.compareTo(BigDecimal.ONE) != 0) {
                double percentage = (value.doubleValue() - 1.0) * 100;
                reasoning.append(String.format("%s: %+.1f%%; ", factor, percentage));
            }
        });

        return reasoning.length() > 0 ? reasoning.toString() : "No adjustments applied";
    }
}