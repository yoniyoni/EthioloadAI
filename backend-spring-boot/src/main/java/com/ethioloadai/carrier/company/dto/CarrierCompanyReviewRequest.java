package com.ethioloadai.carrier.company.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CarrierCompanyReviewRequest {
    @NotBlank @Size(max = 2000)
    private String message;
}

