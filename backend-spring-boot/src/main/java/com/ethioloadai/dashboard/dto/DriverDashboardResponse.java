package com.ethioloadai.dashboard.dto;

import com.ethioloadai.booking.dto.BookingResponse;

import java.util.List;

public record DriverDashboardResponse(
        long totalBookings,
        long confirmedBookings,
        long completedBookings,
        long pendingBookings,
        List<BookingResponse> pendingBookingRows) {}
