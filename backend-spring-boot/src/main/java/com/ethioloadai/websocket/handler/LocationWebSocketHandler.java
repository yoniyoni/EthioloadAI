package com.ethioloadai.websocket.handler;

import com.ethioloadai.websocket.dto.WebSocketMessage;
import com.ethioloadai.websocket.interceptor.WebSocketAuthenticationInterceptor;
import com.ethioloadai.websocket.service.WebSocketSessionManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket handler for real-time GPS location updates and notifications.
 * 
 * Manages WebSocket connections, message routing, and session lifecycle
 * for real-time tracking and fleet management features.
 */
@Component
@RequiredArgsConstructor
public class LocationWebSocketHandler extends AbstractWebSocketHandler {
    private static final Logger log = LoggerFactory.getLogger(LocationWebSocketHandler.class);

    private final WebSocketSessionManager sessionManager;
    private final ObjectMapper objectMapper;

    // Active WebSocket sessions mapped by session ID
    private final Map<String, WebSocketSession> activeSessions = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String sessionId = session.getId();
        Long userId = (Long) session.getAttributes().get(WebSocketAuthenticationInterceptor.USER_ID_ATTR);
        String username = (String) session.getAttributes().get(WebSocketAuthenticationInterceptor.USERNAME_ATTR);
        String userRole = (String) session.getAttributes().get(WebSocketAuthenticationInterceptor.USER_ROLE_ATTR);

        log.info("WebSocket connection established: sessionId={}, userId={}, username={}, role={}", 
                sessionId, userId, username, userRole);

        // Store session
        activeSessions.put(sessionId, session);

        // Register session with session manager for subscription handling
        sessionManager.registerSession(session);

        // Send welcome message
        WebSocketMessage welcomeMessage = WebSocketMessage.system(
            "CONNECTION_ESTABLISHED",
            Map.of(
                "sessionId", sessionId,
                "timestamp", LocalDateTime.now(),
                "message", "WebSocket connection established successfully"
            )
        );
        
        sendMessage(session, welcomeMessage);

        // Send initial data based on user role and endpoint
        sendInitialData(session);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String sessionId = session.getId();
        Long userId = (Long) session.getAttributes().get(WebSocketAuthenticationInterceptor.USER_ID_ATTR);

        log.debug("Received text message from sessionId={}, userId={}", sessionId, userId);

