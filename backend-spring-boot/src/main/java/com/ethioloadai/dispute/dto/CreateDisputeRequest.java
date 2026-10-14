package com.ethioloadai.dispute.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateDisputeRequest(
        @NotNull(message = "Freight ID is required")
        Long freightId,

        @NotBlank(message = "Reason is required")
        @Size(max = 255, message = "Reason must not exceed 255 characters")
        String reason,

        @Size(max = 2000, message = "Description must not exceed 2000 characters")
        String description
) {
}
