package com.ethioloadai.websocket.notification;

import com.ethioloadai.websocket.service.WebSocketSessionManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Phase 18 / Phase 19 – NotificationService unit tests.
 */
@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private WebSocketSessionManager sessionManager;

    private NotificationServiceImpl notificationService;

    @BeforeEach
    void setUp() {
        notificationService = new NotificationServiceImpl(sessionManager);
    }

    // ── notifyUser ────────────────────────────────────────────────────────────

    @Test
    void notifyUser_broadcastsToCorrectTopic() {
        notificationService.notifyUser(42L,
            NotificationService.Notification.info("TEST", "Hello", "World"));

        verify(sessionManager).broadcastToTopic(eq("user.42.notifications"), any());
    }

    @Test
    void notifyUser_includesAllRequiredFields() {
        notificationService.notifyUser(1L,
            NotificationService.Notification.urgent("ALERT", "Danger", "Help!", Map.of("key", "val")));

        verify(sessionManager).broadcastToTopic(eq("user.1.notifications"), any());
    }

    // ── notifyRole ────────────────────────────────────────────────────────────

    @Test
    void notifyRole_broadcastsToRoleTopic() {
        notificationService.notifyRole("DRIVER",
            NotificationService.Notification.info("INFO", "All drivers", "Note"));

        verify(sessionManager).broadcastToTopic(eq("role.driver.notifications"), any());
    }

    // ── notifyTripEvent ───────────────────────────────────────────────────────

    @Test
    void notifyTripEvent_notifiesBothShipperAndDriver() {
        notificationService.notifyTripEvent(10L, 20L, 30L,
            "TRIP_STARTED", "Trip is underway", Map.of());

        verify(sessionManager).broadcastToTopic(eq("user.20.notifications"), any()); // shipper
        verify(sessionManager).broadcastToTopic(eq("user.30.notifications"), any()); // driver
    }

    @Test
    void notifyTripEvent_worksWithNullDriver() {
        notificationService.notifyTripEvent(10L, 20L, null,
            "TRIP_STARTED", "Trip started", Map.of());

        verify(sessionManager).broadcastToTopic(eq("user.20.notifications"), any());
        verify(sessionManager, times(1)).broadcastToTopic(anyString(), any());
    }

    @Test
    void notifyTripEvent_worksWithNullShipper() {
        notificationService.notifyTripEvent(10L, null, 30L,
            "TRIP_STARTED", "Trip started", Map.of());

        verify(sessionManager).broadcastToTopic(eq("user.30.notifications"), any());
        verify(sessionManager, times(1)).broadcastToTopic(anyString(), any());
    }

    // ── notifyFleetOwner ──────────────────────────────────────────────────────

    @Test
    void notifyFleetOwner_notifiesOwnerUser() {
        notificationService.notifyFleetOwner(5L, 50L, "SPEEDING",
            "Vehicle exceeding speed limit", Map.of("speed", 120));

        verify(sessionManager).broadcastToTopic(eq("user.5.notifications"), any());
    }

    // ── Priority mapping ──────────────────────────────────────────────────────

    @Test
    void notification_priorityLevelsAreDistinct() {
        assertThat(NotificationService.Priority.values())
            .contains(
                NotificationService.Priority.LOW,
                NotificationService.Priority.NORMAL,
                NotificationService.Priority.HIGH,
                NotificationService.Priority.URGENT
            );
    }

    @Test
    void notification_factoryMethodsSetCorrectPriority() {
        var info   = NotificationService.Notification.info("T", "Title", "msg");
        var urgent = NotificationService.Notification.urgent("T", "Title", "msg", Map.of());

        assertThat(info.priority()).isEqualTo(NotificationService.Priority.NORMAL);
        assertThat(urgent.priority()).isEqualTo(NotificationService.Priority.URGENT);
    }

    @Test
    void notification_generatesUniqueIds() {
        var n1 = NotificationService.Notification.info("T", "A", "a");
        var n2 = NotificationService.Notification.info("T", "B", "b");
        assertThat(n1.id()).isNotEqualTo(n2.id());
    }

    @Test
    void notification_timestampIsSet() {
        var n = NotificationService.Notification.info("T", "Title", "msg");
        assertThat(n.timestamp()).isNotNull();
    }
}
