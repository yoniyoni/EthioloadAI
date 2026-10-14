package com.ethioloadai.booking.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record BookingTripResponse(
        Long bookingId,
        Long freightId,
        String bookingStatus,
        Long tripId,
        String tripStatus,
        String tripType,
        String serviceType) {}
