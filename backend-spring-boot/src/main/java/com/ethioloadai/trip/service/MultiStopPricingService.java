package com.ethioloadai.trip.service;

import com.ethioloadai.trip.entity.Trip;
import com.ethioloadai.trip.entity.TripStop;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Advanced pricing service for multi-stop trips.
 * Implements sophisticated pricing models from Laravel reference.
 */
public interface MultiStopPricingService {

    /**
     * Calculate comprehensive pricing for a multi-stop route.
     *
     * @param stops ordered list of stops
     * @param baseWeight cargo weight in tons
     * @param material cargo material type
     * @param urgency urgency level
     * @return detailed pricing breakdown
     */
    MultiStopPricingResult calculateMultiStopPricing(List<TripStop> stops, BigDecimal baseWeight, 
                                                    String material, String urgency);

    /**
     * Estimate empty return probability and cost impact.
     *
     * @param finalDestination final destination city
     * @return empty return analysis
     */
    EmptyReturnAnalysis analyzeEmptyReturnRisk(String finalDestination);

    /**
     * Calculate distance-based pricing between two locations.
     *
     * @param fromLocation starting location
     * @param toLocation destination location
     * @param weight cargo weight
     * @param material material type
     * @param urgency urgency level
     * @return pricing estimate
     */
    PricingEstimate calculateDistanceBasedPricing(String fromLocation, String toLocation,
                                                 BigDecimal weight, String material, String urgency);

    /**
     * Optimize pricing across multiple stops for maximum efficiency.
     *
     * @param trip trip with stops to optimize pricing for
     * @return pricing optimization results
     */
    PricingOptimization optimizeMultiStopPricing(Trip trip);

    /**
     * Calculate fuel cost estimates for multi-stop route.
     *
     * @param stops ordered stops
     * @param vehicleType vehicle type/capacity
     * @return fuel cost analysis
     */
    FuelCostAnalysis calculateFuelCosts(List<TripStop> stops, String vehicleType);

    /**
     * Get dynamic pricing adjustments based on market conditions.
     *
     * @param basePrice base calculated price
     * @param route route information
     * @param timeOfDay time of day factor
     * @return adjusted price with factors
     */
    DynamicPricingAdjustment getDynamicPricingAdjustments(BigDecimal basePrice, String route, String timeOfDay);

    // ── Value Objects ────────────────────────────────────────────────────────

    record MultiStopPricingResult(
        BigDecimal totalBasePrice,
        BigDecimal totalDistance,
        BigDecimal estimatedDuration,
        List<StopPricingDetail> stopBreakdown,
        Map<String, BigDecimal> multipliers,
        BigDecimal recommendedTotalPrice,
        String currency,
        BigDecimal confidence
    ) {}

    record StopPricingDetail(
        Integer stopOrder,
        String fromLocation,
        String toLocation,
        BigDecimal distanceKm,
        BigDecimal basePrice,
        BigDecimal agreedPrice,
        Map<String, BigDecimal> appliedMultipliers
    ) {}

    record EmptyReturnAnalysis(
        String destination,
        BigDecimal emptyReturnProbability,
        String riskLevel,
        String recommendation,
        BigDecimal suggestedMarkup,
        BigDecimal estimatedReturnCost
    ) {}

    record PricingEstimate(
        BigDecimal priceMin,
        BigDecimal priceMax,
        BigDecimal estimatedPrice,
        BigDecimal distanceKm,
        String currency,
        BigDecimal confidence,
        String source,
        Map<String, Object> metadata
    ) {}

    record PricingOptimization(
        BigDecimal originalTotal,
        BigDecimal optimizedTotal,
        BigDecimal potentialSavings,
        List<OptimizationSuggestion> suggestions,
        String strategy
    ) {}

    record OptimizationSuggestion(
        String type,
        String description,
        BigDecimal impact,
        String reasoning
    ) {}

    record FuelCostAnalysis(
        BigDecimal totalDistanceKm,
        BigDecimal estimatedFuelLiters,
        BigDecimal fuelCostETB,
        BigDecimal fuelEfficiencyKmPerLiter,
        Map<String, BigDecimal> costBreakdown
    ) {}

    record DynamicPricingAdjustment(
        BigDecimal adjustedPrice,
        BigDecimal adjustmentFactor,
        Map<String, BigDecimal> factors,
        String reasoning
    ) {}
}