package com.ethioloadai.trip.exception;

import com.ethioloadai.exception.BaseException;
import com.ethioloadai.trip.entity.Trip;

public class TripStatusException extends BaseException {

    public TripStatusException(String message) {
        super(message, "TRIP_STATUS_ERROR", 400);
    }

    public static TripStatusException invalidTransition(Trip.TripStatus from, Trip.TripStatus to) {
        return new TripStatusException(
            String.format("Invalid trip status transition from %s to %s", from, to)
        );
    }

    public static TripStatusException cannotModifyCompleted() {
        return new TripStatusException("Cannot modify a completed trip");
    }

    public static TripStatusException tripNotOngoing(Long tripId) {
        return new TripStatusException(
            String.format("Trip %d is not in ongoing status", tripId)
        );
    }
}