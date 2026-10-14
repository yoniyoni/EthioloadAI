package com.ethioloadai.carrier.fleet.dto;

import com.ethioloadai.carrier.fleet.entity.VehicleAvailability;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** PATCH body for a company vehicle. All fields optional. */
@Getter @Setter
public class UpdateCompanyVehicleRequest {
    @Size(max = 255) private String truckType;
    @DecimalMin(value = "0.1") private Double capacity;
    /** HEAVY or LIGHT. */
    private String vehicleCategory;
    @Size(max = 255) private String currentCity;
    /** Manually override availability (e.g. MAINTENANCE). */
    private VehicleAvailability availability;
}
