package com.ethioloadai.ai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiRecommendationResponse {

    @JsonProperty("recommended_trucks")
    @Builder.Default
    private List<Map<String, Object>> recommendedTrucks = List.of();

    @JsonProperty("matches")
    public List<Map<String, Object>> getMatches() {
        return recommendedTrucks;
    }

    @JsonProperty("recommendedTrucks")
    public List<Map<String, Object>> getRecommendedTrucksCamel() {
        return recommendedTrucks;
    }
}
