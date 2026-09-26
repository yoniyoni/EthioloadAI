package com.ethioloadai.trip.exception;

import com.ethioloadai.exception.BaseException;

public class TripStopValidationException extends BaseException {

    public TripStopValidationException(String message) {
        super(message, "TRIP_STOP_VALIDATION_ERROR", 400);
    }

    public static TripStopValidationException invalidStopOrder(Integer stopOrder) {
        return new TripStopValidationException(
            String.format("Invalid stop order: %d. Must be positive integer", stopOrder));
    }

    public static TripStopValidationException duplicateStopOrder(Integer stopOrder, Long tripId) {
        return new TripStopValidationException(
            String.format("Stop order %d already exists for trip %d", stopOrder, tripId));
    }

    public static TripStopValidationException cannotModifyCompleted() {
        return new TripStopValidationException("Cannot modify completed trip stops");
    }

    public static TripStopValidationException invalidSequence(String reason) {
        return new TripStopValidationException("Invalid stop sequence: " + reason);
    }

    public static TripStopValidationException priceValidation(String reason) {
        return new TripStopValidationException("Stop price validation failed: " + reason);
    }

    public static TripStopValidationException locationRequired() {
        return new TripStopValidationException("Stop location name is required");
    }

    public static TripStopValidationException coordinatesInvalid(String reason) {
        return new TripStopValidationException("Invalid GPS coordinates: " + reason);
    }

    public static TripStopValidationException cargoRequestConflict(Long cargoId, String reason) {
        return new TripStopValidationException(
            String.format("Cargo request %d conflict: %s", cargoId, reason));
    }
}