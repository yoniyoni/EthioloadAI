package com.ethioloadai.trip.exception;

import com.ethioloadai.exception.BaseException;

public class TripStopNotFoundException extends BaseException {

    public TripStopNotFoundException(String message) {
        super(message, "TRIP_STOP_NOT_FOUND", 404);
    }

    public static TripStopNotFoundException withId(Long stopId) {
        return new TripStopNotFoundException(String.format("TripStop not found with ID: %d", stopId));
    }

    public static TripStopNotFoundException withIdAndTrip(Long stopId, Long tripId) {
        return new TripStopNotFoundException(
            String.format("TripStop %d not found for trip %d", stopId, tripId));
    }

    public static TripStopNotFoundException withOrder(Integer stopOrder, Long tripId) {
        return new TripStopNotFoundException(
            String.format("TripStop with order %d not found for trip %d", stopOrder, tripId));
    }
}