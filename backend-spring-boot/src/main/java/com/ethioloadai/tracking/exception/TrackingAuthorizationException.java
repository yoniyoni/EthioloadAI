package com.ethioloadai.tracking.exception;

import com.ethioloadai.exception.BaseException;
import org.springframework.http.HttpStatus;

/**
 * Exception thrown when GPS tracking authorization fails.
 * Used to prevent unauthorized location updates and IDOR attacks.
 */
public class TrackingAuthorizationException extends BaseException {

    public TrackingAuthorizationException(String message) {
        super(message, "TRACKING_AUTHORIZATION_FAILED", 403);
    }

    public TrackingAuthorizationException(String message, Throwable cause) {
        super(message, cause, "TRACKING_AUTHORIZATION_FAILED", 403);
    }

    // Specific authorization failure scenarios

    public static TrackingAuthorizationException userCannotSubmitTracking(Long userId, String userRole) {
        return new TrackingAuthorizationException(
            String.format("User %d with role %s is not authorized to submit GPS tracking data", userId, userRole)
        );
    }

    public static TrackingAuthorizationException vehicleNotFound(Long vehicleId) {
        return new TrackingAuthorizationException(
            String.format("Vehicle %d not found or does not exist", vehicleId)
        );
    }

    public static TrackingAuthorizationException unauthorizedVehicleAccess(Long vehicleId, Long userId) {
        return new TrackingAuthorizationException(
            String.format("User %d does not have access to vehicle %d", userId, vehicleId)
        );
    }

    public static TrackingAuthorizationException tripNotFound(Long tripId) {
        return new TrackingAuthorizationException(
            String.format("Trip %d not found or does not exist", tripId)
        );
    }

    public static TrackingAuthorizationException tripVehicleMismatch(Long tripId, Long requestedVehicleId, Long tripVehicleId) {
        return new TrackingAuthorizationException(
            String.format("Trip %d uses vehicle %d, cannot update location for vehicle %d", 
                         tripId, tripVehicleId, requestedVehicleId)
        );
    }

    public static TrackingAuthorizationException tripNotTrackable(Long tripId, String tripStatus) {
        return new TrackingAuthorizationException(
            String.format("Trip %d with status %s cannot receive GPS updates (must be ONGOING)", tripId, tripStatus)
        );
    }

    public static TrackingAuthorizationException unauthorizedFleetVehicle(Long vehicleId, Long userId, Long userFleetId, Long vehicleFleetId) {
        return new TrackingAuthorizationException(
            String.format("User %d (fleet %d) cannot access vehicle %d (fleet %d)", 
                         userId, userFleetId, vehicleId, vehicleFleetId)
        );
    }

    public static TrackingAuthorizationException unauthorizedTripAccess(Long tripId, Long userId) {
        return new TrackingAuthorizationException(
            String.format("User %d is not authorized to update GPS for trip %d", userId, tripId)
        );
    }
}