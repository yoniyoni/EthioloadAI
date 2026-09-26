package com.ethioloadai.trip.service;

import com.ethioloadai.trip.entity.Trip;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

/**
 * Trip State Machine for managing valid status transitions and business rules.
 * Centralizes trip lifecycle logic and ensures consistency across the system.
 */
@Component
public class TripStateMachine {

    private final Map<Trip.TripStatus, Set<Trip.TripStatus>> validTransitions;

    public TripStateMachine() {
        this.validTransitions = new EnumMap<>(Trip.TripStatus.class);
        initializeValidTransitions();
    }

    /**
     * Check if a status transition is valid according to business rules.
     *
     * @param from current status
     * @param to target status
     * @return true if transition is allowed
     */
    public boolean isValidTransition(Trip.TripStatus from, Trip.TripStatus to) {
        if (from == null || to == null) {
            return false;
        }

        if (from == to) {
            return true; // Same status is always valid
        }

        Set<Trip.TripStatus> allowedTransitions = validTransitions.get(from);
        return allowedTransitions != null && allowedTransitions.contains(to);
    }

    /**
     * Get all valid next statuses from the current status.
     *
     * @param current current status
     * @return set of valid next statuses
     */
    public Set<Trip.TripStatus> getValidNextStatuses(Trip.TripStatus current) {
        return validTransitions.getOrDefault(current, Set.of());
    }

    /**
     * Get human-readable description of the status.
     *
     * @param status trip status
     * @return description
     */
    public String getStatusDescription(Trip.TripStatus status) {
        return switch (status) {
            case ONGOING -> "Trip is active and in progress";
            case ARRIVED -> "Driver has arrived at the destination";
            case DELIVERY_PENDING_VERIFICATION -> "Proof of delivery submitted, awaiting shipper verification";
            case COMPLETED -> "Trip has been completed successfully";
        };
    }

    /**
     * Check if a status represents an active (ongoing) state.
     *
     * @param status status to check
     * @return true if status is active
     */
    public boolean isActiveStatus(Trip.TripStatus status) {
        return status == Trip.TripStatus.ONGOING
            || status == Trip.TripStatus.ARRIVED
            || status == Trip.TripStatus.DELIVERY_PENDING_VERIFICATION;
    }

    /**
     * Check if a status represents a terminal (final) state.
     *
     * @param status status to check
     * @return true if status is terminal
     */
    public boolean isTerminalStatus(Trip.TripStatus status) {
        return status == Trip.TripStatus.COMPLETED;
    }

    /**
     * Get the initial status for new trips.
     *
     * @return initial trip status
     */
    public Trip.TripStatus getInitialStatus() {
        return Trip.TripStatus.ONGOING;
    }

    /**
     * Validate that a trip can be created with the given initial status.
     *
     * @param initialStatus proposed initial status
     * @return true if valid for creation
     */
    public boolean isValidInitialStatus(Trip.TripStatus initialStatus) {
        return initialStatus == Trip.TripStatus.ONGOING;
    }

    // ── Private Methods ──────────────────────────────────────────────────────

    private void initializeValidTransitions() {
        // ONGOING → ARRIVED (driver marks arrival at destination)
        // ONGOING → COMPLETED (legacy direct completion path, kept for backward compat)
        validTransitions.put(Trip.TripStatus.ONGOING, Set.of(
            Trip.TripStatus.ARRIVED,
            Trip.TripStatus.COMPLETED
        ));

        // ARRIVED → DELIVERY_PENDING_VERIFICATION (driver submits proof)
        validTransitions.put(Trip.TripStatus.ARRIVED, Set.of(
            Trip.TripStatus.DELIVERY_PENDING_VERIFICATION
        ));

        // DELIVERY_PENDING_VERIFICATION → COMPLETED (shipper verifies)
        // DELIVERY_PENDING_VERIFICATION → ARRIVED   (shipper rejects proof; driver resubmits)
        validTransitions.put(Trip.TripStatus.DELIVERY_PENDING_VERIFICATION, Set.of(
            Trip.TripStatus.COMPLETED,
            Trip.TripStatus.ARRIVED
        ));

        // COMPLETED → (no transitions allowed - terminal state)
        validTransitions.put(Trip.TripStatus.COMPLETED, Set.of());
    }
}