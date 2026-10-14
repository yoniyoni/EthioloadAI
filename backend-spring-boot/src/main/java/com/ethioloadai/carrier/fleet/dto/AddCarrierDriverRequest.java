package com.ethioloadai.carrier.fleet.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * Request body for adding an existing {@link com.ethioloadai.user.entity.User}
 * (role=DRIVER) to a carrier company fleet.
 *
 * <p>The {@code carrierCompanyId} is intentionally absent — it comes from the
 * URL path variable and the server-side {@link com.ethioloadai.carrier.team.service.CompanyContext}.
 * This prevents mass-assignment attacks that would move a driver to a different company.
 */
@Getter @Setter
public class AddCarrierDriverRequest {

    /** ID of the User with role=DRIVER to associate with this company. */
    @NotNull(message = "driverUserId is required")
    private Long driverUserId;

    /** Optional notes (e.g. license info, joining context). Max 1000 chars. */
    private String notes;
}
