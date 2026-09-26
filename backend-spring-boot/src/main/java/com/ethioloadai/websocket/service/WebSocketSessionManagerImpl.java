package com.ethioloadai.websocket.service;

import com.ethioloadai.user.entity.User;
import com.ethioloadai.user.repository.UserRepository;
import com.ethioloadai.websocket.dto.WebSocketMessage;
import com.ethioloadai.websocket.interceptor.WebSocketAuthenticationInterceptor;
import com.ethioloadai.websocket.security.WebSocketAuditService;
import com.ethioloadai.websocket.security.WebSocketAuthorizationService;
import com.ethioloadai.websocket.security.WebSocketRateLimitService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Implementation of WebSocket session management with topic-based subscriptions.
 * 
 * Manages real-time data distribution to subscribed clients based on topics
 * like vehicle locations, trip notifications, and fleet management updates.
 */
@Service
@RequiredArgsConstructor
public class WebSocketSessionManagerImpl implements WebSocketSessionManager {
    private static final Logger log = LoggerFactory.getLogger(WebSocketSessionManagerImpl.class);

    private final ObjectMapper objectMapper;
    private final WebSocketRateLimitService rateLimitService;
    private final WebSocketAuthorizationService authorizationService;
    private final WebSocketAuditService auditService;
    private final UserRepository userRepository;

    // Active WebSocket sessions
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    
    // Session metadata
    private final Map<String, SessionInfo> sessionInfos = new ConcurrentHashMap<>();
    
    // Topic subscriptions: topic -> set of session IDs
    private final Map<String, Set<String>> topicSubscriptions = new ConcurrentHashMap<>();
    
    // Session subscriptions: session ID -> set of topics
    private final Map<String, Set<String>> sessionSubscriptions = new ConcurrentHashMap<>();
    
    // Subscription filters: session ID + topic -> filter criteria
    private final Map<String, Object> subscriptionFilters = new ConcurrentHashMap<>();

    @Override
    public void registerSession(WebSocketSession session) {
        String sessionId = session.getId();
        Long userId = (Long) session.getAttributes().get(WebSocketAuthenticationInterceptor.USER_ID_ATTR);
        String username = (String) session.getAttributes().get(WebSocketAuthenticationInterceptor.USERNAME_ATTR);
        String userRole = (String) session.getAttributes().get(WebSocketAuthenticationInterceptor.USER_ROLE_ATTR);
        String endpoint = extractEndpoint(session);

        log.info("Registering WebSocket session: sessionId={}, userId={}, endpoint={}", 
                sessionId, userId, endpoint);

        // Store session
        sessions.put(sessionId, session);
        
        // Create session info
        SessionInfo sessionInfo = new SessionInfo(
            sessionId, userId, username, userRole, endpoint,
            LocalDateTime.now(), true, new ArrayList<>()
        );
        sessionInfos.put(sessionId, sessionInfo);
        
        // Initialize subscription tracking
        sessionSubscriptions.put(sessionId, ConcurrentHashMap.newKeySet());

        log.debug("WebSocket session registered: {}", sessionInfo);
    }

    @Override
    public void unregisterSession(WebSocketSession session) {
        String sessionId = session.getId();
        
        log.info("Unregistering WebSocket session: {}", sessionId);

        // Remove all subscriptions for this session
        Set<String> topics = sessionSubscriptions.get(sessionId);
        if (topics != null) {
            topics.forEach(topic -> {
                Set<String> subscribers = topicSubscriptions.get(topic);
                if (subscribers != null) {
                    subscribers.remove(sessionId);
                    if (subscribers.isEmpty()) {
                        topicSubscriptions.remove(topic);
                    }
                }
                // Remove subscription filters
                subscriptionFilters.remove(sessionId + ":" + topic);
            });
        }

        // Clean up session data
        sessions.remove(sessionId);
        sessionInfos.remove(sessionId);
        sessionSubscriptions.remove(sessionId);

        log.debug("WebSocket session unregistered: {}", sessionId);
    }

