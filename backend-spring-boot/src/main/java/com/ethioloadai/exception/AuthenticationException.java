package com.ethioloadai.exception;

public class AuthenticationException extends BaseException {

    public AuthenticationException(String message) {
        super(message, "AUTH_ERROR", 401);
    }

    public AuthenticationException(String message, Throwable cause) {
        super(message, cause, "AUTH_ERROR", 401);
    }

    public static class InvalidCredentialsException extends AuthenticationException {
        public InvalidCredentialsException() {
            super("Invalid credentials. Check your email/phone and password.");
        }
    }

    public static class InvalidTokenException extends AuthenticationException {
        public InvalidTokenException() {
            super("Invalid token.");
        }
    }

    public static class ExpiredTokenException extends AuthenticationException {
        public ExpiredTokenException() {
            super("Token has expired.");
        }
    }

    public static class RevokedTokenException extends AuthenticationException {
        public RevokedTokenException() {
            super("Token has been revoked.");
        }
    }

    public static class MissingTokenException extends AuthenticationException {
        public MissingTokenException() {
            super("Authorization header is missing.");
        }
    }

    public static class AccountInactiveException extends AuthenticationException {
        public AccountInactiveException() {
            super("Account is inactive. Please contact support.");
        }

        @Override
        public int getHttpStatus() {
            return 403;
        }
    }

    public static class DriverNotVerifiedException extends AuthenticationException {
        public DriverNotVerifiedException() {
            super("Driver account is not verified. Please complete document verification.");
        }
    }

    public static class CurrentPasswordIncorrectException extends AuthenticationException {
        public CurrentPasswordIncorrectException() {
            super("Current password is incorrect.");
        }
    }
}
