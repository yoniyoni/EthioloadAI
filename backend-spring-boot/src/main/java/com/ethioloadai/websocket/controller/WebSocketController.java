package com.ethioloadai.websocket.controller;

import com.ethioloadai.websocket.service.WebSocketSessionManager;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST controller for WebSocket management and monitoring.
 * 
 * Provides endpoints for testing WebSocket functionality, monitoring connections,
 * and managing real-time data broadcasting.
 */
@RestController
@RequestMapping("/websocket")
@RequiredArgsConstructor
public class WebSocketController {
    private static final Logger log = LoggerFactory.getLogger(WebSocketController.class);

    private final WebSocketSessionManager sessionManager;

    /**
     * Get WebSocket session statistics.
     */
    @GetMapping("/stats")
    @PreAuthorize("hasAnyRole('ADMIN', 'FLEET_OWNER')")
    public ResponseEntity<WebSocketSessionManager.SessionStatistics> getSessionStatistics() {
        log.debug("Retrieving WebSocket session statistics");
        
        WebSocketSessionManager.SessionStatistics stats = sessionManager.getSessionStatistics();
        return ResponseEntity.ok(stats);
    }

    /**
     * Broadcast test message to a specific topic.
     * Useful for testing WebSocket functionality.
     */
    @PostMapping("/broadcast/{topic}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> broadcastMessage(
            @PathVariable String topic,
            @RequestBody Map<String, Object> message) {
        
        log.info("Broadcasting test message to topic '{}': {}", topic, message);
        
        try {
            sessionManager.broadcastToTopic(topic, message);
            
            return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "Broadcast sent to topic: " + topic,
                "timestamp", java.time.LocalDateTime.now()
            ));
            
        } catch (Exception e) {
            log.error("Failed to broadcast message to topic '{}': {}", topic, e.getMessage(), e);
            
            return ResponseEntity.badRequest().body(Map.of(
                "status", "error",
                "message", "Failed to broadcast message: " + e.getMessage(),
                "timestamp", java.time.LocalDateTime.now()
            ));
        }
    }

    /**
     * Send test message to a specific session.
     */
    @PostMapping("/session/{sessionId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> sendToSession(
            @PathVariable String sessionId,
            @RequestBody Map<String, Object> message) {
        
        log.info("Sending test message to session '{}': {}", sessionId, message);
        
        try {
            sessionManager.sendToSession(sessionId, message);
            
            return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "Message sent to session: " + sessionId,
                "timestamp", java.time.LocalDateTime.now()
            ));
            
        } catch (Exception e) {
            log.error("Failed to send message to session '{}': {}", sessionId, e.getMessage(), e);
            
            return ResponseEntity.badRequest().body(Map.of(
                "status", "error",
                "message", "Failed to send message: " + e.getMessage(),
                "timestamp", java.time.LocalDateTime.now()
            ));
        }
    }

    /**
     * Clean up inactive WebSocket sessions.
     */
    @PostMapping("/cleanup")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> cleanupSessions() {
        log.info("Cleaning up inactive WebSocket sessions");
        
        try {
            int cleanedUp = sessionManager.cleanupInactiveSessions();
            
            return ResponseEntity.ok(Map.of(
                "status", "success",
                "cleanedUp", cleanedUp,
                "message", "Cleaned up " + cleanedUp + " inactive sessions",
                "timestamp", java.time.LocalDateTime.now()
            ));
            
        } catch (Exception e) {
            log.error("Failed to cleanup sessions: {}", e.getMessage(), e);
            
            return ResponseEntity.badRequest().body(Map.of(
                "status", "error",
                "message", "Failed to cleanup sessions: " + e.getMessage(),
                "timestamp", java.time.LocalDateTime.now()
            ));
        }
    }

    /**
     * WebSocket health check endpoint.
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> healthCheck() {
        try {
            WebSocketSessionManager.SessionStatistics stats = sessionManager.getSessionStatistics();
            
            return ResponseEntity.ok(Map.of(
                "status", "healthy",
                "activeSessions", stats.activeSessions(),
                "totalSubscriptions", stats.totalSubscriptions(),
                "timestamp", java.time.LocalDateTime.now()
            ));
            
        } catch (Exception e) {
            log.error("WebSocket health check failed: {}", e.getMessage(), e);
            
            return ResponseEntity.status(503).body(Map.of(
                "status", "unhealthy",
                "error", e.getMessage(),
                "timestamp", java.time.LocalDateTime.now()
            ));
        }
    }

    /**
     * Test WebSocket connectivity.
     * Returns WebSocket endpoint URLs and connection instructions.
     */
    @GetMapping("/test")
    public ResponseEntity<Map<String, Object>> getTestInfo() {
        return ResponseEntity.ok(Map.of(
            "status", "ready",
            "endpoints", Map.of(
                "location", "/ws/location",
                "notifications", "/ws/notifications",
                "fleet", "/ws/fleet"
            ),
            "authentication", Map.of(
                "method", "JWT Token",
                "queryParam", "?token=<jwt_token>",
                "header", "Authorization: Bearer <jwt_token>"
            ),
            "sampleTopics", Map.of(
                "location", "trip.{tripId}.location",
                "notifications", "user.{userId}.notifications",
                "fleet", "fleet.vehicles"
            ),
            "messageFormat", Map.of(
                "subscribe", Map.of(
                    "type", "SUBSCRIBE",
                    "data", Map.of("topic", "trip.123.location")
                ),
                "unsubscribe", Map.of(
                    "type", "UNSUBSCRIBE",
                    "data", Map.of("topic", "trip.123.location")
                ),
                "ping", Map.of(
                    "type", "PING",
                    "data", Map.of()
                )
            ),
            "timestamp", java.time.LocalDateTime.now()
        ));
    }
}