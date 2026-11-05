package com.ethioloadai.booking.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record CompanyBookingResponse(
        Long id,
        Long carrierCompanyId,
        Long freightId,
        String freightDescription,
        Long shipperId,
        String shipperName,
        Long driverId,
        String driverName,
        Long vehicleId,
        String vehiclePlateNumber,
        String vehicleType,
        Long tripId,
        String tripStatus,
        String bookingStatus,
        BigDecimal estimatedPrice,
        BigDecimal commissionFee,
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss") LocalDateTime createdAt,
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss") LocalDateTime updatedAt) {
}
