package com.ethioloadai.trip.service;

import com.ethioloadai.trip.entity.Trip;
import com.ethioloadai.user.entity.User;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Service for handling Trip status transitions with comprehensive validation,
 * business rules, and automated transition logic.
 */
public interface TripStatusTransitionService {

    /**
     * Execute a status transition with full validation and business logic.
     * Handles all side effects including timestamp updates, related entity changes,
     * and validation of prerequisites.
     *
     * @param tripId trip to transition
     * @param targetStatus target status
     * @param user user requesting transition
     * @return updated trip
     */
    Trip executeStatusTransition(Long tripId, Trip.TripStatus targetStatus, User user);

    /**
     * Validate if a status transition can be performed.
     * Checks business rules, prerequisites, and authorization.
     *
     * @param trip trip to validate
     * @param targetStatus target status
     * @param user user requesting transition
     * @return validation result
     */
    TransitionValidationResult validateTransition(Trip trip, Trip.TripStatus targetStatus, User user);

    /**
     * Get all possible next statuses for a trip.
     * Considers business rules and current trip state.
     *
     * @param trip trip to analyze
     * @param user user requesting information
     * @return list of possible statuses with descriptions
     */
    List<StatusOption> getAvailableTransitions(Trip trip, User user);

    /**
     * Force a status transition (admin only).
     * Bypasses some validation for emergency scenarios.
     *
     * @param tripId trip to transition
     * @param targetStatus target status
     * @param admin admin user
     * @param reason reason for forced transition
     * @return updated trip
     */
    Trip forceStatusTransition(Long tripId, Trip.TripStatus targetStatus, User admin, String reason);

    /**
     * Process any safe automatic transitions. Delivery completion requires shipper confirmation.
     *
     * @return list of trips that were automatically transitioned
     */
    List<Trip> processAutomaticTransitions();

    /**
     * Get transition history for a trip (if audit logging is implemented).
     *
     * @param tripId trip ID
     * @param user requesting user
     * @return transition history
     */
    List<TransitionEvent> getTransitionHistory(Long tripId, User user);

    /**
     * Rollback a recent transition (admin only, limited scenarios).
     *
     * @param tripId trip ID
     * @param admin admin user
     * @param reason rollback reason
     * @return updated trip
     */
    Trip rollbackTransition(Long tripId, User admin, String reason);

    /**
     * Represents validation result for a status transition.
     */
    class TransitionValidationResult {
        private final boolean valid;
        private final List<String> violations;
        private final List<String> warnings;
        private final Trip.TripStatus currentStatus;
        private final Trip.TripStatus targetStatus;

        public TransitionValidationResult(boolean valid, List<String> violations, 
                                        List<String> warnings, Trip.TripStatus currentStatus, 
                                        Trip.TripStatus targetStatus) {
            this.valid = valid;
            this.violations = violations;
            this.warnings = warnings;
            this.currentStatus = currentStatus;
            this.targetStatus = targetStatus;
        }

        // Getters
        public boolean isValid() { return valid; }
        public List<String> getViolations() { return violations; }
        public List<String> getWarnings() { return warnings; }
        public Trip.TripStatus getCurrentStatus() { return currentStatus; }
        public Trip.TripStatus getTargetStatus() { return targetStatus; }
        public boolean hasWarnings() { return !warnings.isEmpty(); }
    }

    /**
     * Represents an available status transition option.
     */
    class StatusOption {
        private final Trip.TripStatus status;
        private final String description;
        private final boolean requiresConfirmation;
        private final List<String> prerequisites;

        public StatusOption(Trip.TripStatus status, String description, 
                           boolean requiresConfirmation, List<String> prerequisites) {
            this.status = status;
            this.description = description;
            this.requiresConfirmation = requiresConfirmation;
            this.prerequisites = prerequisites;
        }

        // Getters
        public Trip.TripStatus getStatus() { return status; }
        public String getDescription() { return description; }
        public boolean isRequiresConfirmation() { return requiresConfirmation; }
        public List<String> getPrerequisites() { return prerequisites; }
    }

    /**
     * Represents a transition event for audit purposes.
     */
    class TransitionEvent {
        private final Trip.TripStatus fromStatus;
        private final Trip.TripStatus toStatus;
        private final LocalDateTime timestamp;
        private final Long userId;
        private final String userRole;
        private final String reason;
        private final boolean wasForced;

        public TransitionEvent(Trip.TripStatus fromStatus, Trip.TripStatus toStatus,
                              LocalDateTime timestamp, Long userId, String userRole,
                              String reason, boolean wasForced) {
            this.fromStatus = fromStatus;
            this.toStatus = toStatus;
            this.timestamp = timestamp;
            this.userId = userId;
            this.userRole = userRole;
            this.reason = reason;
            this.wasForced = wasForced;
        }

        // Getters
        public Trip.TripStatus getFromStatus() { return fromStatus; }
        public Trip.TripStatus getToStatus() { return toStatus; }
        public LocalDateTime getTimestamp() { return timestamp; }
        public Long getUserId() { return userId; }
        public String getUserRole() { return userRole; }
        public String getReason() { return reason; }
        public boolean isWasForced() { return wasForced; }
    }
}