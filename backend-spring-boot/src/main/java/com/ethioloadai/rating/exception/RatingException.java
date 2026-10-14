package com.ethioloadai.rating.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

public final class RatingException {
    private RatingException() {}

    @ResponseStatus(HttpStatus.FORBIDDEN)
    public static class AuthorizationException extends RuntimeException {
        public AuthorizationException(String msg) { super(msg); }
    }

    @ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
    public static class InvalidStateException extends RuntimeException {
        public InvalidStateException(String msg) { super(msg); }
    }

    @ResponseStatus(HttpStatus.CONFLICT)
    public static class AlreadyRatedException extends RuntimeException {
        public AlreadyRatedException(Long bookingId) {
            super("You have already submitted a rating for booking " + bookingId);
        }
    }

    public static InvalidStateException bookingNotCompleted(Long bookingId) {
        return new InvalidStateException(
                "Booking " + bookingId + " must be DELIVERED or COMPLETED before rating is allowed");
    }
}
