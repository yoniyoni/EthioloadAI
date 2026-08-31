package com.ethioloadai.vehicle.dto;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VehicleUpdateRequest {
    private String truckType;

    private VehicleCategory vehicleCategory;

    @Size(max = 255, message = "Plate number must not exceed 255 characters")
    private String plateNumber;

    @Positive(message = "Capacity must be positive")
    private Double capacity;

    @Size(max = 255, message = "Current city must not exceed 255 characters")
    private String currentCity;

    @DecimalMin(value = "-90.0", message = "Latitude must be between -90 and 90")
    @DecimalMax(value = "90.0", message = "Latitude must be between -90 and 90")
    private BigDecimal latitude;

    @DecimalMin(value = "-180.0", message = "Longitude must be between -180 and 180")
    @DecimalMax(value = "180.0", message = "Longitude must be between -180 and 180")
    private BigDecimal longitude;

    private String availabilityStatus;

    public enum VehicleCategory {
        HEAVY,
        LIGHT
    }
}
