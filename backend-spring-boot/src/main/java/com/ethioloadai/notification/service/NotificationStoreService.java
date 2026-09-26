package com.ethioloadai.notification.service;

import com.ethioloadai.notification.dto.NotificationItemDto;
import com.ethioloadai.notification.dto.NotificationListResponse;

import java.util.Map;

/**
 * Service for in-memory, thread-safe notification storage and lifecycle.
 */
public interface NotificationStoreService {

    /**
     * Get up to 50 latest notifications for the given user with unread count.
     */
    NotificationListResponse getNotifications(Long userId);

    /**
     * Mark a single notification as read.
     * @return true if found and updated, false if not found
     */
    boolean markAsRead(Long userId, String notificationId);

    /**
     * Mark all notifications for the given user as read.
     */
    void markAllAsRead(Long userId);

    /**
     * Record a new notification for a specific user.
     */
    void recordNotification(Long userId, String type, Map<String, Object> data);
}
