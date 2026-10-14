package com.ethioloadai.fleet.dto;

import com.ethioloadai.vehicle.entity.Vehicle;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FleetVehicleResponse {
    private Long id;
    private String plateNumber;
    private String truckType;
    private String vehicleCategory;
    private Double capacity;
    private String currentCity;
    private String availabilityStatus;
    private Long assignedDriverId;

    public static FleetVehicleResponse from(Vehicle vehicle) {
        return FleetVehicleResponse.builder()
                .id(vehicle.getId())
                .plateNumber(vehicle.getPlateNumber())
                .truckType(vehicle.getTruckType())
                .vehicleCategory(vehicle.getVehicleCategory() != null ? 
                    vehicle.getVehicleCategory().name().toLowerCase() : null)
                .capacity(vehicle.getCapacity())
                .currentCity(vehicle.getCurrentCity())
                .availabilityStatus(vehicle.getAvailabilityStatus())
                .assignedDriverId(vehicle.getUserId())
                .build();
    }
}
