package com.ethioloadai.carrier.fleet.entity;

/**
 * Operational availability of a company vehicle.
 *
 * <p>AVAILABLE  — ready to be assigned to a driver.
 * ASSIGNED   — has an ACTIVE {@link VehicleDriverAssignment}; cannot receive another.
 * ON_TRIP    — currently on an active trip; system-managed.
 * MAINTENANCE — undergoing maintenance; cannot be assigned.
 * UNAVAILABLE — manually marked unavailable.
 */
public enum VehicleAvailability {
    AVAILABLE,
    ASSIGNED,
    ON_TRIP,
    MAINTENANCE,
    UNAVAILABLE
}
