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
public class AiBackhaulResponse {

    @JsonProperty("opportunities")
    @Builder.Default
    private List<Map<String, Object>> opportunities = List.of();
}
