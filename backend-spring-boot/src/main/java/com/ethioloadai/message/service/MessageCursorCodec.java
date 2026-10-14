package com.ethioloadai.message.service;

import com.ethioloadai.message.exception.MessageException;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;

final class MessageCursorCodec {

    private MessageCursorCodec() {}

    static String encode(Long freightId, LocalDateTime createdAt, Long messageId) {
        String payload = "v1|" + freightId + "|" + createdAt.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                + "|" + messageId;
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payload.getBytes(StandardCharsets.UTF_8));
    }

    static Cursor decode(String encoded, Long expectedFreightId) {
        try {
            String payload = new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8);
            String[] parts = payload.split("\\|", -1);
            if (parts.length != 4 || !"v1".equals(parts[0])) {
                throw new IllegalArgumentException("Unsupported cursor format");
            }

            long freightId = Long.parseLong(parts[1]);
            LocalDateTime createdAt = LocalDateTime.parse(parts[2], DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            long messageId = Long.parseLong(parts[3]);
            if (freightId <= 0 || messageId <= 0) {
                throw new IllegalArgumentException("Cursor identifiers must be positive");
            }
            if (freightId != expectedFreightId) {
                throw new MessageException.InvalidMessageException(
                        "Message cursor does not belong to this freight");
            }
            return new Cursor(createdAt, messageId);
        } catch (MessageException.InvalidMessageException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new MessageException.InvalidMessageException("Malformed message cursor");
        }
    }

    record Cursor(LocalDateTime createdAt, Long messageId) {}
}
