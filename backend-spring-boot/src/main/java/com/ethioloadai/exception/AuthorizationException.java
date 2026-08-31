package com.ethioloadai.exception;

public class AuthorizationException extends BaseException {

    public AuthorizationException(String message) {
        super(message, "FORBIDDEN", 403);
    }

    public AuthorizationException(String message, Throwable cause) {
        super(message, cause, "FORBIDDEN", 403);
    }

    public static class AccessDeniedException extends AuthorizationException {
        public AccessDeniedException() {
            super("Forbidden");
        }
    }

    public static class ResourceNotFoundException extends AuthorizationException {
        public ResourceNotFoundException() {
            super("Resource not found");
        }

        public ResourceNotFoundException(String resource) {
            super(resource + " not found");
        }
    }

    public static class OwnershipException extends AuthorizationException {
        public OwnershipException() {
            super("You do not have permission to access this resource");
        }
    }
}
