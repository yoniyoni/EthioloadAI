package com.ethioloadai.websocket.security;

import com.ethioloadai.user.entity.User;

import java.util.List;

/**
 * Service for WebSocket authorization and access control.
 * 
 * Provides fine-grained authorization for WebSocket topics, actions,
 * and data access based on user roles, trip relationships, and business rules.
 */
public interface WebSocketAuthorizationService {

    /**
     * Check if a user can subscribe to a specific topic.
     * 
     * @param user the user requesting subscription
     * @param topic the topic to subscribe to
     * @param filter optional subscription filter
     * @return authorization result with details
     */
    AuthorizationResult canSubscribeToTopic(User user, String topic, Object filter);

    /**
     * Check if a user can send a location update.
     * 
     * @param user the user sending the update
     * @param tripId trip identifier
     * @param vehicleId vehicle identifier
     * @return authorization result with details
     */
    AuthorizationResult canSendLocationUpdate(User user, Long tripId, Long vehicleId);

    /**
     * Check if a user can access trip data.
     * 
     * @param user the user requesting access
     * @param tripId trip identifier
     * @return authorization result with details
     */
    AuthorizationResult canAccessTrip(User user, Long tripId);

    /**
     * Check if a user can access vehicle data.
     * 
     * @param user the user requesting access
     * @param vehicleId vehicle identifier
     * @return authorization result with details
     */
    AuthorizationResult canAccessVehicle(User user, Long vehicleId);

    /**
     * Check if a user can access fleet data.
     * 
     * @param user the user requesting access
     * @param fleetOwnerId fleet owner identifier (null for own fleet)
     * @return authorization result with details
     */
    AuthorizationResult canAccessFleet(User user, Long fleetOwnerId);

    /**
     * Check if a user can receive notifications for another user.
     * 
     * @param user the user requesting access
     * @param targetUserId target user identifier
     * @return authorization result with details
     */
    AuthorizationResult canAccessUserNotifications(User user, Long targetUserId);

    /**
     * Filter topics that a user is authorized to access.
     * 
     * @param user the user
     * @param topics list of topics to filter
     * @return filtered list of authorized topics
     */
    List<String> filterAuthorizedTopics(User user, List<String> topics);

    /**
     * Get all topics a user is authorized to subscribe to.
     * 
     * @param user the user
     * @return list of authorized topic patterns
     */
    List<String> getAuthorizedTopicPatterns(User user);

    /**
     * Validate subscription filter for authorization.
     * 
     * @param user the user
     * @param topic the topic
     * @param filter subscription filter
     * @return authorization result for the filter
     */
    AuthorizationResult validateSubscriptionFilter(User user, String topic, Object filter);

    /**
     * Check if a user can perform administrative actions.
     * 
     * @param user the user
     * @param action administrative action
     * @param target target of the action (optional)
     * @return authorization result
     */
    AuthorizationResult canPerformAdminAction(User user, String action, Object target);

    // ── Value Objects ────────────────────────────────────────────────────────

    /**
     * Authorization result with details.
     */
    record AuthorizationResult(
        boolean allowed,
        String reason,
        String denialCode,
        java.time.LocalDateTime timestamp,
        String requiredRole,
        String requiredPermission
    ) {
        public static AuthorizationResult allow(String reason) {
            return new AuthorizationResult(true, reason, null, java.time.LocalDateTime.now(), null, null);
        }
        
        public static AuthorizationResult deny(String reason, String denialCode) {
            return new AuthorizationResult(false, reason, denialCode, java.time.LocalDateTime.now(), null, null);
        }
        
        public static AuthorizationResult denyRequireRole(String reason, String requiredRole) {
            return new AuthorizationResult(false, reason, "INSUFFICIENT_ROLE", 
                                         java.time.LocalDateTime.now(), requiredRole, null);
        }
        
        public static AuthorizationResult denyRequirePermission(String reason, String requiredPermission) {
            return new AuthorizationResult(false, reason, "INSUFFICIENT_PERMISSION", 
                                         java.time.LocalDateTime.now(), null, requiredPermission);
        }
    }

    /**
     * Topic access levels.
     */
    enum AccessLevel {
        NONE,        // No access
        READ_ONLY,   // Can subscribe and receive updates
        READ_WRITE,  // Can subscribe and send updates
        ADMIN        // Full administrative access
    }

    /**
     * Topic authorization context.
     */
    record TopicAuthorizationContext(
        User user,
        String topic,
        AccessLevel requestedLevel,
        Object filter,
        java.time.LocalDateTime timestamp
    ) {}
}