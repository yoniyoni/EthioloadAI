package com.ethioloadai.carrier.fleet.dto;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

/**
 * Request body for registering a new vehicle under a carrier company.
 *
 * <p>The {@code carrierCompanyId} is server-assigned from the URL / CompanyContext.
 * Clients must not send it to avoid mass-assignment.
 */
@Getter @Setter
public class RegisterCompanyVehicleRequest {

    @NotBlank(message = "truckType is required")
    @Size(max = 255)
    private String truckType;

    @NotBlank(message = "plateNumber is required")
    @Size(max = 255)
    private String plateNumber;

    @NotNull(message = "capacity is required")
    @DecimalMin(value = "0.1", message = "capacity must be > 0")
    private Double capacity;

    /** HEAVY or LIGHT — case-insensitive. */
    private String vehicleCategory;

    @Size(max = 255)
    private String currentCity;
}
