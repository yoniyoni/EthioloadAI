package com.ethioloadai.notification.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Envelope DTO for notifications matching the legacy Laravel shape.
 * Expected by both React ethio-topbar.tsx and Flutter NotificationRepository.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationListResponse {

    @Builder.Default
    private List<NotificationItemDto> data = List.of();

    @JsonProperty("unread_count")
    @Builder.Default
    private Integer unreadCount = 0;

    @JsonProperty("unreadCount")
    public Integer getUnreadCountCamel() {
        return unreadCount;
    }
}
