package com.ethioloadai.carrier.fleet.entity;

/**
 * Lifecycle status of a driver within a carrier company.
 *
 * <p>ACTIVE   — current driver; can be assigned to vehicles and jobs.
 * INACTIVE — temporarily suspended; cannot receive new assignments.
 * REMOVED  — left or was removed; row is kept for trip-history traceability.
 *            The driver's {@link com.ethioloadai.user.entity.User} account is never deleted.
 */
public enum CarrierDriverStatus {
    ACTIVE,
    INACTIVE,
    REMOVED
}
