package com.ethioloadai.websocket.notification;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Phase 18: Real-time notifications over WebSocket.
 *
 * Sends structured push notifications to specific users or roles
 * via the existing WebSocket session manager.
 */
public interface NotificationService {

    /** Send to a single user. */
    void notifyUser(Long userId, Notification notification);

    /** Send to every user with a given role. */
    void notifyRole(String role, Notification notification);

    /** Send a trip event notification (arrival, departure, status change). */
    void notifyTripEvent(Long tripId, Long shipperId, Long driverId,
                         String eventType, String message, Map<String, Object> data);

    /** Send an alert to fleet owner about a vehicle. */
    void notifyFleetOwner(Long fleetOwnerId, Long vehicleId, String alertType,
                          String message, Map<String, Object> data);

    // ── Value objects ─────────────────────────────────────────────────────────

    enum Priority { LOW, NORMAL, HIGH, URGENT }

    record Notification(
        String              id,
        String              type,
        String              title,
        String              message,
        Priority            priority,
        Map<String, Object> data,
        LocalDateTime       timestamp
    ) {
        public static Notification of(String type, String title, String message,
                                      Priority priority, Map<String, Object> data) {
            return new Notification(
                java.util.UUID.randomUUID().toString(),
                type, title, message, priority, data, LocalDateTime.now()
            );
        }

        public static Notification info(String type, String title, String message) {
            return of(type, title, message, Priority.NORMAL, Map.of());
        }

        public static Notification urgent(String type, String title, String message,
                                          Map<String, Object> data) {
            return of(type, title, message, Priority.URGENT, data);
        }
    }
}
