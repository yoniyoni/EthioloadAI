package com.ethioloadai.user.exception;

import com.ethioloadai.exception.BaseException;

/**
 * Domain exceptions for User administration capability.
 */
public class UserException extends BaseException {

    public UserException(String message, String code, int httpStatus) {
        super(message, code, httpStatus);
    }

    public static class UserNotFoundException extends UserException {
        public UserNotFoundException(Long id) {
            super("User not found with id: " + id, "USER_NOT_FOUND", 404);
        }

        public UserNotFoundException(String message) {
            super(message, "USER_NOT_FOUND", 404);
        }
    }

    public static class UserAlreadyExistsException extends UserException {
        public UserAlreadyExistsException(String message) {
            super(message, "USER_ALREADY_EXISTS", 409);
        }
    }

    public static class UserHasHistoryException extends UserException {
        public UserHasHistoryException(String message) {
            super(message, "USER_HAS_HISTORY", 409);
        }
    }

    public static class UserValidationException extends UserException {
        public UserValidationException(String message) {
            super(message, "USER_VALIDATION_ERROR", 422);
        }
    }
}
