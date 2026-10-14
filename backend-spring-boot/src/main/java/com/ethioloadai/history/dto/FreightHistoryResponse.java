package com.ethioloadai.history.dto;

import com.ethioloadai.freight.dto.FreightResponse;

import java.time.LocalDateTime;

public record FreightHistoryResponse(
        FreightResponse freight,
        BookingSummary booking,
        TripSummary trip,
        PaymentSummary payment) {

    public record BookingSummary(
            Long id,
            String bookingStatus,
            String driverName,
            LocalDateTime updatedAt) {}

    public record TripSummary(
            Long id,
            String tripStatus,
            LocalDateTime updatedAt,
            DriverSummary driver) {}

    public record DriverSummary(String fullName) {}

    public record PaymentSummary(
            Long id,
            String paymentStatus,
            LocalDateTime updatedAt) {}
}
