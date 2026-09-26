package com.ethioloadai.setting.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for updating platform pricing settings.
 *
 * <p>Accepts either snake_case (e.g. {@code rate_min}) or camelCase (e.g. {@code rateMin}).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdatePricingSettingsRequest {

    @NotNull(message = "rate_min is required")
    @DecimalMin(value = "1.0", message = "rate_min must be at least 1")
    @JsonProperty("rate_min")
    @JsonAlias({"rateMin", "rate_min"})
    private Double rateMin;

    @NotNull(message = "rate_max is required")
    @DecimalMin(value = "1.0", message = "rate_max must be at least 1")
    @JsonProperty("rate_max")
    @JsonAlias({"rateMax", "rate_max"})
    private Double rateMax;
}
