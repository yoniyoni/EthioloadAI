package com.ethioloadai.trip.service;

import com.ethioloadai.trip.entity.Trip;
import com.ethioloadai.trip.entity.TripStop;
import com.ethioloadai.user.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Map;

public interface TripService {

    /**
     * Create a trip from an eligible booking.
     * Validates booking eligibility, driver authorization, and vehicle ownership.
     * Implements concurrency-safe trip creation (one booking → one trip).
     *
     * @param bookingId ID of the booking to create trip from
     * @param driver the authenticated driver (for authorization)
     * @return created trip
     */
    Trip createTripFromBooking(Long bookingId, User driver);

    /**
     * Get a trip by ID with authorization check.
     *
     * @param tripId trip ID
     * @param user requesting user (driver, shipper, or admin)
     * @return trip if authorized
     */
    Trip getTripWithAuthorization(Long tripId, User user);

    /**
     * Get trips for a specific driver with pagination.
     *
     * @param driverId driver ID
     * @param pageable pagination info
     * @return page of driver's trips
     */
    Page<Trip> getDriverTrips(Long driverId, Pageable pageable);

    /**
     * Update trip status with validation.
     *
     * @param tripId trip ID
     * @param newStatus new status to set
     * @param user requesting user (must be driver or admin)
     * @return updated trip
     */
    Trip updateTripStatus(Long tripId, Trip.TripStatus newStatus, User user);

    /**
     * Complete a trip and update related entities (booking, freight).
     * Calculates final commission and updates booking status.
     *
     * @param tripId trip ID
     * @param user requesting user (must be driver or admin)
     * @return completed trip
     */
    Trip completeTrip(Long tripId, User user);

    /**
     * Update trip location with GPS breadcrumb.
     * Implements growth boundary to prevent unbounded route_data growth.
     *
     * @param tripId trip ID
     * @param gpsData GPS coordinates and timestamp
     * @param user requesting user (must be driver)
     * @return updated trip
     */
    Trip updateLocation(Long tripId, Map<String, Object> gpsData, User user);

    /**
     * Update trip location with vehicle consistency validation.
     * Ensures the GPS update comes from the correct vehicle assigned to the trip.
     *
     * @param tripId trip ID
     * @param locationRequest validated location update request with vehicleId
     * @param user requesting user (must be driver)
     * @return updated trip
     */
    Trip updateLocationWithVehicleValidation(Long tripId, 
            com.ethioloadai.tracking.dto.LocationUpdateRequest locationRequest, User user);

    /**
     * Get trip location data for tracking.
     *
     * @param tripId trip ID
     * @param user requesting user (driver, shipper, or admin)
     * @return trip with route data
     */
    Trip getTripLocation(Long tripId, User user);

    /**
     * Check if trip status transition is valid.
     *
     * @param from current status
     * @param to target status
     * @return true if transition is allowed
     */
    boolean isValidStatusTransition(Trip.TripStatus from, Trip.TripStatus to);

    /**
     * Start a trip (if not already started).
     *
     * @param tripId trip ID
     * @param user requesting user (must be driver)
     * @return started trip
     */
    Trip startTrip(Long tripId, User user);

    /**
     * Phase 7 — Driver marks arrival at destination.
     *
     * @param tripId trip ID
     * @param user requesting user (must be assigned driver)
     * @return updated trip
     */
    Trip markArrived(Long tripId, User user);

    /**
     * Completes an arrived trip after confirmation by its freight's shipper.
     */
    Trip confirmDeliveryByShipper(Long tripId, User shipper);

    /**
     * Phase 7 — Internal: mark trip as DELIVERY_PENDING_VERIFICATION after proof submitted.
     *
     * @param tripId trip ID
     * @return updated trip
     */
    Trip markDeliveryPendingVerification(Long tripId);

    /**
     * Phase 7 — Internal: revert trip to ARRIVED after proof rejected.
     *
     * @param tripId trip ID
     * @return updated trip
     */
    Trip revertToArrived(Long tripId);

    /**
     * Phase 7 — Complete trip after shipper verifies delivery.
     * Idempotent.
     *
     * @param tripId trip ID
     * @return completed trip
     */
    Trip completeTripAfterVerification(Long tripId);

    /**
     * Get trip progress information.
     *
     * @param tripId trip ID
     * @param user requesting user
     * @return trip progress details
     */
    TripLifecycleService.TripProgress getTripProgress(Long tripId, User user);

    /**
     * Get all trips requiring attention (overdue, etc.).
     * Admin only method.
     *
     * @param user requesting user (must be admin)
     * @return list of trips requiring attention
     */
    List<Trip> getTripsRequiringAttention(User user);

    /**
     * Get comprehensive trip statistics.
     * Admin only method.
     *
     * @param user requesting user (must be admin)
     * @return statistics map
     */
    Map<String, Object> getTripStatistics(User user);

    /**
     * Get trip system health status.
     * Admin only method.
     *
     * @param user requesting user (must be admin)
     * @return system health status
     */
    TripMonitoringService.TripSystemHealth getTripSystemHealth(User user);

    /**
     * Execute a status transition with comprehensive validation.
     *
     * @param tripId trip ID
     * @param targetStatus target status
     * @param user requesting user
     * @return updated trip
     */
    Trip executeStatusTransition(Long tripId, Trip.TripStatus targetStatus, User user);

