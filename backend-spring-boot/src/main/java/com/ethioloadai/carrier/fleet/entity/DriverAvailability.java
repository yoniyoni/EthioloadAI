package com.ethioloadai.carrier.fleet.entity;

/**
 * Operational availability of a carrier driver.
 *
 * <p>AVAILABLE  — ready to accept an assignment or job.
 * ASSIGNED   — has an ACTIVE {@link VehicleDriverAssignment}; cannot receive another.
 * ON_TRIP    — currently on an active trip; availability is system-managed.
 * UNAVAILABLE — manually marked unavailable (e.g. sick leave, off-duty).
 */
public enum DriverAvailability {
    AVAILABLE,
    ASSIGNED,
    ON_TRIP,
    UNAVAILABLE
}
