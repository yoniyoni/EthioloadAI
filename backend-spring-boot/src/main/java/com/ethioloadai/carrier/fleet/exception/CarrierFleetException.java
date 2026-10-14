package com.ethioloadai.carrier.fleet.exception;

import com.ethioloadai.exception.BaseException;

/**
 * Domain exceptions for Phase 4 carrier fleet operations.
 * Extends {@link BaseException} so the existing {@link com.ethioloadai.exception.GlobalExceptionHandler}
 * handles them automatically.
 */
public class CarrierFleetException extends BaseException {

    public CarrierFleetException(String message, String code, int httpStatus) {
        super(message, code, httpStatus);
    }

    /** 404 — resource not found or hidden from caller (IDOR protection). */
    public static CarrierFleetException notFound(String what) {
        return new CarrierFleetException(what + " not found", "FLEET_NOT_FOUND", 404);
    }

    /** 403 — caller does not have permission to perform this action. */
    public static CarrierFleetException forbidden() {
        return new CarrierFleetException("Company fleet access denied", "FLEET_FORBIDDEN", 403);
    }

    /** 409 — business-rule conflict (duplicate active assignment, etc.). */
    public static CarrierFleetException conflict(String message) {
        return new CarrierFleetException(message, "FLEET_CONFLICT", 409);
    }

    /** 422 — invalid state transition or request data. */
    public static CarrierFleetException invalid(String message) {
        return new CarrierFleetException(message, "FLEET_INVALID", 422);
    }
}
