package com.ethioloadai.carrier.fleet.dto;

import com.ethioloadai.carrier.fleet.entity.DriverAvailability;
import lombok.Getter;
import lombok.Setter;

/**
 * Writable fields for a carrier driver record.
 * All fields are optional (PATCH semantics).
 */
@Getter @Setter
public class UpdateCarrierDriverRequest {
    /** Override availability manually (e.g. mark UNAVAILABLE). */
    private DriverAvailability availability;
    /** Free-text notes about this driver in the company context. */
    private String notes;
}
