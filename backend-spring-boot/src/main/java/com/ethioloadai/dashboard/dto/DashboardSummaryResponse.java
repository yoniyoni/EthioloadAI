package com.ethioloadai.dashboard.dto;

import com.ethioloadai.freight.dto.FreightResponse;

import java.util.List;

public record DashboardSummaryResponse(
        long totalFreightPosts,
        long operationalShipments,
        long completedFreight,
        long cancelledFreight,
        long activeBookings,
        long activeTrips,
        List<FreightResponse> recentFreight,
        List<FreightResponse> recentOperationalFreight,
        List<BookingActivity> recentBookings,
        List<TripActivity> recentTrips,
        long totalBookings,
        long pendingBookings) {

    public record BookingActivity(Long id, String bookingStatus) {
    }

    public record TripActivity(Long id, String tripStatus) {
    }
}
