package com.ethioloadai.message.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;

import java.time.LocalDateTime;

@Builder
public record MessageResponse(
    Long id,
    Long senderId,
    Long receiverId,
    Long freightId,
    String type,
    String content,
    String maskedContent,
    Boolean hasPhoneNumber,
    Boolean hasPaymentRequest,
    Boolean isRead,
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    LocalDateTime createdAt,
    SenderDto sender,
    @JsonInclude(JsonInclude.Include.NON_NULL)
    String warning
) {}
