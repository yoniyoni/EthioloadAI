package com.ethioloadai.websocket.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Phase 13: Client subscription management.
 *
 * Provides query and administrative operations on the live subscription graph:
 * which sessions are subscribed to which topics, when they subscribed, and
 * subscription health metrics.
 */
public interface SubscriptionManagerService {

    /** Return every active subscription for a user across all sessions. */
    List<SubscriptionInfo> getSubscriptionsForUser(Long userId);

    /** Return every active subscription for a specific session. */
    List<SubscriptionInfo> getSubscriptionsForSession(String sessionId);

    /** Return every session currently subscribed to a topic. */
    List<SubscriptionInfo> getSubscribersForTopic(String topic);

    /**
     * Force-unsubscribe a session from a topic (admin / cleanup).
     *
     * @return true if the subscription existed and was removed
     */
    boolean forceUnsubscribe(String sessionId, String topic);

    /**
     * Force-unsubscribe ALL sessions of a user from ALL topics (e.g. on account suspension).
     *
     * @return number of subscriptions removed
     */
    int forceUnsubscribeUser(Long userId);

    /** Return an aggregated view of all active subscriptions. */
    SubscriptionSummary getSummary();

    // ── Value objects ─────────────────────────────────────────────────────────

    record SubscriptionInfo(
        String        sessionId,
        Long          userId,
        String        username,
        String        userRole,
        String        topic,
        Object        filter,
        LocalDateTime subscribedAt,
        boolean       sessionOpen
    ) {}

    record SubscriptionSummary(
        int                   totalSubscriptions,
        int                   uniqueTopics,
        int                   uniqueUsers,
        Map<String, Integer>  subscriptionCountByTopic,
        Map<String, Integer>  subscriptionCountByRole,
        List<String>          mostPopularTopics,
        LocalDateTime         snapshotTime
    ) {}
}
