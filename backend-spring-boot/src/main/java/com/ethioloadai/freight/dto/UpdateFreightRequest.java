package com.ethioloadai.freight.dto;

import com.ethioloadai.freight.entity.Freight;
import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateFreightRequest {
    private String cargoDescription;

    @DecimalMin(value = "1.0", message = "Budget must be at least 1 ETB")
    private BigDecimal budget;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate deadline;

    @Pattern(regexp = "^(low|normal|high|urgent)$",
            message = "Urgency level must be one of: low, normal, high, urgent")
    private String urgencyLevel;

    private Freight.FreightStatus status;
}
