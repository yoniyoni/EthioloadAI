package com.ethioloadai.carrier.team.dto;

import com.ethioloadai.user.entity.User;
import com.ethioloadai.vehicle.entity.Vehicle;
import lombok.Builder;
import lombok.Value;

/**
 * Phase 3 company-scoped fleet response DTOs.
 *
 * <p>Deliberately minimal: only fields safe to expose to any active company member.
 * Phone numbers and sensitive personal fields are intentionally excluded from
 * non-admin endpoints. Phase 4 will expand these DTOs as fleet management matures.
 */
public final class CompanyFleetResponse {

    private CompanyFleetResponse() {}

    @Value
    @Builder
    public static class DriverSummary {
        Long id;
        String fullName;
        Boolean isActive;
        Boolean verificationStatus;
        Long fleetOwnerId;

        public static DriverSummary from(User user) {
            return DriverSummary.builder()
                    .id(user.getId())
                    .fullName(user.getFullName())
                    .isActive(user.getIsActive())
                    .verificationStatus(user.getVerificationStatus())
                    .fleetOwnerId(user.getFleetOwnerId())
                    .build();
        }
    }

    @Value
    @Builder
    public static class VehicleSummary {
        Long id;
        String plateNumber;
        String truckType;
        Double capacity;
        String currentCity;
        String availabilityStatus;
        String approvalStatus;
        Long fleetOwnerId;
        Long assignedDriverId;

        public static VehicleSummary from(Vehicle v) {
            return VehicleSummary.builder()
                    .id(v.getId())
                    .plateNumber(v.getPlateNumber())
                    .truckType(v.getTruckType())
                    .capacity(v.getCapacity())
                    .currentCity(v.getCurrentCity())
                    .availabilityStatus(v.getAvailabilityStatus())
                    .approvalStatus(v.getApprovalStatus() != null ? v.getApprovalStatus().name() : null)
                    .fleetOwnerId(v.getFleetOwnerId())
                    .assignedDriverId(v.getUserId())
                    .build();
        }
    }
}
