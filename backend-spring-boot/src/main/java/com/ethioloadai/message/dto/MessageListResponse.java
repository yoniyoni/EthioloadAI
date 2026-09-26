package com.ethioloadai.message.dto;

import java.util.List;

public record MessageListResponse(
    List<MessageResponse> messages
) {}
