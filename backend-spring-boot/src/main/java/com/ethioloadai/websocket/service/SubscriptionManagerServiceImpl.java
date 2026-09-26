package com.ethioloadai.websocket.service;

import com.ethioloadai.websocket.interceptor.WebSocketAuthenticationInterceptor;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.WebSocketSession;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Phase 13 implementation of subscription management.
 *
 * Keeps a secondary index — subscriptionMeta — that stores extra metadata
 * (subscribe time, filter) for each (sessionId, topic) pair,
 * layered on top of the session-manager's primary subscription maps.
 */
@Service
@RequiredArgsConstructor
public class SubscriptionManagerServiceImpl implements SubscriptionManagerService {
    private static final Logger log = LoggerFactory.getLogger(SubscriptionManagerServiceImpl.class);

    private final WebSocketSessionManager sessionManager;

    /**
     * Metadata for each active subscription: key = "sessionId::topic".
     */
    private final ConcurrentHashMap<String, SubscriptionMeta> subscriptionMeta =
        new ConcurrentHashMap<>();

    // ── Registration hooks (called by WebSocketSessionManagerImpl) ─────────────

    /** Called when a subscription is created. */
    public void onSubscribed(WebSocketSession session, String topic, Object filter) {
        String key = metaKey(session.getId(), topic);
        subscriptionMeta.put(key, new SubscriptionMeta(session, topic, filter, LocalDateTime.now()));
    }

    /** Called when a subscription is removed. */
    public void onUnsubscribed(String sessionId, String topic) {
        subscriptionMeta.remove(metaKey(sessionId, topic));
    }

    /** Called when a session is fully disconnected. */
    public void onSessionClosed(String sessionId) {
        subscriptionMeta.keySet().removeIf(k -> k.startsWith(sessionId + "::"));
    }

    // ── Query API ─────────────────────────────────────────────────────────────

    @Override
    public List<SubscriptionInfo> getSubscriptionsForUser(Long userId) {
        return subscriptionMeta.values().stream()
            .filter(m -> userId.equals(m.userId()))
            .map(this::toInfo)
            .sorted(Comparator.comparing(SubscriptionInfo::subscribedAt))
            .collect(Collectors.toList());
    }

    @Override
    public List<SubscriptionInfo> getSubscriptionsForSession(String sessionId) {
        return subscriptionMeta.values().stream()
            .filter(m -> sessionId.equals(m.sessionId()))
            .map(this::toInfo)
            .sorted(Comparator.comparing(SubscriptionInfo::topic))
            .collect(Collectors.toList());
    }

    @Override
    public List<SubscriptionInfo> getSubscribersForTopic(String topic) {
        return subscriptionMeta.values().stream()
            .filter(m -> topic.equals(m.topic()))
            .map(this::toInfo)
            .sorted(Comparator.comparing(SubscriptionInfo::userId,
                                         Comparator.nullsLast(Long::compareTo)))
            .collect(Collectors.toList());
    }

    @Override
    public boolean forceUnsubscribe(String sessionId, String topic) {
        String key = metaKey(sessionId, topic);
        if (!subscriptionMeta.containsKey(key)) return false;

        // Remove from our metadata index first
        subscriptionMeta.remove(key);

        // Best-effort: also delegate to session manager to keep its maps consistent
        List<WebSocketSession> sessions = sessionManager.getSubscribersForTopic(topic);
        sessions.stream()
            .filter(s -> s.getId().equals(sessionId))
            .findFirst()
            .ifPresent(s -> {
                sessionManager.unsubscribe(s, topic);
                log.info("Force-unsubscribed session {} from topic {}", sessionId, topic);
            });

        return true; // metadata was removed — subscription is gone
    }

    @Override
    public int forceUnsubscribeUser(Long userId) {
        List<SubscriptionInfo> subs = getSubscriptionsForUser(userId);
        int removed = 0;
        for (SubscriptionInfo sub : subs) {
            if (forceUnsubscribe(sub.sessionId(), sub.topic())) removed++;
        }
        log.info("Force-unsubscribed user {} from {} subscriptions", userId, removed);
        return removed;
    }

    @Override
    public SubscriptionSummary getSummary() {
        Map<String, Integer> byTopic = subscriptionMeta.values().stream()
            .collect(Collectors.groupingBy(SubscriptionMeta::topic,
                     Collectors.collectingAndThen(Collectors.counting(), Math::toIntExact)));

        Map<String, Integer> byRole = subscriptionMeta.values().stream()
            .filter(m -> m.userRole() != null)
            .collect(Collectors.groupingBy(SubscriptionMeta::userRole,
                     Collectors.collectingAndThen(Collectors.counting(), Math::toIntExact)));

        List<String> popular = byTopic.entrySet().stream()
            .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
            .limit(10)
            .map(Map.Entry::getKey)
            .collect(Collectors.toList());

        long uniqueUsers = subscriptionMeta.values().stream()
            .map(SubscriptionMeta::userId).filter(Objects::nonNull).distinct().count();

        return new SubscriptionSummary(
            subscriptionMeta.size(),
            (int) subscriptionMeta.values().stream().map(SubscriptionMeta::topic).distinct().count(),
            (int) uniqueUsers,
            byTopic,
            byRole,
            popular,
            LocalDateTime.now()
        );
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static String metaKey(String sessionId, String topic) {
        return sessionId + "::" + topic;
    }

    private SubscriptionInfo toInfo(SubscriptionMeta m) {
        return new SubscriptionInfo(
            m.sessionId(), m.userId(), m.username(), m.userRole(),
            m.topic(), m.filter(), m.subscribedAt(), m.sessionOpen()
        );
    }

    // ── Internal meta record ──────────────────────────────────────────────────

    private record SubscriptionMeta(
        String        sessionId,
        Long          userId,
        String        username,
        String        userRole,
        String        topic,
        Object        filter,
        LocalDateTime subscribedAt,
        boolean       sessionOpen
    ) {
        SubscriptionMeta(WebSocketSession session, String topic, Object filter, LocalDateTime subscribedAt) {
            this(
                session.getId(),
                (Long)   session.getAttributes().get(WebSocketAuthenticationInterceptor.USER_ID_ATTR),
                (String) session.getAttributes().get(WebSocketAuthenticationInterceptor.USERNAME_ATTR),
                (String) session.getAttributes().get(WebSocketAuthenticationInterceptor.USER_ROLE_ATTR),
                topic,
                filter,
                subscribedAt,
                session.isOpen()
            );
        }
    }
}
