package com.ethioloadai.websocket.security;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Service for WebSocket security audit logging and monitoring.
 * 
 * Provides comprehensive audit trails for WebSocket security events,
 * connection tracking, and violation monitoring for compliance and security analysis.
 */
public interface WebSocketAuditService {

    /**
     * Log a WebSocket connection event.
     * 
     * @param event connection audit event
     */
    void logConnectionEvent(ConnectionAuditEvent event);

    /**
     * Log a WebSocket authentication event.
     * 
     * @param event authentication audit event
     */
    void logAuthenticationEvent(AuthenticationAuditEvent event);

    /**
     * Log a WebSocket authorization event.
     * 
     * @param event authorization audit event
     */
    void logAuthorizationEvent(AuthorizationAuditEvent event);

    /**
     * Log a rate limiting violation.
     * 
     * @param event rate limit audit event
     */
    void logRateLimitEvent(RateLimitAuditEvent event);

    /**
     * Log a security violation.
     * 
     * @param event security violation audit event
     */
    void logSecurityViolation(SecurityViolationEvent event);

    /**
     * Log a subscription event.
     * 
     * @param event subscription audit event
     */
    void logSubscriptionEvent(SubscriptionAuditEvent event);

    /**
     * Log a message event (if enabled).
     * 
     * @param event message audit event
     */
    void logMessageEvent(MessageAuditEvent event);

    /**
     * Get audit events for a specific user.
     * 
     * @param userId user identifier
     * @param startTime start time for query
     * @param endTime end time for query
     * @param eventTypes filter by event types (optional)
     * @return list of audit events
     */
    List<AuditEvent> getUserAuditEvents(Long userId, LocalDateTime startTime, LocalDateTime endTime, List<String> eventTypes);

    /**
     * Get audit events for a specific session.
     * 
     * @param sessionId WebSocket session identifier
     * @return list of audit events for the session
     */
    List<AuditEvent> getSessionAuditEvents(String sessionId);

    /**
     * Get security violation summary.
     * 
     * @param startTime start time for analysis
     * @param endTime end time for analysis
     * @return security violation summary
     */
    SecurityViolationSummary getSecurityViolationSummary(LocalDateTime startTime, LocalDateTime endTime);

    /**
     * Get rate limit violation summary.
     * 
     * @param startTime start time for analysis
     * @param endTime end time for analysis
     * @return rate limit violation summary
     */
    RateLimitViolationSummary getRateLimitViolationSummary(LocalDateTime startTime, LocalDateTime endTime);

    /**
     * Clean up old audit events based on retention policy.
     * 
     * @return number of events cleaned up
     */
    int cleanupOldAuditEvents();

    /**
     * Export audit events for compliance reporting.
     * 
     * @param startTime start time for export
     * @param endTime end time for export
     * @param format export format (JSON, CSV, etc.)
     * @return exported audit data
     */
    String exportAuditEvents(LocalDateTime startTime, LocalDateTime endTime, String format);

    // ── Audit Event Types ────────────────────────────────────────────────────

    /**
     * Base audit event.
     */
    record AuditEvent(
        String eventId,
        String eventType,
        Long userId,
        String sessionId,
        String ipAddress,
        LocalDateTime timestamp,
        String details,
        Map<String, Object> metadata
    ) {}

    /**
     * WebSocket connection audit event.
     */
    record ConnectionAuditEvent(
        String eventId,
        Long userId,
        String sessionId,
        String ipAddress,
        String userAgent,
        String endpoint,
        boolean success,
        String failureReason,
        LocalDateTime timestamp,
        Map<String, Object> connectionMetadata
    ) {}

    /**
     * Authentication audit event.
     */
    record AuthenticationAuditEvent(
        String eventId,
        Long userId,
        String sessionId,
        String ipAddress,
        String authenticationMethod,
        boolean success,
        String failureReason,
        String userRole,
        LocalDateTime timestamp,
        Map<String, Object> authMetadata
    ) {}

    /**
     * Authorization audit event.
     */
    record AuthorizationAuditEvent(
        String eventId,
        Long userId,
        String sessionId,
        String resource,
        String action,
        boolean allowed,
        String denialReason,
        String denialCode,
        LocalDateTime timestamp,
        Map<String, Object> authzMetadata
    ) {}

    /**
     * Rate limit audit event.
     */
    record RateLimitAuditEvent(
        String eventId,
        Long userId,
        String sessionId,
        String ipAddress,
        String limitType,
        int currentCount,
        int limitThreshold,
        boolean violated,
        String action,
        LocalDateTime timestamp,
        Map<String, Object> rateLimitMetadata
    ) {}

    /**
     * Security violation audit event.
     */
    record SecurityViolationEvent(
        String eventId,
        Long userId,
        String sessionId,
        String ipAddress,
        String violationType,
        String description,
        String severity,
        String action,
        LocalDateTime timestamp,
        Map<String, Object> violationMetadata
    ) {}

    /**
     * Subscription audit event.
     */
    record SubscriptionAuditEvent(
        String eventId,
        Long userId,
        String sessionId,
        String topic,
        String action,
        boolean success,
        String failureReason,
        Object filter,
        LocalDateTime timestamp,
        Map<String, Object> subscriptionMetadata
    ) {}

    /**
     * Message audit event.
     */
    record MessageAuditEvent(
        String eventId,
        Long userId,
        String sessionId,
        String messageType,
        String direction,
        String topic,
        int messageSize,
        LocalDateTime timestamp,
        Map<String, Object> messageMetadata
    ) {}

    // ── Summary Objects ──────────────────────────────────────────────────────

    /**
     * Security violation summary.
     */
    record SecurityViolationSummary(
        LocalDateTime periodStart,
        LocalDateTime periodEnd,
        int totalViolations,
        Map<String, Integer> violationsByType,
        Map<String, Integer> violationsBySeverity,
        List<String> topViolatingIps,
        List<Long> topViolatingUsers,
        Map<String, Integer> actionsTaken
    ) {}

    /**
     * Rate limit violation summary.
     */
    record RateLimitViolationSummary(
        LocalDateTime periodStart,
        LocalDateTime periodEnd,
        int totalViolations,
        Map<String, Integer> violationsByType,
        List<String> topViolatingIps,
        List<Long> topViolatingUsers,
        int usersCurrentlyLimited,
        Map<String, Integer> actionsTaken
    ) {}
}