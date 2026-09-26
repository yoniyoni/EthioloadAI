package com.ethioloadai.trip.exception;

import com.ethioloadai.exception.BaseException;

public class TripValidationException extends BaseException {

    public TripValidationException(String message) {
        super(message, "TRIP_VALIDATION_ERROR", 400);
    }

    public static TripValidationException bookingNotEligible(String reason) {
        return new TripValidationException("Booking is not eligible for trip creation: " + reason);
    }

    public static TripValidationException tripAlreadyExists(Long bookingId) {
        return new TripValidationException(
            String.format("Trip already exists for booking %d", bookingId)
        );
    }

    public static TripValidationException vehicleNotOwnedByDriver(Long vehicleId, Long driverId) {
        return new TripValidationException(
            String.format("Vehicle %d is not owned by driver %d", vehicleId, driverId)
        );
    }

    public static TripValidationException bookingNotFound(Long bookingId) {
        return new TripValidationException(
            String.format("Booking %d not found", bookingId)
        );
    }

    public static TripValidationException unauthorizedAccess(Long bookingId, Long userId) {
        return new TripValidationException(
            String.format("User %d is not authorized to create trip for booking %d", userId, bookingId)
        );
    }

    public static TripValidationException invalidBookingStatus(String currentStatus) {
        return new TripValidationException(
            String.format("Cannot create trip from booking with status: %s", currentStatus)
        );
    }

    public static TripValidationException maxConcurrentTripsExceeded(Long driverId, int maxTrips) {
        return new TripValidationException(
            String.format("Driver %d has reached maximum concurrent trips (%d)", driverId, maxTrips)
        );
    }

    public static TripValidationException vehicleInUse(Long vehicleId, Long conflictingBookingId) {
        return new TripValidationException(
            String.format("Vehicle %d is already in use by booking %d", vehicleId, conflictingBookingId)
        );
    }

    public static TripValidationException vehicleInOngoingTrip(Long vehicleId, Long conflictingTripId) {
        return new TripValidationException(
            String.format("Vehicle %d is already in use by ongoing trip %d", vehicleId, conflictingTripId)
        );
    }

    public static TripValidationException tripNotOngoing(Long tripId) {
        return new TripValidationException(String.format("Trip %d is not ongoing", tripId));
    }

    public static TripValidationException invalidGpsData(String reason) {
        return new TripValidationException("Invalid GPS data: " + reason);
    }
}