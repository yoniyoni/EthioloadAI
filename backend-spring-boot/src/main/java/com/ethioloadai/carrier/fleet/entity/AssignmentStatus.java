package com.ethioloadai.carrier.fleet.entity;

/**
 * Status of a {@link VehicleDriverAssignment}.
 *
 * <p>ACTIVE    — current, live assignment.
 * ENDED     — terminated normally (unassign or reassign).
 * CANCELLED — cancelled before the assignment became operational.
 */
public enum AssignmentStatus {
    ACTIVE,
    ENDED,
    CANCELLED
}
