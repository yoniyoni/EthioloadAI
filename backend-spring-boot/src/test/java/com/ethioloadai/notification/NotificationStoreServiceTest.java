package com.ethioloadai.notification;

import com.ethioloadai.notification.dto.NotificationItemDto;
import com.ethioloadai.notification.dto.NotificationListResponse;
import com.ethioloadai.notification.service.NotificationStoreService;
import com.ethioloadai.notification.service.NotificationStoreServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class NotificationStoreServiceTest {

    private NotificationStoreService notificationStoreService;

    @BeforeEach
    void setUp() {
        notificationStoreService = new NotificationStoreServiceImpl();
    }

    @Test
    @DisplayName("Should retrieve default welcome notification when user has no notifications")
    void testGetNotificationsDefaultSeed() {
        NotificationListResponse response = notificationStoreService.getNotifications(101L);

        assertNotNull(response);
        assertNotNull(response.getData());
        assertFalse(response.getData().isEmpty());
        assertEquals("SystemWelcomeNotification", response.getData().get(0).getType());
        assertEquals(0, response.getUnreadCount()); // Welcome notification is seeded as read
    }

    @Test
    @DisplayName("Should record new notification and track unread count correctly")
    void testRecordNotificationAndUnreadCount() {
        Long userId = 202L;
        notificationStoreService.recordNotification(userId, "NewFreightNotification", Map.of(
                "message", "New freight available from Addis to Adama",
                "route", "Addis Ababa → Adama"
        ));

        NotificationListResponse response = notificationStoreService.getNotifications(userId);
        assertNotNull(response);
        assertTrue(response.getUnreadCount() >= 1);

        NotificationItemDto latest = response.getData().get(0);
        assertEquals("NewFreightNotification", latest.getType());
        assertNull(latest.getReadAt());
        assertEquals("New freight available from Addis to Adama", latest.getData().get("message"));
    }

    @Test
    @DisplayName("Should mark single notification as read")
    void testMarkAsRead() {
        Long userId = 303L;
        notificationStoreService.recordNotification(userId, "BidAcceptedNotification", Map.of(
                "message", "Your bid was accepted"
        ));

        NotificationListResponse before = notificationStoreService.getNotifications(userId);
        String notifId = before.getData().get(0).getId();
        int unreadBefore = before.getUnreadCount();

        boolean marked = notificationStoreService.markAsRead(userId, notifId);
        assertTrue(marked);

        NotificationListResponse after = notificationStoreService.getNotifications(userId);
        assertNotNull(after.getData().get(0).getReadAt());
        assertEquals(unreadBefore - 1, after.getUnreadCount());
    }

    @Test
    @DisplayName("Should mark all notifications as read")
    void testMarkAllAsRead() {
        Long userId = 404L;
        notificationStoreService.recordNotification(userId, "Notif1", Map.of("message", "M1"));
        notificationStoreService.recordNotification(userId, "Notif2", Map.of("message", "M2"));

        NotificationListResponse before = notificationStoreService.getNotifications(userId);
        assertTrue(before.getUnreadCount() >= 2);

        notificationStoreService.markAllAsRead(userId);

        NotificationListResponse after = notificationStoreService.getNotifications(userId);
        assertEquals(0, after.getUnreadCount());
    }

    @Test
    @DisplayName("Should cap notifications at 50 per user")
    void testCapAtFiftyNotifications() {
        Long userId = 505L;
        for (int i = 0; i < 60; i++) {
            notificationStoreService.recordNotification(userId, "TestNotif", Map.of("index", i));
        }

        NotificationListResponse response = notificationStoreService.getNotifications(userId);
        assertEquals(50, response.getData().size());
    }
}
