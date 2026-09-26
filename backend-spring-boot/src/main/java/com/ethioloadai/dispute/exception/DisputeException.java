package com.ethioloadai.dispute.exception;

import com.ethioloadai.exception.BaseException;

public class DisputeException extends BaseException {

    public DisputeException(String message, String code, int httpStatus) {
        super(message, code, httpStatus);
    }

    public static class DisputeNotFoundException extends DisputeException {
        public DisputeNotFoundException(String message) {
            super(message, "DISPUTE_NOT_FOUND", 404);
        }
    }

    public static class UnauthorizedDisputeAccessException extends DisputeException {
        public UnauthorizedDisputeAccessException(String message) {
            super(message, "FORBIDDEN", 403);
        }
    }

    public static class DuplicateDisputeException extends DisputeException {
        public DuplicateDisputeException(String message) {
            super(message, "DUPLICATE_DISPUTE", 409);
        }
    }

    public static class InvalidDisputeOperationException extends DisputeException {
        public InvalidDisputeOperationException(String message) {
            super(message, "INVALID_DISPUTE_OPERATION", 400);
        }

        public InvalidDisputeOperationException(String message, int httpStatus) {
            super(message, "INVALID_DISPUTE_OPERATION", httpStatus);
        }
    }

    public static class DisputeAlreadyResolvedException extends DisputeException {
        public DisputeAlreadyResolvedException(String message) {
            super(message, "DISPUTE_ALREADY_RESOLVED", 409);
        }
    }
}
