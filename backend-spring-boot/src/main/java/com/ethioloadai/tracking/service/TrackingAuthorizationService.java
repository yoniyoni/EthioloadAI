package com.ethioloadai.tracking.service;

import com.ethioloadai.tracking.dto.LocationUpdateRequest;
import com.ethioloadai.trip.entity.Trip;
import com.ethioloadai.vehicle.entity.Vehicle;
import com.ethioloadai.user.entity.User;

/**
 * Authorization service for GPS tracking operations.
 * Implements comprehensive security validation for location updates.
 */
public interface TrackingAuthorizationService {

    /**
     * Validates complete authorization chain for location updates:
     * 1. User is authenticated  
     * 2. User is authorized to submit tracking data
     * 3. Vehicle exists and user has access
     * 4. Trip exists and is associated with vehicle/driver
     * 5. Trip is in trackable state
     * 
     * @param request location update request
     * @param user authenticated user
     * @return authorization result with vehicle and trip if valid
     * @throws TrackingAuthorizationException if any validation fails
     */
    TrackingAuthorizationResult validateLocationUpdateAuthorization(LocationUpdateRequest request, User user);

    /**
     * Validates user is authorized to submit GPS tracking data.
     * Only DRIVER, FLEET_OWNER, and ADMIN roles can submit location updates.
     * 
     * @param user authenticated user
     * @return true if user can submit tracking data
     */
    boolean canSubmitLocationUpdates(User user);

    /**
     * Validates user has access to the specified vehicle.
     * Rules:
     * - DRIVER: Must own vehicle OR be authorized for fleet vehicle
     * - FLEET_OWNER: Must own the fleet that owns the vehicle
     * - ADMIN: Can access any vehicle
     * 
     * @param vehicle target vehicle
     * @param user authenticated user
     * @return true if user has vehicle access
     */
    boolean hasVehicleAccess(Vehicle vehicle, User user);

    /**
     * Validates trip is associated with the vehicle and driver.
     * Prevents cross-vehicle/cross-driver location updates.
     * 
     * @param trip target trip
     * @param vehicle GPS transmitting vehicle
     * @param user authenticated user
     * @return true if trip-vehicle-driver relationship is valid
     */
    boolean isTripVehicleConsistent(Trip trip, Vehicle vehicle, User user);

    /**
     * Validates trip is in a trackable lifecycle state.
     * Only ONGOING trips can receive GPS updates.
     * 
     * @param trip target trip
     * @return true if trip can be tracked
     */
    boolean isTripTrackable(Trip trip);

    /**
     * Result of tracking authorization validation.
     */
    record TrackingAuthorizationResult(
        boolean isAuthorized,
        Vehicle vehicle,
        Trip trip,
        String reason,
        AuthorizationLevel level
    ) {
        public static TrackingAuthorizationResult authorized(Vehicle vehicle, Trip trip, AuthorizationLevel level) {
            return new TrackingAuthorizationResult(true, vehicle, trip, "Authorization granted", level);
        }
        
        public static TrackingAuthorizationResult denied(String reason) {
            return new TrackingAuthorizationResult(false, null, null, reason, null);
        }
    }

    /**
     * Authorization level achieved by user.
     */
    enum AuthorizationLevel {
        OWNER,          // User owns the vehicle
        FLEET_MEMBER,   // User is authorized fleet driver  
        FLEET_OWNER,    // User owns the fleet
        ADMIN           // Administrative access
    }
}