package com.ethioloadai.message.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record MessageListResponse(
        List<MessageResponse> messages,
        MessagePageMetadata page
) {
    public MessageListResponse(List<MessageResponse> messages) {
        this(messages, null);
    }
}
