package com.ethioloadai.carrier.fleet.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * Request body for creating a driver ↔ vehicle assignment.
 *
 * <p>The {@code carrierCompanyId} is server-assigned from the URL / CompanyContext.
 * Both driver and vehicle must belong to the same company — the service validates this.
 */
@Getter @Setter
public class CreateAssignmentRequest {

    @NotNull(message = "carrierDriverId is required")
    private Long carrierDriverId;   // carrier_drivers.id

    @NotNull(message = "vehicleId is required")
    private Long vehicleId;         // vehicles.id

    /** Optional context notes for this assignment. */
    private String notes;
}
