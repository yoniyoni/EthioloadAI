package com.ethioloadai.trip.service;

import com.ethioloadai.trip.entity.Trip;
import com.ethioloadai.trip.entity.TripStop;
import com.ethioloadai.user.entity.User;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Service for advanced multi-stop business rules from Laravel reference implementation.
 * Handles pricing models, sequential completion, commission calculations, and route optimization.
 */
public interface MultiStopBusinessRulesService {

    /**
     * Calculate total amount for multi-stop trip.
     * Sums all stop agreed_price values.
     *
     * @param trip trip with stops
     * @return total amount across all stops
     */
    BigDecimal calculateTripTotalAmount(Trip trip);

    /**
     * Calculate commission for multi-stop trip.
     * Uses 10% commission rate on total trip amount.
     *
     * @param trip trip to calculate commission for
     * @return commission amount and breakdown
     */
    CommissionCalculation calculateMultiStopCommission(Trip trip);

    /**
     * Validate sequential completion workflow.
     * Ensures stops are completed in proper order.
     *
     * @param stop stop to validate
     * @param targetStatus status being transitioned to
     * @return validation result with violations
     */
    SequentialCompletionValidation validateSequentialCompletion(TripStop stop, TripStop.StopStatus targetStatus);

    /**
     * Auto-complete trip when all stops are finished.
     * Triggers automatic trip completion logic.
     *
     * @param trip trip to check for completion
     * @param user user performing operation
     * @return true if trip was auto-completed
     */
    boolean autoCompleteMultiStopTrip(Trip trip, User user);

    /**
     * Calculate pricing for a new stop using distance and material factors.
     *
     * @param fromLocation previous location
     * @param toLocation stop location
     * @param weight cargo weight in tons
     * @param material cargo material type
     * @param urgency urgency level
     * @return pricing estimate with breakdown
     */
    PricingEstimate calculateStopPricing(String fromLocation, String toLocation, 
                                        BigDecimal weight, String material, String urgency);

    /**
     * Validate stop addition business rules.
     *
     * @param trip trip to add stop to
     * @param stopOrder desired order
     * @param cargoRequestId cargo request (optional)
     * @param user requesting user
     * @return validation result
     */
    StopAdditionValidation validateStopAddition(Trip trip, Integer stopOrder, 
                                               Long cargoRequestId, User user);

    /**
     * Update trip type based on stop count.
     * Switches between SINGLE and MULTI_STOP based on total_stops.
     *
     * @param trip trip to update
     */
    void updateTripTypeForStopCount(Trip trip);

    /**
     * Calculate route optimization for multiple stops.
     *
     * @param trip trip with stops to optimize
     * @return optimized stop ordering
     */
    RouteOptimization optimizeStopRoute(Trip trip);

    /**
     * Get next available stop order for a trip.
     *
     * @param trip trip to get next order for
     * @return next stop order number
     */
    Integer getNextStopOrder(Trip trip);

    /**
     * Re-sequence stops after deletion.
     *
     * @param trip trip with stops to re-sequence
     * @param deletedOrder order of deleted stop
     */
    void resequenceStopsAfterDeletion(Trip trip, Integer deletedOrder);

    /**
     * Calculate distance between two locations.
     *
     * @param fromLocation starting location
     * @param toLocation destination location
     * @return distance in kilometers
     */
    BigDecimal calculateDistance(String fromLocation, String toLocation);

    /**
     * Validate cargo requests for multi-stop workflow.
     *
     * @param cargoRequestIds list of cargo request IDs
     * @param user requesting user
     * @return validation results
     */
    CargoValidationResult validateCargoRequests(List<Long> cargoRequestIds, User user);

    // ── Value Objects ────────────────────────────────────────────────────────

    record CommissionCalculation(
        BigDecimal totalAmount,
        BigDecimal commissionRate,
        BigDecimal commissionAmount,
        BigDecimal driverNetAmount,
        String currency
    ) {}

    record SequentialCompletionValidation(
        boolean isValid,
        List<String> violations,
        List<String> warnings,
        TripStop previousStop,
        TripStop.StopStatus requiredPreviousStatus
    ) {}

    record PricingEstimate(
        BigDecimal priceMin,
        BigDecimal priceMax,
        BigDecimal estimatedPrice,
        BigDecimal distanceKm,
        String currency,
        BigDecimal confidence,
        String source,
        Map<String, BigDecimal> multipliers
    ) {}

    record StopAdditionValidation(
        boolean canAdd,
        List<String> violations,
        List<String> warnings,
        Integer suggestedOrder,
        BigDecimal estimatedPrice
    ) {}

    record RouteOptimization(
        List<TripStop> optimizedStops,
        BigDecimal totalDistanceKm,
        BigDecimal estimatedDurationHours,
        BigDecimal fuelSavings,
        String optimizationMethod
    ) {}

    record CargoValidationResult(
        List<Long> validCargoIds,
        List<Long> invalidCargoIds,
        Map<Long, String> validationErrors,
        boolean allValid
    ) {}
}