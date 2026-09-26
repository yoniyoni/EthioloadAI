package com.ethioloadai.trip.exception;

import com.ethioloadai.exception.BaseException;

public class TripNotFoundException extends BaseException {

    public TripNotFoundException(String message) {
        super(message, "TRIP_NOT_FOUND", 404);
    }

    public static TripNotFoundException withId(Long tripId) {
        return new TripNotFoundException(String.format("Trip not found with ID: %d", tripId));
    }

    public static TripNotFoundException forBooking(Long bookingId) {
        return new TripNotFoundException(String.format("No trip found for booking: %d", bookingId));
    }
}