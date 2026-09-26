package com.ethioloadai.ai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiAssistantResponse {

    @JsonProperty("response")
    private AssistantContent response;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AssistantContent {
        private String text;
        private String intent;
    }

    public static AiAssistantResponse of(String text, String intent) {
        return AiAssistantResponse.builder()
                .response(new AssistantContent(text, intent))
                .build();
    }
}