        try {
            handleIncomingTextMessage(session, message);
        } catch (Exception e) {
            log.error("Error handling WebSocket message from sessionId={}: {}", sessionId, e.getMessage(), e);
            sendMessage(session, WebSocketMessage.error("Message processing failed"));
        }
    }

    @Override
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) {
        Long userId = (Long) session.getAttributes().get(WebSocketAuthenticationInterceptor.USER_ID_ATTR);
        log.debug("Received binary message from userId={}, size={} bytes", userId, message.getPayloadLength());
        sendMessage(session, WebSocketMessage.error("Binary messages not supported"));
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        String sessionId = session.getId();
        Long userId = (Long) session.getAttributes().get(WebSocketAuthenticationInterceptor.USER_ID_ATTR);

        log.error("WebSocket transport error for sessionId={}, userId={}: {}", 
                 sessionId, userId, exception.getMessage(), exception);

        // Clean up session
        sessionManager.unregisterSession(session);
        activeSessions.remove(sessionId);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) throws Exception {
        String sessionId = session.getId();
        Long userId = (Long) session.getAttributes().get(WebSocketAuthenticationInterceptor.USER_ID_ATTR);

        log.info("WebSocket connection closed: sessionId={}, userId={}, status={}", 
                sessionId, userId, closeStatus);

        // Clean up session
        sessionManager.unregisterSession(session);
        activeSessions.remove(sessionId);
    }

    @Override
    public boolean supportsPartialMessages() {
        return false; // We don't support partial messages for simplicity
    }

    /**
     * Handle incoming text messages.
     * 
     * @param session WebSocket session
     * @param message text message
     */
    private void handleIncomingTextMessage(WebSocketSession session, TextMessage message) throws IOException {
        String payload = message.getPayload();
        Long userId = (Long) session.getAttributes().get(WebSocketAuthenticationInterceptor.USER_ID_ATTR);

        log.debug("Processing text message from userId={}: {}", userId, payload);

        try {
            // Parse JSON message
            @SuppressWarnings("unchecked")
            Map<String, Object> messageData = objectMapper.readValue(payload, Map.class);
            
            String messageType = (String) messageData.get("type");
            Object data = messageData.get("data");

            // Route message based on type
            switch (messageType) {
                case "SUBSCRIBE" -> handleSubscriptionMessage(session, data);
                case "UNSUBSCRIBE" -> handleUnsubscriptionMessage(session, data);
                case "PING" -> handlePingMessage(session);
                case "REQUEST_UPDATE" -> handleUpdateRequest(session, data);
                default -> {
                    log.warn("Unknown message type from userId={}: {}", userId, messageType);
                    sendErrorMessage(session, "Unknown message type: " + messageType);
                }
            }

        } catch (Exception e) {
            log.error("Failed to parse text message from userId={}: {}", userId, e.getMessage(), e);
            sendErrorMessage(session, "Invalid message format");
        }
    }

    /**
     * Handle incoming binary messages.
     * 
     * @param session WebSocket session
     * @param message binary message
     */
    private void handleIncomingBinaryMessage(WebSocketSession session, BinaryMessage message) {
        Long userId = (Long) session.getAttributes().get(WebSocketAuthenticationInterceptor.USER_ID_ATTR);
        log.debug("Received binary message from userId={}, size={} bytes", userId, message.getPayloadLength());
        
        // For now, we don't support binary messages
        sendErrorMessage(session, "Binary messages not supported");
    }

    /**
     * Handle subscription requests.
     */
    private void handleSubscriptionMessage(WebSocketSession session, Object data) {
        Long userId = (Long) session.getAttributes().get(WebSocketAuthenticationInterceptor.USER_ID_ATTR);
        
        @SuppressWarnings("unchecked")
        Map<String, Object> subscriptionData = (Map<String, Object>) data;
        
        String topic = (String) subscriptionData.get("topic");
        Object filter = subscriptionData.get("filter");

        log.info("User {} subscribing to topic: {} with filter: {}", userId, topic, filter);

        // Delegate to session manager
        boolean success = sessionManager.subscribe(session, topic, filter);
        
        if (success) {
            sendAcknowledgmentMessage(session, "SUBSCRIBED", Map.of("topic", topic));
        } else {
            sendErrorMessage(session, "Subscription failed for topic: " + topic);
        }
    }

    /**
     * Handle unsubscription requests.
     */
    private void handleUnsubscriptionMessage(WebSocketSession session, Object data) {
        Long userId = (Long) session.getAttributes().get(WebSocketAuthenticationInterceptor.USER_ID_ATTR);
        
        @SuppressWarnings("unchecked")
        Map<String, Object> unsubscriptionData = (Map<String, Object>) data;
        
        String topic = (String) unsubscriptionData.get("topic");

        log.info("User {} unsubscribing from topic: {}", userId, topic);

        // Delegate to session manager
        boolean success = sessionManager.unsubscribe(session, topic);
        
        if (success) {
            sendAcknowledgmentMessage(session, "UNSUBSCRIBED", Map.of("topic", topic));
        } else {
            sendErrorMessage(session, "Unsubscription failed for topic: " + topic);
        }
    }

    /**
     * Handle ping messages for connection health checks.
     */
    private void handlePingMessage(WebSocketSession session) {
        sendAcknowledgmentMessage(session, "PONG", Map.of("timestamp", LocalDateTime.now()));
    }

    /**
     * Handle requests for data updates.
     */
    private void handleUpdateRequest(WebSocketSession session, Object data) {
        Long userId = (Long) session.getAttributes().get(WebSocketAuthenticationInterceptor.USER_ID_ATTR);
        
        @SuppressWarnings("unchecked")
        Map<String, Object> requestData = (Map<String, Object>) data;
        
        String requestType = (String) requestData.get("requestType");

        log.debug("User {} requesting update: {}", userId, requestType);

        // Delegate to session manager for data fetching
        sessionManager.handleUpdateRequest(session, requestType, requestData);
    }

    /**
     * Send initial data to newly connected session based on endpoint and user role.
     */
    private void sendInitialData(WebSocketSession session) {
        String userRole = (String) session.getAttributes().get(WebSocketAuthenticationInterceptor.USER_ROLE_ATTR);
        String endpoint = extractEndpoint(session);

        // Delegate to session manager
        sessionManager.sendInitialData(session, endpoint, userRole);
    }

    /**
     * Send WebSocket message to session.
     */
    private void sendMessage(WebSocketSession session, WebSocketMessage message) {
        if (session.isOpen()) {
            try {
                String messageJson = objectMapper.writeValueAsString(message);
                session.sendMessage(new TextMessage(messageJson));
            } catch (IOException e) {
                log.error("Failed to send message to sessionId={}: {}", session.getId(), e.getMessage(), e);
            }
        }
    }

    /**
     * Send error message to session.
     */
    private void sendErrorMessage(WebSocketSession session, String error) {
        WebSocketMessage errorMessage = WebSocketMessage.error(error);
        sendMessage(session, errorMessage);
    }

    /**
     * Send acknowledgment message to session.
     */
    private void sendAcknowledgmentMessage(WebSocketSession session, String type, Map<String, Object> data) {
        WebSocketMessage ackMessage = WebSocketMessage.ack(type, data);
        sendMessage(session, ackMessage);
    }

    /**
     * Extract endpoint name from WebSocket URI.
     */
    private String extractEndpoint(WebSocketSession session) {
        String path = session.getUri().getPath();
        if (path.contains("/location")) return "location";
        if (path.contains("/notifications")) return "notifications";
        if (path.contains("/fleet")) return "fleet";
        return "unknown";
    }

    // ── Public API for External Services ────────────────────────────────────

    /**
     * Broadcast message to all active sessions.
     * Called by external services for real-time updates.
     */
    public void broadcastMessage(WebSocketMessage message) {
        log.debug("Broadcasting message to {} active sessions", activeSessions.size());
        
        activeSessions.values().parallelStream()
            .filter(WebSocketSession::isOpen)
            .forEach(session -> sendMessage(session, message));
    }

    /**
     * Send message to specific session.
     */
    public void sendMessageToSession(String sessionId, WebSocketMessage message) {
        WebSocketSession session = activeSessions.get(sessionId);
        if (session != null && session.isOpen()) {
            sendMessage(session, message);
        } else {
            log.warn("Attempted to send message to inactive session: {}", sessionId);
        }
    }

    /**
     * Get count of active WebSocket sessions.
     */
    public int getActiveSessionCount() {
        return (int) activeSessions.values().stream()
            .filter(WebSocketSession::isOpen)
            .count();
    }
}