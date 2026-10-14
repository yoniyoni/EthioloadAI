package com.ethioloadai.exception;

public class RegistrationConflictException extends BaseException {
    public RegistrationConflictException() {
        super("Unable to register using the provided details.", "REGISTRATION_UNAVAILABLE", 409);
    }
}
