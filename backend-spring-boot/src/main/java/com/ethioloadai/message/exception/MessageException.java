package com.ethioloadai.message.exception;

import com.ethioloadai.exception.BaseException;

public class MessageException extends BaseException {

    public MessageException(String message, String code, int httpStatus) {
        super(message, code, httpStatus);
    }

    public static class FreightNotFoundException extends MessageException {
        public FreightNotFoundException(Long freightId) {
            super("Freight not found with ID: " + freightId, "FREIGHT_NOT_FOUND", 404);
        }
    }

    public static class UnauthorizedParticipantException extends MessageException {
        public UnauthorizedParticipantException(String message) {
            super(message, "FORBIDDEN", 403);
        }
    }

    public static class InvalidMessageException extends MessageException {
        public InvalidMessageException(String message) {
            super(message, "INVALID_MESSAGE", 400);
        }
    }
}
