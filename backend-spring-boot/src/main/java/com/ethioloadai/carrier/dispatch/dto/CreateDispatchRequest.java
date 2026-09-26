package com.ethioloadai.carrier.dispatch.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Phase 6 — Request body for creating a dispatch assignment.
 *
 * <p>The client supplies ONLY the driver and vehicle IDs plus an optional note.
 * All other fields (carrierCompanyId, freightId, carrierOfferId, agreedAmount,
 * assignedBy) are derived server-side and NEVER accepted from this DTO.
 *
 * <p>Sending extra fields (e.g. agreedAmount, status) is harmless — they are
 * simply not bound to this DTO class.
 */
@Getter
@Setter
@NoArgsConstructor
public class CreateDispatchRequest {

    /**
     * User.id of the driver to be dispatched.
     * Must belong to the winning carrier company.
     */
    @NotNull(message = "driverUserId is required")
    private Long driverUserId;

    /**
     * Vehicle.id of the vehicle to be assigned.
     * Must belong to the winning carrier company.
     */
    @NotNull(message = "vehicleId is required")
    private Long vehicleId;

    /** Optional dispatcher notes (route instructions, special requirements). */
    private String notes;
}
