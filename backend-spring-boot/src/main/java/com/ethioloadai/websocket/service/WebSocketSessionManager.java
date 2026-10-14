package com.ethioloadai.websocket.service;

import org.springframework.web.socket.WebSocketSession;

import java.util.List;
import java.util.Map;

/**
 * Service for managing WebSocket sessions, subscriptions, and real-time data distribution.
 * 
 * Handles client subscriptions to different data streams (location updates, notifications, etc.)
 * and manages the routing of real-time updates to appropriate subscribers.
 */
public interface WebSocketSessionManager {

    /**
     * Register a new WebSocket session.
     * 
     * @param session WebSocket session to register
     */
    void registerSession(WebSocketSession session);

    /**
     * Unregister a WebSocket session.
     * 
     * @param session WebSocket session to unregister
     */
    void unregisterSession(WebSocketSession session);

    /**
     * Subscribe a session to a specific topic with optional filtering.
     * 
     * @param session WebSocket session
     * @param topic topic to subscribe to (e.g., "trip.123.location", "fleet.locations")
     * @param filter optional filter criteria for the subscription
     * @return true if subscription was successful
     */
    boolean subscribe(WebSocketSession session, String topic, Object filter);

    /**
     * Unsubscribe a session from a topic.
     * 
     * @param session WebSocket session
     * @param topic topic to unsubscribe from
     * @return true if unsubscription was successful
     */
    boolean unsubscribe(WebSocketSession session, String topic);

    /**
     * Get all sessions subscribed to a specific topic.
     * 
     * @param topic the topic
     * @return list of subscribed sessions
     */
    List<WebSocketSession> getSubscribersForTopic(String topic);

    /**
     * Broadcast message to all subscribers of a topic.
     * 
     * @param topic the topic
     * @param message message to broadcast
     */
    void broadcastToTopic(String topic, Object message);

    /**
     * Send message to specific session.
     * 
     * @param sessionId session identifier
     * @param message message to send
     */
    void sendToSession(String sessionId, Object message);

    /**
     * Handle update requests from clients.
     * 
     * @param session requesting session
     * @param requestType type of update requested
     * @param requestData additional request parameters
     */
    void handleUpdateRequest(WebSocketSession session, String requestType, Map<String, Object> requestData);

    /**
     * Send initial data to a newly connected session.
     * 
     * @param session WebSocket session
     * @param endpoint the WebSocket endpoint (location, notifications, fleet)
     * @param userRole user's role for authorization
     */
    void sendInitialData(WebSocketSession session, String endpoint, String userRole);

    /**
     * Get session statistics.
     * 
     * @return session statistics
     */
    SessionStatistics getSessionStatistics();

    /**
     * Clean up inactive sessions.
     * 
     * @return number of sessions cleaned up
     */
    int cleanupInactiveSessions();

    // ── Value Objects ────────────────────────────────────────────────────────

    /**
     * WebSocket session statistics.
     */
    record SessionStatistics(
        int totalSessions,
        int activeSessions,
        int totalSubscriptions,
        Map<String, Integer> subscriptionsByTopic,
        Map<String, Integer> sessionsByRole,
        Map<String, Integer> sessionsByEndpoint
    ) {}

    /**
     * Subscription information.
     */
    record Subscription(
        String sessionId,
        String topic,
        Object filter,
        java.time.LocalDateTime subscribedAt
    ) {}

    /**
     * Session information.
     */
    record SessionInfo(
        String sessionId,
        Long userId,
        String username,
        String userRole,
        String endpoint,
        java.time.LocalDateTime connectedAt,
        boolean isActive,
        List<String> subscriptions
    ) {}
}