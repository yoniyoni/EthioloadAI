package com.ethioloadai.fleet.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AddVehicleRequest {
    @NotBlank(message = "Truck type is required")
    private String truckType;

    @NotBlank(message = "Plate number is required")
    private String plateNumber;

    @NotNull(message = "Capacity is required")
    @Positive(message = "Capacity must be positive")
    private Double capacity;

    @NotBlank(message = "Current city is required")
    private String currentCity;

    private String vehicleCategory; // "heavy" or "light"

    private Long driverId; // Optional: assign to fleet driver immediately
}