    @Override
    public boolean subscribe(WebSocketSession session, String topic, Object filter) {
        String sessionId = session.getId();
        Long userId = (Long) session.getAttributes().get(WebSocketAuthenticationInterceptor.USER_ID_ATTR);
        String userRole = (String) session.getAttributes().get(WebSocketAuthenticationInterceptor.USER_ROLE_ATTR);

        log.info("User {} subscribing to topic '{}' with filter: {}", userId, topic, filter);

        try {
            // Rate limiting check
            if (!rateLimitService.canSubscribe(userId, sessionId, topic)) {
                log.warn("Subscription denied for user {} to topic '{}': Rate limit exceeded", userId, topic);
                auditSubscriptionFailure(userId, sessionId, topic, "RATE_LIMIT_EXCEEDED", filter);
                return false;
            }

            // Authorization check
            User user = getUserFromSession(session);
            if (user != null) {
                WebSocketAuthorizationService.AuthorizationResult authResult = 
                    authorizationService.canSubscribeToTopic(user, topic, filter);
                
                if (!authResult.allowed()) {
                    log.warn("Subscription denied for user {} to topic '{}': {}", userId, topic, authResult.reason());
                    auditSubscriptionFailure(userId, sessionId, topic, authResult.denialCode(), filter);
                    return false;
                }
                
                // Audit authorization success
                auditAuthorizationEvent(userId, sessionId, "subscribe", topic, true, null);
            }

            // Add session to topic subscribers
            topicSubscriptions.computeIfAbsent(topic, k -> ConcurrentHashMap.newKeySet()).add(sessionId);
            
            // Add topic to session subscriptions
            sessionSubscriptions.computeIfAbsent(sessionId, k -> ConcurrentHashMap.newKeySet()).add(topic);
            
            // Store filter if provided
            if (filter != null) {
                subscriptionFilters.put(sessionId + ":" + topic, filter);
            }

            // Record for rate limiting
            rateLimitService.recordSubscription(userId, sessionId, topic, true);
            
            // Audit successful subscription
            auditSubscriptionSuccess(userId, sessionId, topic, "SUBSCRIBE", filter);

            log.info("Subscription successful: sessionId={}, topic={}, totalSubscribers={}", 
                    sessionId, topic, topicSubscriptions.get(topic).size());

            return true;

        } catch (Exception e) {
            log.error("Failed to subscribe sessionId={} to topic '{}': {}", sessionId, topic, e.getMessage(), e);
            auditSubscriptionFailure(userId, sessionId, topic, "SYSTEM_ERROR", filter);
            return false;
        }
    }

    @Override
    public boolean unsubscribe(WebSocketSession session, String topic) {
        String sessionId = session.getId();
        Long userId = (Long) session.getAttributes().get(WebSocketAuthenticationInterceptor.USER_ID_ATTR);

        log.info("User {} unsubscribing from topic '{}'", userId, topic);

        try {
            // Remove session from topic subscribers
            Set<String> subscribers = topicSubscriptions.get(topic);
            if (subscribers != null) {
                subscribers.remove(sessionId);
                if (subscribers.isEmpty()) {
                    topicSubscriptions.remove(topic);
                }
            }

            // Remove topic from session subscriptions
            Set<String> topics = sessionSubscriptions.get(sessionId);
            if (topics != null) {
                topics.remove(topic);
            }

            // Remove subscription filter
            subscriptionFilters.remove(sessionId + ":" + topic);

            log.info("Unsubscription successful: sessionId={}, topic={}", sessionId, topic);
            return true;

        } catch (Exception e) {
            log.error("Failed to unsubscribe sessionId={} from topic '{}': {}", sessionId, topic, e.getMessage(), e);
            return false;
        }
    }

    @Override
    public List<WebSocketSession> getSubscribersForTopic(String topic) {
        Set<String> sessionIds = topicSubscriptions.get(topic);
        if (sessionIds == null || sessionIds.isEmpty()) {
            return List.of();
        }

        return sessionIds.stream()
            .map(sessions::get)
            .filter(Objects::nonNull)
            .filter(WebSocketSession::isOpen)
            .collect(Collectors.toList());
    }

    @Override
    public void broadcastToTopic(String topic, Object message) {
        List<WebSocketSession> subscribers = getSubscribersForTopic(topic);
        
        if (subscribers.isEmpty()) {
            log.debug("No subscribers for topic '{}', skipping broadcast", topic);
            return;
        }

        log.debug("Broadcasting to topic '{}': {} subscribers", topic, subscribers.size());

        subscribers.parallelStream().forEach(session -> {
            try {
                // Apply subscription filter if present
                Object filteredMessage = applySubscriptionFilter(session, topic, message);
                if (filteredMessage != null) {
                    sendMessageToSession(session, filteredMessage);
                }
            } catch (Exception e) {
                log.error("Failed to broadcast message to sessionId={}: {}", session.getId(), e.getMessage());
            }
        });
    }

    @Override
    public void sendToSession(String sessionId, Object message) {
        WebSocketSession session = sessions.get(sessionId);
        if (session != null && session.isOpen()) {
            sendMessageToSession(session, message);
        } else {
            log.warn("Attempted to send message to inactive session: {}", sessionId);
        }
    }

