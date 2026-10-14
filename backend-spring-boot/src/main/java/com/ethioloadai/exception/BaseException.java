package com.ethioloadai.exception;

import lombok.Getter;

@Getter
public abstract class BaseException extends RuntimeException {

    private final String code;
    private final int httpStatus;

    protected BaseException(String message, String code, int httpStatus) {
        super(message);
        this.code = code;
        this.httpStatus = httpStatus;
    }

    protected BaseException(String message, Throwable cause, String code, int httpStatus) {
        super(message, cause);
        this.code = code;
        this.httpStatus = httpStatus;
    }
}
