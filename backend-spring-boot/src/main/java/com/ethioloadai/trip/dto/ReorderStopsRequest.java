package com.ethioloadai.trip.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReorderStopsRequest {

    @NotEmpty(message = "Stop IDs list cannot be empty")
    @NotNull(message = "Stop IDs list is required")
    private List<Long> stopIds;
}