    @Override
    public void handleUpdateRequest(WebSocketSession session, String requestType, Map<String, Object> requestData) {
        String sessionId = session.getId();
        Long userId = (Long) session.getAttributes().get(WebSocketAuthenticationInterceptor.USER_ID_ATTR);
        String userRole = (String) session.getAttributes().get(WebSocketAuthenticationInterceptor.USER_ROLE_ATTR);

        log.debug("Handling update request from user {}: type={}, data={}", userId, requestType, requestData);

        try {
            Map<String, Object> response = switch (requestType) {
                case "current_location" -> handleCurrentLocationRequest(userId, userRole, requestData);
                case "trip_status" -> handleTripStatusRequest(userId, userRole, requestData);
                case "fleet_overview" -> handleFleetOverviewRequest(userId, userRole, requestData);
                case "notifications" -> handleNotificationsRequest(userId, userRole, requestData);
                default -> Map.of("error", "Unknown request type: " + requestType);
            };

            WebSocketMessage responseMessage = WebSocketMessage.system(
                "UPDATE_RESPONSE", 
                Map.of("requestType", requestType, "data", response)
            );

            sendMessageToSession(session, responseMessage);

        } catch (Exception e) {
            log.error("Failed to handle update request from sessionId={}: {}", sessionId, e.getMessage(), e);
            
            WebSocketMessage errorMessage = WebSocketMessage.error("Update request failed");
            sendMessageToSession(session, errorMessage);
        }
    }

    @Override
    public void sendInitialData(WebSocketSession session, String endpoint, String userRole) {
        String sessionId = session.getId();
        Long userId = (Long) session.getAttributes().get(WebSocketAuthenticationInterceptor.USER_ID_ATTR);

        log.debug("Sending initial data to sessionId={}, endpoint={}, role={}", sessionId, endpoint, userRole);

        try {
            Map<String, Object> initialData = switch (endpoint) {
                case "location" -> getInitialLocationData(userId, userRole);
                case "notifications" -> getInitialNotificationsData(userId, userRole);
                case "fleet" -> getInitialFleetData(userId, userRole);
                default -> Map.of("message", "No initial data for endpoint: " + endpoint);
            };

            WebSocketMessage message = WebSocketMessage.system("INITIAL_DATA", initialData);

            sendMessageToSession(session, message);

        } catch (Exception e) {
            log.error("Failed to send initial data to sessionId={}: {}", sessionId, e.getMessage(), e);
        }
    }

