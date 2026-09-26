package com.ethioloadai.carrier.fleet.dto;

import com.ethioloadai.vehicle.entity.Vehicle;
import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;

/** Public projection of a company vehicle. */
@Value @Builder
public class CompanyVehicleResponse {

    Long id;
    Long carrierCompanyId;
    String plateNumber;
    String truckType;
    String vehicleCategory;
    Double capacity;
    String currentCity;
    String availabilityStatus;   // raw availability string (will be phase4 availability in Phase 5)
    String approvalStatus;       // VehicleApprovalStatus name
    Long userId;                 // currently assigned driver user id (legacy field)
    Long fleetOwnerId;
    LocalDateTime createdAt;
    LocalDateTime updatedAt;

    public static CompanyVehicleResponse from(Vehicle v) {
        return CompanyVehicleResponse.builder()
                .id(v.getId())
                .carrierCompanyId(v.getCarrierCompanyId())
                .plateNumber(v.getPlateNumber())
                .truckType(v.getTruckType())
                .vehicleCategory(v.getVehicleCategory() != null ? v.getVehicleCategory().name() : null)
                .capacity(v.getCapacity())
                .currentCity(v.getCurrentCity())
                .availabilityStatus(v.getAvailabilityStatus())
                .approvalStatus(v.getApprovalStatus() != null ? v.getApprovalStatus().name() : null)
                .userId(v.getUserId())
                .fleetOwnerId(v.getFleetOwnerId())
                .createdAt(v.getCreatedAt())
                .updatedAt(v.getUpdatedAt())
                .build();
    }
}
