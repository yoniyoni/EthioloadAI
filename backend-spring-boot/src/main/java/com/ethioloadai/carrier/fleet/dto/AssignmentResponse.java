package com.ethioloadai.carrier.fleet.dto;

import com.ethioloadai.carrier.fleet.entity.AssignmentStatus;
import com.ethioloadai.carrier.fleet.entity.VehicleDriverAssignment;
import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;

/** Public projection of a {@link VehicleDriverAssignment}. */
@Value @Builder
public class AssignmentResponse {

    Long id;
    Long carrierCompanyId;
    Long carrierDriverId;
    Long vehicleId;
    AssignmentStatus status;
    Long assignedBy;
    Long endedBy;
    LocalDateTime assignedAt;
    LocalDateTime endedAt;
    String notes;
    LocalDateTime createdAt;
    LocalDateTime updatedAt;

    public static AssignmentResponse from(VehicleDriverAssignment a) {
        return AssignmentResponse.builder()
                .id(a.getId())
                .carrierCompanyId(a.getCarrierCompanyId())
                .carrierDriverId(a.getCarrierDriverId())
                .vehicleId(a.getVehicleId())
                .status(a.getStatus())
                .assignedBy(a.getAssignedBy())
                .endedBy(a.getEndedBy())
                .assignedAt(a.getAssignedAt())
                .endedAt(a.getEndedAt())
                .notes(a.getNotes())
                .createdAt(a.getCreatedAt())
                .updatedAt(a.getUpdatedAt())
                .build();
    }
}