    @Override
    public SessionStatistics getSessionStatistics() {
        int totalSessions = sessions.size();
        int activeSessions = (int) sessions.values().stream()
            .filter(WebSocketSession::isOpen)
            .count();

        int totalSubscriptions = sessionSubscriptions.values().stream()
            .mapToInt(Set::size)
            .sum();

        Map<String, Integer> subscriptionsByTopic = topicSubscriptions.entrySet().stream()
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                entry -> entry.getValue().size()
            ));

        Map<String, Integer> sessionsByRole = sessionInfos.values().stream()
            .collect(Collectors.groupingBy(
                SessionInfo::userRole,
                Collectors.collectingAndThen(Collectors.counting(), Math::toIntExact)
            ));

        Map<String, Integer> sessionsByEndpoint = sessionInfos.values().stream()
            .collect(Collectors.groupingBy(
                SessionInfo::endpoint,
                Collectors.collectingAndThen(Collectors.counting(), Math::toIntExact)
            ));

        return new SessionStatistics(
            totalSessions, activeSessions, totalSubscriptions,
            subscriptionsByTopic, sessionsByRole, sessionsByEndpoint
        );
    }

    @Override
    public int cleanupInactiveSessions() {
        log.debug("Cleaning up inactive WebSocket sessions");
        
        List<String> inactiveSessionIds = sessions.entrySet().stream()
            .filter(entry -> !entry.getValue().isOpen())
            .map(Map.Entry::getKey)
            .collect(Collectors.toList());

        inactiveSessionIds.forEach(sessionId -> {
            WebSocketSession session = sessions.get(sessionId);
            if (session != null) {
                unregisterSession(session);
            }
        });

        log.info("Cleaned up {} inactive WebSocket sessions", inactiveSessionIds.size());
        return inactiveSessionIds.size();
    }

    // ── Private Helper Methods ───────────────────────────────────────────────

    private String extractEndpoint(WebSocketSession session) {
        String path = session.getUri().getPath();
        if (path.contains("/location")) return "location";
        if (path.contains("/notifications")) return "notifications";
        if (path.contains("/fleet")) return "fleet";
        return "unknown";
    }

    private boolean isSubscriptionAllowed(String userRole, String topic) {
        // Role-based topic access control
        return switch (userRole) {
            case "ADMIN" -> true; // Admin can subscribe to everything
            case "FLEET_OWNER" -> topic.startsWith("fleet.") || topic.startsWith("trip.") || topic.startsWith("vehicle.");
            case "DRIVER" -> topic.startsWith("trip.") || topic.contains(".driver.");
            case "SHIPPER" -> topic.startsWith("trip.") || topic.contains(".shipper.");
            default -> false;
        };
    }

    private Object applySubscriptionFilter(WebSocketSession session, String topic, Object message) {
        String filterKey = session.getId() + ":" + topic;
        Object filter = subscriptionFilters.get(filterKey);
        
        if (filter == null) {
            return message; // No filter, return original message
        }

        // Apply filtering logic based on filter criteria
        // This is a simplified implementation - in production, would have sophisticated filtering
        return message;
    }

    private void sendMessageToSession(WebSocketSession session, Object message) {
        if (session.isOpen()) {
            try {
                String messageJson = objectMapper.writeValueAsString(message);
                session.sendMessage(new TextMessage(messageJson));
            } catch (IOException e) {
                log.error("Failed to send message to sessionId={}: {}", session.getId(), e.getMessage());
            }
        }
    }

    // ── Data Providers ───────────────────────────────────────────────────────

    private Map<String, Object> handleCurrentLocationRequest(Long userId, String userRole, Map<String, Object> requestData) {
        // Simplified implementation - would integrate with LocationPersistenceService
        return Map.of(
            "locations", List.of(),
            "timestamp", LocalDateTime.now(),
            "status", "success"
        );
    }

    private Map<String, Object> handleTripStatusRequest(Long userId, String userRole, Map<String, Object> requestData) {
        // Simplified implementation - would integrate with TripService
        return Map.of(
            "trips", List.of(),
            "timestamp", LocalDateTime.now(),
            "status", "success"
        );
    }

    private Map<String, Object> handleFleetOverviewRequest(Long userId, String userRole, Map<String, Object> requestData) {
        // Simplified implementation - would integrate with fleet services
        return Map.of(
            "vehicles", List.of(),
            "activeTrips", 0,
            "timestamp", LocalDateTime.now(),
            "status", "success"
        );
    }

    private Map<String, Object> handleNotificationsRequest(Long userId, String userRole, Map<String, Object> requestData) {
        // Simplified implementation - would integrate with notification service
        return Map.of(
            "notifications", List.of(),
            "unreadCount", 0,
            "timestamp", LocalDateTime.now(),
            "status", "success"
        );
    }

    private Map<String, Object> getInitialLocationData(Long userId, String userRole) {
        return Map.of(
            "type", "location_data",
            "message", "Connected to location tracking",
            "timestamp", LocalDateTime.now()
        );
    }

    private Map<String, Object> getInitialNotificationsData(Long userId, String userRole) {
        return Map.of(
            "type", "notifications_data",
            "message", "Connected to notifications",
            "timestamp", LocalDateTime.now()
        );
    }

    private Map<String, Object> getInitialFleetData(Long userId, String userRole) {
        return Map.of(
            "type", "fleet_data",
            "message", "Connected to fleet management",
            "timestamp", LocalDateTime.now()
        );
    }

    // ── Security Integration Helper Methods ───────────────────────────────────

    private User getUserFromSession(WebSocketSession session) {
        Long userId = (Long) session.getAttributes().get(WebSocketAuthenticationInterceptor.USER_ID_ATTR);
        if (userId != null) {
            return userRepository.findById(userId).orElse(null);
        }
        return null;
    }

    private void auditSubscriptionSuccess(Long userId, String sessionId, String topic, String action, Object filter) {
        WebSocketAuditService.SubscriptionAuditEvent event = new WebSocketAuditService.SubscriptionAuditEvent(
            java.util.UUID.randomUUID().toString(),
            userId, sessionId, topic, action, true, null, filter,
            LocalDateTime.now(), Map.of()
        );
        auditService.logSubscriptionEvent(event);
    }

    private void auditSubscriptionFailure(Long userId, String sessionId, String topic, String failureReason, Object filter) {
        WebSocketAuditService.SubscriptionAuditEvent event = new WebSocketAuditService.SubscriptionAuditEvent(
            java.util.UUID.randomUUID().toString(),
            userId, sessionId, topic, "SUBSCRIBE", false, failureReason, filter,
            LocalDateTime.now(), Map.of()
        );
        auditService.logSubscriptionEvent(event);
    }

    private void auditAuthorizationEvent(Long userId, String sessionId, String action, String resource, boolean allowed, String denialReason) {
        WebSocketAuditService.AuthorizationAuditEvent event = new WebSocketAuditService.AuthorizationAuditEvent(
            java.util.UUID.randomUUID().toString(),
            userId, sessionId, resource, action, allowed, denialReason, 
            allowed ? null : "AUTHORIZATION_DENIED",
            LocalDateTime.now(), Map.of()
        );
        auditService.logAuthorizationEvent(event);
    }
}