package com.ethioloadai.message.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateMessageRequest(
    @NotNull(message = "freightId is required")
    Long freightId,

    Long receiverId,

    @NotBlank(message = "Message content cannot be blank")
    @Size(max = 2000, message = "Message content must not exceed 2000 characters")
    String content,

    String type
) {}
