package com.ethioloadai.trip.service;

import com.ethioloadai.trip.entity.TripStop;
import com.ethioloadai.user.entity.User;

import java.math.BigDecimal;
import java.util.List;

/**
 * Service for managing TripStop operations in multi-stop trips.
 * Handles CRUD operations, ordering logic, and business rule validation.
 */
public interface TripStopService {

    /**
     * Add a new stop to a trip.
     * Validates ordering, coordinates, and business rules.
     *
     * @param tripId trip to add stop to
     * @param locationName stop location name
     * @param stopOrder desired stop order (null for auto-assign)
     * @param pickupLat GPS latitude (optional)
     * @param pickupLng GPS longitude (optional)
     * @param agreedPrice stop price
     * @param cargoRequestId related cargo request (optional)
     * @param notes additional notes (optional)
     * @param user requesting user
     * @return created stop
     */
    TripStop addStop(Long tripId, String locationName, Integer stopOrder,
                     BigDecimal pickupLat, BigDecimal pickupLng, BigDecimal agreedPrice,
                     Long cargoRequestId, String notes, User user);

    /**
     * Update an existing trip stop.
     * Validates permissions and business rules.
     *
     * @param stopId stop to update
     * @param locationName new location name (optional)
     * @param pickupLat new GPS latitude (optional)
     * @param pickupLng new longitude (optional)
     * @param agreedPrice new price (optional)
     * @param notes new notes (optional)
     * @param user requesting user
     * @return updated stop
     */
    TripStop updateStop(Long stopId, String locationName,
                       BigDecimal pickupLat, BigDecimal pickupLng,
                       BigDecimal agreedPrice, String notes, User user);

    /**
     * Update stop status (PENDING → ARRIVED → LOADED → COMPLETED).
     * Enforces sequential completion logic.
     *
     * @param stopId stop to update
     * @param newStatus new status
     * @param user requesting user
     * @return updated stop
     */
    TripStop updateStopStatus(Long stopId, TripStop.StopStatus newStatus, User user);

    /**
     * Remove a stop from a trip.
     * Reorders remaining stops and validates removal eligibility.
     *
     * @param stopId stop to remove
     * @param user requesting user
     */
    void removeStop(Long stopId, User user);

    /**
     * Reorder stops in a trip.
     * Updates stop_order for multiple stops atomically.
     *
     * @param tripId trip to reorder
     * @param newOrdering list of stop IDs in new order
     * @param user requesting user
     * @return reordered stops
     */
    List<TripStop> reorderStops(Long tripId, List<Long> newOrdering, User user);

    /**
     * Get all stops for a trip in order.
     *
     * @param tripId trip ID
     * @param user requesting user
     * @return ordered list of stops
     */
    List<TripStop> getTripStops(Long tripId, User user);

    /**
     * Get a specific stop with authorization check.
     *
     * @param stopId stop ID
     * @param user requesting user
     * @return trip stop
     */
    TripStop getStopWithAuthorization(Long stopId, User user);

    /**
     * Mark stop as arrived.
     * Sets status to ARRIVED and records timestamp.
     *
     * @param stopId stop ID
     * @param user requesting user (must be driver)
     * @return updated stop
     */
    TripStop arriveAtStop(Long stopId, User user);

    /**
     * Mark cargo as loaded at stop.
     * Sets status to LOADED.
     *
     * @param stopId stop ID
     * @param user requesting user (must be driver)
     * @return updated stop
     */
    TripStop loadAtStop(Long stopId, User user);

    /**
     * Complete a stop.
     * Sets status to COMPLETED and records timestamp.
     *
     * @param stopId stop ID
     * @param user requesting user (must be driver)
     * @return updated stop
     */
    TripStop completeStop(Long stopId, User user);

    /**
     * Get next stop order number for a trip.
     *
     * @param tripId trip ID
     * @return next available stop order
     */
    Integer getNextStopOrder(Long tripId);

    /**
     * Validate stop can be added to trip.
     *
     * @param tripId trip ID
     * @param stopOrder desired order
     * @param user requesting user
     */
    void validateStopAddition(Long tripId, Integer stopOrder, User user);

    /**
     * Calculate total amount for all stops in a trip.
     *
     * @param tripId trip ID
     * @return total agreed price sum
     */
    BigDecimal calculateTotalStopAmount(Long tripId);

    /**
     * Get stops requiring attention (stuck, overdue, etc.).
     *
     * @param tripId trip ID (null for all trips)
     * @return stops requiring attention
     */
    List<TripStop> getStopsRequiringAttention(Long tripId);
}