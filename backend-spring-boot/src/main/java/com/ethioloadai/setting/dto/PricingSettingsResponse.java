package com.ethioloadai.setting.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for platform pricing settings.
 *
 * <p>Supports both snake_case (matching legacy Laravel API expected by frontend)
 * and standard camelCase properties via JSON property annotations.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PricingSettingsResponse {

    @JsonProperty("rate_min")
    private Double rateMin;

    @JsonProperty("rate_max")
    private Double rateMax;

    @JsonProperty("intracity_rate_min")
    private Double intracityRateMin;

    @JsonProperty("intracity_rate_max")
    private Double intracityRateMax;

    @JsonProperty("success")
    private Boolean success;

    @JsonProperty("rateMin")
    public Double getRateMinCamel() {
        return rateMin;
    }

    @JsonProperty("rateMax")
    public Double getRateMaxCamel() {
        return rateMax;
    }

    @JsonProperty("intracityRateMin")
    public Double getIntracityRateMinCamel() {
        return intracityRateMin;
    }

    @JsonProperty("intracityRateMax")
    public Double getIntracityRateMaxCamel() {
        return intracityRateMax;
    }
}
