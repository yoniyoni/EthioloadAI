package com.ethioloadai.carrier.fleet.dto;

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

    /** Existing driver account ID. Kept for mobile and older clients. */
    private Long driverUserId;

    /** Existing driver's registered email; preferred by the company workspace. */
    private String driverEmail;

    /** Registered email or phone, for drivers who created phone-only accounts. */
    private String driverIdentifier;

    /** Optional notes (e.g. license info, joining context). Max 1000 chars. */
    private String notes;
}
