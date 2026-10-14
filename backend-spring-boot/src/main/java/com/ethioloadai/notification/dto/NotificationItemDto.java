package com.ethioloadai.notification.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * DTO for an individual user notification item.
 * Supports both snake_case and camelCase field access for cross-platform compatibility.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationItemDto {

    private String id;
    private String type;
    private Map<String, Object> data;

    @JsonProperty("read_at")
    private String readAt;

    @JsonProperty("created_at")
    private String createdAt;

    @JsonProperty("readAt")
    public String getReadAtCamel() {
        return readAt;
    }

    @JsonProperty("createdAt")
    public String getCreatedAtCamel() {
        return createdAt;
    }

    public static NotificationItemDto of(String id, String type, Map<String, Object> data, LocalDateTime createdAt) {
        String isoCreatedAt = createdAt != null ? createdAt.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) : null;
        return NotificationItemDto.builder()
                .id(id)
                .type(type)
                .data(data)
                .readAt(null)
                .createdAt(isoCreatedAt)
                .build();
    }
}
