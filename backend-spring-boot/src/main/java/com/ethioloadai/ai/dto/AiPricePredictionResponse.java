package com.ethioloadai.ai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiPricePredictionResponse {

    @JsonProperty("price_min")
    private Integer priceMin;

    @JsonProperty("price_max")
    private Integer priceMax;

    @JsonProperty("estimated_price")
    private Integer estimatedPrice;

    @JsonProperty("distance_km")
    private Integer distanceKm;

    @Builder.Default
    private String currency = "ETB";

    @Builder.Default
    private Double confidence = 0.85;

    @Builder.Default
    private String source = "local_estimate";

    @JsonProperty("priceMin")
    public Integer getPriceMinCamel() {
        return priceMin;
    }

    @JsonProperty("priceMax")
    public Integer getPriceMaxCamel() {
        return priceMax;
    }

    @JsonProperty("estimatedPrice")
    public Integer getEstimatedPriceCamel() {
        return estimatedPrice;
    }

    @JsonProperty("distanceKm")
    public Integer getDistanceKmCamel() {
        return distanceKm;
    }
}