    /**
     * Get available status transitions for a trip.
     *
     * @param tripId trip ID
     * @param user requesting user
     * @return available transition options
     */
    List<TripStatusTransitionService.StatusOption> getAvailableTransitions(Long tripId, User user);

    /**
     * Validate if a status transition is possible.
     *
     * @param tripId trip ID
     * @param targetStatus target status
     * @param user requesting user
     * @return validation result
     */
    TripStatusTransitionService.TransitionValidationResult validateStatusTransition(
            Long tripId, Trip.TripStatus targetStatus, User user);

    /**
     * Process automatic status transitions (admin only).
     *
     * @param user requesting user (must be admin)
     * @return list of automatically transitioned trips
     */
    List<Trip> processAutomaticTransitions(User user);

    // ── Multi-Stop Management ────────────────────────────────────────────────

    /**
     * Add a stop to a trip.
     *
     * @param tripId trip ID
     * @param locationName stop location
     * @param stopOrder desired order (null for auto-assign)
     * @param pickupLat GPS latitude (optional)
     * @param pickupLng GPS longitude (optional)
     * @param agreedPrice stop price
     * @param cargoRequestId related cargo (optional)
     * @param notes additional notes (optional)
     * @param user requesting user
     * @return created stop
     */
    TripStop addTripStop(Long tripId, String locationName, Integer stopOrder,
                        java.math.BigDecimal pickupLat, java.math.BigDecimal pickupLng,
                        java.math.BigDecimal agreedPrice, Long cargoRequestId, 
                        String notes, User user);

    /**
     * Update a trip stop.
     *
     * @param stopId stop ID
     * @param locationName new location (optional)
     * @param pickupLat new latitude (optional)
     * @param pickupLng new longitude (optional)
     * @param agreedPrice new price (optional)
     * @param notes new notes (optional)
     * @param user requesting user
     * @return updated stop
     */
    TripStop updateTripStop(Long stopId, String locationName,
                           java.math.BigDecimal pickupLat, java.math.BigDecimal pickupLng,
                           java.math.BigDecimal agreedPrice, String notes, User user);

    /**
     * Remove a stop from a trip.
     *
     * @param stopId stop ID
     * @param user requesting user
     */
    void removeTripStop(Long stopId, User user);

    /**
     * Get all stops for a trip.
     *
     * @param tripId trip ID
     * @param user requesting user
     * @return ordered list of stops
     */
    List<TripStop> getTripStops(Long tripId, User user);

    /**
     * Update stop status with workflow validation.
     *
     * @param stopId stop ID
     * @param newStatus new status
     * @param user requesting user
     * @return updated stop
     */
    TripStop updateTripStopStatus(Long stopId, TripStop.StopStatus newStatus, User user);

    /**
     * Reorder stops in a trip.
     *
     * @param tripId trip ID
     * @param newOrdering list of stop IDs in new order
     * @param user requesting user
     * @return reordered stops
     */
    List<TripStop> reorderTripStops(Long tripId, List<Long> newOrdering, User user);

    // ── Route Data Management ────────────────────────────────────────────────

    /**
     * Get current trip location (most recent GPS point).
     *
     * @param tripId trip ID
     * @param user requesting user
     * @return current location or null if no GPS data
     */
    RouteDataService.GpsLocationData getCurrentTripLocation(Long tripId, User user);

    /**
     * Get route history with optional filtering.
     *
     * @param tripId trip ID
     * @param limit maximum points to return
     * @param user requesting user
     * @return route history
     */
    List<RouteDataService.GpsLocationData> getTripRouteHistory(Long tripId, Integer limit, User user);

    /**
     * Get comprehensive route analytics for trip.
     *
     * @param tripId trip ID
     * @param user requesting user
     * @return route analytics
     */
    RouteDataService.RouteAnalytics getTripRouteAnalytics(Long tripId, User user);

    /**
     * Archive old GPS data for trip to prevent unbounded growth.
     *
     * @param tripId trip ID
     * @param user requesting user (must be admin)
     * @return number of points archived
     */
    int archiveTripRouteData(Long tripId, User user);

    /**
     * Export trip route data in specified format.
     *
     * @param tripId trip ID
     * @param format export format (GPS, KML, JSON)
     * @param user requesting user
     * @return exported route data
     */
    String exportTripRouteData(Long tripId, String format, User user);

    // ── Enhanced Security Methods ────────────────────────────────────────────

    /**
     * Get trips with comprehensive security filtering.
     *
     * @param user requesting user
     * @param pageable pagination parameters
     * @return page of authorized trips
     */
    Page<Trip> getSecureTrips(User user, Pageable pageable);

    /**
     * Search trips with security filtering.
     *
     * @param user requesting user
     * @param searchCriteria search criteria
     * @param pageable pagination parameters
     * @return filtered search results
     */
    Page<Trip> searchSecureTrips(User user, TripSecurityService.TripSearchCriteria searchCriteria, Pageable pageable);

    /**
     * Validate trip access for specific operation.
     *
     * @param tripId trip ID
     * @param user requesting user
     * @param accessType required access type
     * @return access validation result
     */
    TripSecurityService.TripAccessValidation validateTripAccess(Long tripId, User user, TripSecurityService.AccessType accessType);

    /**
     * Get comprehensive trip analytics with security filtering.
     *
     * @param user requesting user
     * @param dateRange date range for analytics
     * @return authorized analytics data
     */
    TripSecurityService.TripAnalytics getSecureTripAnalytics(User user, TripSecurityService.DateRange dateRange);
}