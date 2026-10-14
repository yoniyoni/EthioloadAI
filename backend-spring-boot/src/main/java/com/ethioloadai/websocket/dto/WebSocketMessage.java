package com.ethioloadai.websocket.dto;

/**
 * Standard WebSocket message format for real-time GPS tracking and notifications.
 * 
 * Provides consistent message structure across all WebSocket endpoints
 * for location updates, trip notifications, and fleet management.
 */
public record WebSocketMessage(
    String type,
    Object data,
    String source
) {
    
    /**
     * Create a system message.
     */
    public static WebSocketMessage system(String type, Object data) {
        return new WebSocketMessage(type, data, "system");
    }
    
    /**
     * Create a location update message.
     */
    public static WebSocketMessage locationUpdate(Object data) {
        return new WebSocketMessage("LOCATION_UPDATE", data, "tracking");
    }
    
    /**
     * Create a notification message.
     */
    public static WebSocketMessage notification(Object data) {
        return new WebSocketMessage("NOTIFICATION", data, "system");
    }
    
    /**
     * Create an error message.
     */
    public static WebSocketMessage error(String message) {
        return new WebSocketMessage("ERROR", 
            java.util.Map.of("error", message, "timestamp", java.time.LocalDateTime.now()), 
            "system");
    }
    
    /**
     * Create an acknowledgment message.
     */
    public static WebSocketMessage ack(String type, Object data) {
        return new WebSocketMessage(type, data, "system");
    }
}