package com.ethioloadai.fleet.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AddDriverRequest {
    @NotNull(message = "Driver ID is required")
    private Long driverId;
}
