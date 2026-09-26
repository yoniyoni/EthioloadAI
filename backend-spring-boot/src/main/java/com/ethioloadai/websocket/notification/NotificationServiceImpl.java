package com.ethioloadai.websocket.notification;

import com.ethioloadai.websocket.dto.WebSocketMessage;
import com.ethioloadai.websocket.service.WebSocketSessionManager;
import com.ethioloadai.user.entity.User;
import com.ethioloadai.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Phase 18 implementation — pushes notifications through the WebSocket
 * session manager to the appropriate user or fleet topics.
 */
@Service
public class NotificationServiceImpl implements NotificationService {
    private static final Logger log = LoggerFactory.getLogger(NotificationServiceImpl.class);

    private final WebSocketSessionManager sessionManager;
    private final org.springframework.beans.factory.ObjectProvider<com.ethioloadai.notification.service.NotificationStoreService> notificationStoreProvider;
    private final UserRepository userRepository;

    @org.springframework.beans.factory.annotation.Autowired
    public NotificationServiceImpl(
            WebSocketSessionManager sessionManager,
            org.springframework.beans.factory.ObjectProvider<com.ethioloadai.notification.service.NotificationStoreService> notificationStoreProvider,
            UserRepository userRepository) {
        this.sessionManager = sessionManager;
        this.notificationStoreProvider = notificationStoreProvider;
        this.userRepository = userRepository;
    }

    public NotificationServiceImpl(WebSocketSessionManager sessionManager) {
        this.sessionManager = sessionManager;
        this.notificationStoreProvider = null;
        this.userRepository = null;
    }

    @Override
    public void notifyUser(Long userId, Notification notification) {
        log.debug("Notifying user {}: [{}] {}", userId, notification.type(), notification.title());

        WebSocketMessage message = WebSocketMessage.notification(Map.of(
            "notificationId", notification.id(),
            "type",           notification.type(),
            "title",          notification.title(),
            "message",        notification.message(),
            "priority",       notification.priority().name(),
            "data",           notification.data(),
            "timestamp",      notification.timestamp()
        ));

        sessionManager.broadcastToTopic("user." + userId + ".notifications", message);

        com.ethioloadai.notification.service.NotificationStoreService store = 
            notificationStoreProvider != null ? notificationStoreProvider.getIfAvailable() : null;
        if (store != null) {
            java.util.Map<String, Object> dataPayload = new java.util.HashMap<>(notification.data() != null ? notification.data() : java.util.Map.of());
            dataPayload.put("message", notification.message());
            dataPayload.put("title", notification.title());
            store.recordNotification(userId, notification.type(), dataPayload);
        }
    }

    @Override
    public void notifyRole(String role, Notification notification) {
        log.debug("Notifying role {}: [{}] {}", role, notification.type(), notification.title());

        WebSocketMessage message = WebSocketMessage.notification(Map.of(
            "notificationId", notification.id(),
            "type",           notification.type(),
            "title",          notification.title(),
            "message",        notification.message(),
            "priority",       notification.priority().name(),
            "data",           notification.data(),
            "timestamp",      notification.timestamp()
        ));

        // Broadcast to role-scoped topic (subscribers filter on their role)
        sessionManager.broadcastToTopic("role." + role.toLowerCase() + ".notifications", message);
        if (notificationStoreProvider != null && userRepository != null) {
            var store = notificationStoreProvider.getIfAvailable();
            if (store != null && "admin".equalsIgnoreCase(role)) {
                userRepository.findByRoleOrderByCreatedAtDesc(User.Role.ADMIN).forEach(admin -> {
                    var payload = new java.util.HashMap<String, Object>(notification.data() == null ? Map.of() : notification.data());
                    payload.put("message", notification.message());
                    payload.put("title", notification.title());
                    store.recordNotification(admin.getId(), notification.type(), payload);
                });
            }
        }
    }

    @Override
    public void notifyTripEvent(Long tripId, Long shipperId, Long driverId,
                                String eventType, String message, Map<String, Object> data) {

        log.info("Trip notification: trip={}, event={}, shipper={}, driver={}",
                 tripId, eventType, shipperId, driverId);

        Notification notification = Notification.of(
            "TRIP_EVENT",
            "Trip Update",
            message,
            tripEventPriority(eventType),
            combineData(data, Map.of("tripId", tripId, "eventType", eventType))
        );

        if (shipperId != null) notifyUser(shipperId, notification);
        if (driverId  != null) notifyUser(driverId,  notification);
    }

    @Override
    public void notifyFleetOwner(Long fleetOwnerId, Long vehicleId, String alertType,
                                 String message, Map<String, Object> data) {

        log.info("Fleet notification: owner={}, vehicle={}, alert={}", fleetOwnerId, vehicleId, alertType);

        Notification notification = Notification.of(
            "FLEET_ALERT",
            "Fleet Alert: " + alertType,
            message,
            fleetAlertPriority(alertType),
            combineData(data, Map.of("vehicleId", vehicleId, "alertType", alertType))
        );

        notifyUser(fleetOwnerId, notification);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private NotificationService.Priority tripEventPriority(String eventType) {
        return switch (eventType) {
            case "TRIP_STARTED", "TRIP_COMPLETED" -> Priority.HIGH;
            case "TRIP_CANCELLED", "TRIP_DELAYED"  -> Priority.URGENT;
            default                                 -> Priority.NORMAL;
        };
    }

    private NotificationService.Priority fleetAlertPriority(String alertType) {
        return switch (alertType) {
            case "BREAKDOWN", "ACCIDENT", "SOS"    -> Priority.URGENT;
            case "SPEEDING", "OUT_OF_ROUTE"         -> Priority.HIGH;
            default                                 -> Priority.NORMAL;
        };
    }

    private Map<String, Object> combineData(Map<String, Object> base, Map<String, Object> extra) {
        var combined = new java.util.HashMap<>(base);
        combined.putAll(extra);
        return Map.copyOf(combined);
    }
}
