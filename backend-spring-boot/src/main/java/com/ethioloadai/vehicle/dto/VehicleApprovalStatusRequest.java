package com.ethioloadai.vehicle.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class VehicleApprovalStatusRequest {
    @NotNull(message = "Approval status is required")
    private VehicleResponse.VehicleApprovalStatus status;
}