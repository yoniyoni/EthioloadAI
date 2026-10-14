package com.ethioloadai.settlement.exception;

import com.ethioloadai.settlement.entity.CarrierSettlement.SettlementStatus;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** Typed exceptions for CarrierSettlement operations. */
public final class SettlementException {

    private SettlementException() {}

    @ResponseStatus(HttpStatus.NOT_FOUND)
    public static class NotFoundException extends RuntimeException {
        public NotFoundException(String msg) { super(msg); }
    }

    @ResponseStatus(HttpStatus.FORBIDDEN)
    public static class AuthorizationException extends RuntimeException {
        public AuthorizationException(String msg) { super(msg); }
    }

    @ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
    public static class InvalidStateException extends RuntimeException {
        public InvalidStateException(String msg) { super(msg); }
    }

    @ResponseStatus(HttpStatus.CONFLICT)
    public static class DuplicateSettlementException extends RuntimeException {
        public DuplicateSettlementException(Long bookingId) {
            super("A settlement already exists for booking " + bookingId);
        }
    }

    public static NotFoundException notFound(Long settlementId) {
        return new NotFoundException("Settlement not found: " + settlementId);
    }

    public static InvalidStateException invalidTransition(SettlementStatus from, String action) {
        return new InvalidStateException(
                "Cannot " + action + " settlement in status " + from);
    }

    public static AuthorizationException unauthorized(String reason) {
        return new AuthorizationException("Settlement access denied: " + reason);
    }
}
