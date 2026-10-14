package com.ethioloadai.websocket.security;

import java.time.LocalDateTime;

/**
 * Service for WebSocket rate limiting and throttling.
 * 
 * Provides rate limiting capabilities for WebSocket connections, messages,
 * and subscriptions to prevent abuse and ensure fair resource usage.
 */
public interface WebSocketRateLimitService {

    /**
     * Check if a user can establish a new WebSocket connection.
     * 
     * @param userId user identifier
     * @param ipAddress client IP address
     * @return true if connection is allowed
     */
    boolean canConnect(Long userId, String ipAddress);

    /**
     * Check if a user can send a message.
     * 
     * @param userId user identifier
     * @param sessionId WebSocket session identifier
     * @return true if message is allowed
     */
    boolean canSendMessage(Long userId, String sessionId);

    /**
     * Check if a user can subscribe to a topic.
     * 
     * @param userId user identifier
     * @param sessionId WebSocket session identifier
     * @param topic topic to subscribe to
     * @return true if subscription is allowed
     */
    boolean canSubscribe(Long userId, String sessionId, String topic);

    /**
     * Check if a location update can be sent for a trip.
     * 
     * @param tripId trip identifier
     * @param vehicleId vehicle identifier
     * @return true if location update is allowed
     */
    boolean canSendLocationUpdate(Long tripId, Long vehicleId);

    /**
     * Atomically enforce connection limits and register an admitted connection.
     */
    boolean tryAcquireConnection(Long userId, String ipAddress, String connectionId);

    /**
     * Release a previously acquired connection. Releasing an unknown or already
     * released connection has no effect.
     */
    boolean releaseConnection(String connectionId);

    /**
     * Record a message sent.
     * 
     * @param userId user identifier
     * @param sessionId WebSocket session identifier
     * @param messageType type of message sent
     */
    void recordMessage(Long userId, String sessionId, String messageType);

    /**
     * Record a subscription attempt.
     * 
     * @param userId user identifier
     * @param sessionId WebSocket session identifier
     * @param topic topic subscribed to
     * @param success whether subscription was successful
     */
    void recordSubscription(Long userId, String sessionId, String topic, boolean success);

    /**
     * Record a location update.
     * 
     * @param tripId trip identifier
     * @param vehicleId vehicle identifier
     * @param timestamp update timestamp
     */
    void recordLocationUpdate(Long tripId, Long vehicleId, LocalDateTime timestamp);

    /**
     * Clean up expired rate limit entries.
     * 
     * @return number of entries cleaned up
     */
    int cleanupExpiredEntries();

    /**
     * Get rate limit statistics for monitoring.
     * 
     * @return rate limit statistics
     */
    RateLimitStatistics getRateLimitStatistics();

    /**
     * Reset rate limits for a user (admin operation).
     * 
     * @param userId user identifier
     */
    void resetUserRateLimits(Long userId);

    /**
     * Check if a user is currently rate limited.
     * 
     * @param userId user identifier
     * @return rate limit status
     */
    RateLimitStatus getUserRateLimitStatus(Long userId);

    // ── Value Objects ────────────────────────────────────────────────────────

    /**
     * Rate limit statistics.
     */
    record RateLimitStatistics(
        int totalConnections,
        int activeConnections,
        int rateLimitedUsers,
        int messagesPerMinute,
        int subscriptionsPerMinute,
        int locationUpdatesPerMinute,
        LocalDateTime lastCleanup
    ) {}

    /**
     * Rate limit status for a user.
     */
    record RateLimitStatus(
        Long userId,
        boolean isRateLimited,
        String reason,
        LocalDateTime rateLimitExpiry,
        int connectionCount,
        int messageCount,
        int subscriptionCount,
        LocalDateTime windowStart
    ) {}

    /**
     * Rate limit violation details.
     */
    record RateLimitViolation(
        Long userId,
        String sessionId,
        String violationType,
        String details,
        LocalDateTime timestamp,
        String action
    ) {}
}