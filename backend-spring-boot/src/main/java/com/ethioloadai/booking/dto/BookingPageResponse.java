package com.ethioloadai.booking.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record BookingPageResponse(
        List<BookingResponse> content,
        int number,
        int size,
        boolean hasNext,
        Long totalBookings) {}
