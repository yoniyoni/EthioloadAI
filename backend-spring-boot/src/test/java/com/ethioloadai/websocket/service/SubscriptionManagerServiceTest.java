package com.ethioloadai.websocket.service;

import com.ethioloadai.websocket.interceptor.WebSocketAuthenticationInterceptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.web.socket.WebSocketSession;

import java.net.URI;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Phase 13 / Phase 19 – SubscriptionManagerService unit tests.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SubscriptionManagerServiceTest {

    @Mock
    private WebSocketSessionManager sessionManager;

    private SubscriptionManagerServiceImpl subscriptionManager;

    @BeforeEach
    void setUp() {
        subscriptionManager = new SubscriptionManagerServiceImpl(sessionManager);
    }

    // ── onSubscribed / getSubscriptionsForSession ──────────────────────────────

    @Test
    void onSubscribed_recordsMetadata() {
        WebSocketSession session = mockSession("sess-1", 10L, "Alice", "DRIVER");

        subscriptionManager.onSubscribed(session, "trip.1.location", null);

        List<SubscriptionManagerService.SubscriptionInfo> subs =
            subscriptionManager.getSubscriptionsForSession("sess-1");

        assertThat(subs).hasSize(1);
        assertThat(subs.get(0).topic()).isEqualTo("trip.1.location");
        assertThat(subs.get(0).userId()).isEqualTo(10L);
        assertThat(subs.get(0).username()).isEqualTo("Alice");
        assertThat(subs.get(0).userRole()).isEqualTo("DRIVER");
        assertThat(subs.get(0).subscribedAt()).isNotNull();
    }

    @Test
    void onSubscribed_multipleTopicsSameSession() {
        WebSocketSession session = mockSession("sess-1", 10L, "Bob", "FLEET_OWNER");

        subscriptionManager.onSubscribed(session, "trip.1.location",   null);
        subscriptionManager.onSubscribed(session, "vehicle.5.location", null);

        assertThat(subscriptionManager.getSubscriptionsForSession("sess-1")).hasSize(2);
    }

    // ── getSubscriptionsForUser ───────────────────────────────────────────────

    @Test
    void getSubscriptionsForUser_filtersCorrectly() {
        WebSocketSession s1 = mockSession("sess-1", 10L, "Alice", "DRIVER");
        WebSocketSession s2 = mockSession("sess-2", 99L, "Other", "SHIPPER");

        subscriptionManager.onSubscribed(s1, "trip.1.location", null);
        subscriptionManager.onSubscribed(s2, "trip.2.location", null);

        List<SubscriptionManagerService.SubscriptionInfo> subs =
            subscriptionManager.getSubscriptionsForUser(10L);

        assertThat(subs).hasSize(1);
        assertThat(subs.get(0).userId()).isEqualTo(10L);
    }

    // ── getSubscribersForTopic ────────────────────────────────────────────────

    @Test
    void getSubscribersForTopic_returnsAllSubscribedSessions() {
        WebSocketSession s1 = mockSession("sess-1", 10L, "A", "DRIVER");
        WebSocketSession s2 = mockSession("sess-2", 11L, "B", "SHIPPER");

        subscriptionManager.onSubscribed(s1, "trip.5.location", null);
        subscriptionManager.onSubscribed(s2, "trip.5.location", null);

        List<SubscriptionManagerService.SubscriptionInfo> subs =
            subscriptionManager.getSubscribersForTopic("trip.5.location");

        assertThat(subs).hasSize(2);
    }

    // ── onUnsubscribed ────────────────────────────────────────────────────────

    @Test
    void onUnsubscribed_removesEntry() {
        WebSocketSession session = mockSession("sess-1", 10L, "Alice", "DRIVER");
        subscriptionManager.onSubscribed(session, "trip.1.location", null);
        subscriptionManager.onUnsubscribed("sess-1", "trip.1.location");

        assertThat(subscriptionManager.getSubscriptionsForSession("sess-1")).isEmpty();
    }

    // ── onSessionClosed ───────────────────────────────────────────────────────

    @Test
    void onSessionClosed_removesAllSubscriptionsForSession() {
        WebSocketSession session = mockSession("sess-1", 10L, "Alice", "DRIVER");
        subscriptionManager.onSubscribed(session, "trip.1.location",   null);
        subscriptionManager.onSubscribed(session, "vehicle.1.location", null);

        subscriptionManager.onSessionClosed("sess-1");

        assertThat(subscriptionManager.getSubscriptionsForSession("sess-1")).isEmpty();
    }

    // ── forceUnsubscribeUser ──────────────────────────────────────────────────

    @Test
    void forceUnsubscribeUser_removesAllUserSubscriptions() {
        WebSocketSession s1 = mockSession("sess-1", 10L, "Alice", "DRIVER");
        WebSocketSession s2 = mockSession("sess-2", 10L, "Alice", "DRIVER");

        subscriptionManager.onSubscribed(s1, "trip.1.location", null);
        subscriptionManager.onSubscribed(s2, "trip.2.location", null);

        // Mock session manager to return empty list (no live sessions to unsubscribe from)
        when(sessionManager.getSubscribersForTopic(anyString())).thenReturn(List.of());

        int removed = subscriptionManager.forceUnsubscribeUser(10L);
        assertThat(removed).isEqualTo(2);
    }

    // ── getSummary ────────────────────────────────────────────────────────────

    @Test
    void getSummary_reflectsActiveSubscriptions() {
        WebSocketSession s1 = mockSession("sess-1", 10L, "A", "DRIVER");
        WebSocketSession s2 = mockSession("sess-2", 11L, "B", "FLEET_OWNER");

        subscriptionManager.onSubscribed(s1, "trip.1.location",   null);
        subscriptionManager.onSubscribed(s2, "fleet.vehicles",    null);
        subscriptionManager.onSubscribed(s2, "vehicle.5.location", null);

        SubscriptionManagerService.SubscriptionSummary summary = subscriptionManager.getSummary();

        assertThat(summary.totalSubscriptions()).isEqualTo(3);
        assertThat(summary.uniqueTopics()).isEqualTo(3);
        assertThat(summary.uniqueUsers()).isEqualTo(2);
        assertThat(summary.snapshotTime()).isNotNull();
    }

    @Test
    void getSummary_mostPopularTopicsOrdered() {
        WebSocketSession s1 = mockSession("sess-1", 10L, "A", "DRIVER");
        WebSocketSession s2 = mockSession("sess-2", 11L, "B", "SHIPPER");
        WebSocketSession s3 = mockSession("sess-3", 12L, "C", "DRIVER");

        subscriptionManager.onSubscribed(s1, "fleet.vehicles", null);
        subscriptionManager.onSubscribed(s2, "fleet.vehicles", null);
        subscriptionManager.onSubscribed(s3, "trip.1.location", null);

        SubscriptionManagerService.SubscriptionSummary summary = subscriptionManager.getSummary();

        // fleet.vehicles has 2 subs, trip.1.location has 1 → fleet should be first
        assertThat(summary.mostPopularTopics().get(0)).isEqualTo("fleet.vehicles");
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private WebSocketSession mockSession(String id, Long userId, String name, String role) {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn(id);
        when(session.isOpen()).thenReturn(true);
        when(session.getAttributes()).thenReturn(Map.of(
            WebSocketAuthenticationInterceptor.USER_ID_ATTR,   userId,
            WebSocketAuthenticationInterceptor.USERNAME_ATTR,  name,
            WebSocketAuthenticationInterceptor.USER_ROLE_ATTR, role
        ));
        try {
            when(session.getUri()).thenReturn(new URI("ws://localhost/ws/location"));
        } catch (Exception ignored) {}
        return session;
    }
}
