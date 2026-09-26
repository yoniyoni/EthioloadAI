package com.ethioloadai.dispute.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record ResolveDisputeRequest(
        @NotBlank(message = "Resolution is required")
        String resolution,

        @Size(max = 2000, message = "Admin notes must not exceed 2000 characters")
        String adminNotes,

        @PositiveOrZero(message = "Refund amount must not be negative")
        BigDecimal refundAmount
) {
}
