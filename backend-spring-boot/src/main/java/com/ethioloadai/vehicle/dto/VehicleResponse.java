package com.ethioloadai.vehicle.dto;

import com.ethioloadai.vehicle.entity.Vehicle;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VehicleResponse {
    private Long id;
    private Long userId;
    private Long fleetOwnerId;
    private String truckType;
    private VehicleCategory vehicleCategory;
    private String plateNumber;
    private Double capacity;
    private String currentCity;
    private BigDecimal latitude;
    private BigDecimal longitude;
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime lastLocationAt;
    private String availabilityStatus;
    private BigDecimal rating;
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime createdAt;
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime updatedAt;

    public enum VehicleCategory {
        HEAVY,
        LIGHT
    }
}
