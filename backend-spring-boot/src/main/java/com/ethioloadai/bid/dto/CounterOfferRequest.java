package com.ethioloadai.bid.dto;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CounterOfferRequest {

    @NotNull(message = "Counter amount is required")
    @DecimalMin(value = "0.01", message = "Counter amount must be greater than 0")
    @Digits(integer = 13, fraction = 2, message = "Counter amount must have at most 13 integer digits and 2 decimal places")
    private BigDecimal counterAmount;

    @Size(max = 500, message = "Counter note must not exceed 500 characters")
    private String counterNote;
}