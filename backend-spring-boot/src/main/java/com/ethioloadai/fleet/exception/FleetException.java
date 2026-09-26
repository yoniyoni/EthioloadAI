package com.ethioloadai.fleet.exception;

/**
 * Domain exceptions for fleet management operations.
 */
public class FleetException extends RuntimeException {

    public FleetException(String message) {
        super(message);
    }

    // ── Concrete subtypes ─────────────────────────────────────────────────────

    /** Thrown when a resource is not found or access is denied. */
    public static class FleetResourceNotFoundException extends FleetException {
        public FleetResourceNotFoundException(String message) {
            super(message);
        }
    }

    /** Thrown when the caller is not authorized to perform a fleet operation. */
    public static class FleetAuthorizationException extends FleetException {
        public FleetAuthorizationException(String message) {
            super(message);
        }
    }

    /** Thrown when fleet operation validation fails. */
    public static class FleetValidationException extends FleetException {
        public FleetValidationException(String message) {
            super(message);
        }
    }

    /** Thrown when a driver/vehicle already belongs to another fleet. */
    public static class ResourceAlreadyAssignedException extends FleetException {
        public ResourceAlreadyAssignedException(String message) {
            super(message);
        }
    }
}
