package com.ethioloadai.exception;

import java.util.Map;

public class ValidationException extends BaseException {

    private final Map<String, String> errors;

    public ValidationException(String message, Map<String, String> errors) {
        super(message, "VALIDATION_ERROR", 422);
        this.errors = errors;
    }

    public ValidationException(String message, String field, String error) {
        super(message, "VALIDATION_ERROR", 422);
        this.errors = Map.of(field, error);
    }

    public Map<String, String> getErrors() {
        return errors;
    }
}
