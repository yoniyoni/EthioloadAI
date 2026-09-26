package com.ethioloadai.carrier.dispatch.entity;

/**
 * Phase 6 — Lifecycle status for a {@link DispatchAssignment}.
 *
 * <p>Valid transitions:
 * <pre>
 *   ASSIGNED → DRIVER_ACCEPTED   (driver accepts)
 *   ASSIGNED → DRIVER_REJECTED   (driver rejects)
 *   ASSIGNED → CANCELLED         (carrier operator cancels before driver responds)
 *   ASSIGNED → SUPERSEDED        (carrier creates a new dispatch, superseding this one)
 * </pre>
 *
 * <p>Terminal states (no further transitions):
 * DRIVER_ACCEPTED, DRIVER_REJECTED, CANCELLED, SUPERSEDED
 */
public enum DispatchStatus {

    /** Assignment created; driver has been notified and has not yet responded. */
    ASSIGNED,

    /** Driver accepted the assignment; Booking has been created. */
    DRIVER_ACCEPTED,

    /** Driver rejected the assignment; resources released; carrier may redispatch. */
    DRIVER_REJECTED,

    /** Cancelled by an authorised carrier operator before the driver responded. */
    CANCELLED,

    /** This assignment was replaced by a newer one (e.g. redispatch after rejection). */
    SUPERSEDED;

    /** True when no further state transitions are possible. */
    public boolean isTerminal() {
        return this == DRIVER_ACCEPTED || this == DRIVER_REJECTED
                || this == CANCELLED || this == SUPERSEDED;
    }

    /** True while the assignment is still pending a driver response. */
    public boolean isPending() {
        return this == ASSIGNED;
    }
}
