package com.ethioloadai.trip.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateTripStopStatusRequest {

    @NotBlank(message = "Stop status is required")
    @Pattern(regexp = "^(PENDING|ARRIVED|LOADED|COMPLETED)$", 
             message = "Stop status must be one of: PENDING, ARRIVED, LOADED, COMPLETED")
    private String stopStatus;
}