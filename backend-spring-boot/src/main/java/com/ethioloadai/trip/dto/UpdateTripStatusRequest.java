package com.ethioloadai.trip.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateTripStatusRequest {

    @NotBlank(message = "Trip status is required")
    @Pattern(regexp = "^(ONGOING|COMPLETED)$", 
             message = "Trip status must be either ONGOING or COMPLETED")
    private String tripStatus;
    
    @Size(max = 500, message = "Reason must not exceed 500 characters")
    private String reason;
    
    @Builder.Default
    private Boolean forceTransition = false;
}