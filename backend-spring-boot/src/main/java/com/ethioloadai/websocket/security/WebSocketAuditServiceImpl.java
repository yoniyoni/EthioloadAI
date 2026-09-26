package com.ethioloadai.websocket.security;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Implementation of WebSocket audit service using in-memory storage.
 * 
 * In production, this would typically use a database or dedicated audit log system
 * for persistence and advanced querying capabilities.
 */
@Service
@RequiredArgsConstructor
public class WebSocketAuditServiceImpl implements WebSocketAuditService {
    private static final Logger log = LoggerFactory.getLogger(WebSocketAuditServiceImpl.class);

    private final WebSocketSecurityConfig securityConfig;

    // In-memory audit event storage - in production, use database
    private final Map<String, AuditEvent> auditEvents = new ConcurrentHashMap<>();
    private final Map<Long, List<String>> userEvents = new ConcurrentHashMap<>();
    private final Map<String, List<String>> sessionEvents = new ConcurrentHashMap<>();

    @Override
    public void logConnectionEvent(ConnectionAuditEvent event) {
        if (!securityConfig.getAuditLogging().isLogConnections()) {
            return;
        }

        AuditEvent auditEvent = new AuditEvent(
            event.eventId(),
            "CONNECTION",
            event.userId(),
            event.sessionId(),
            event.ipAddress(),
            event.timestamp(),
            String.format("WebSocket connection %s from %s to %s", 
                         event.success() ? "established" : "failed", 
                         event.ipAddress(), event.endpoint()),
            Map.of(
                "success", event.success(),
                "endpoint", event.endpoint(),
                "userAgent", event.userAgent(),
                "failureReason", Objects.toString(event.failureReason(), ""),
                "connectionMetadata", event.connectionMetadata()
            )
        );

        storeAuditEvent(auditEvent);
        
        log.debug("Logged connection event: {} for user {} from IP {}", 
                 event.success() ? "SUCCESS" : "FAILURE", event.userId(), event.ipAddress());
    }

    @Override
    public void logAuthenticationEvent(AuthenticationAuditEvent event) {
        if (!securityConfig.getAuditLogging().isLogConnections()) {
            return;
        }

        AuditEvent auditEvent = new AuditEvent(
            event.eventId(),
            "AUTHENTICATION",
            event.userId(),
            event.sessionId(),
            event.ipAddress(),
            event.timestamp(),
            String.format("WebSocket authentication %s for user %s using %s", 
                         event.success() ? "succeeded" : "failed",
                         event.userId(), event.authenticationMethod()),
            Map.of(
                "success", event.success(),
                "authenticationMethod", event.authenticationMethod(),
                "userRole", Objects.toString(event.userRole(), ""),
                "failureReason", Objects.toString(event.failureReason(), ""),
                "authMetadata", event.authMetadata()
            )
        );

        storeAuditEvent(auditEvent);
        
        log.debug("Logged authentication event: {} for user {} using {}", 
                 event.success() ? "SUCCESS" : "FAILURE", event.userId(), event.authenticationMethod());
    }

    @Override
    public void logAuthorizationEvent(AuthorizationAuditEvent event) {
        AuditEvent auditEvent = new AuditEvent(
            event.eventId(),
            "AUTHORIZATION",
            event.userId(),
            event.sessionId(),
            null,
            event.timestamp(),
            String.format("Authorization %s for user %s to %s %s", 
                         event.allowed() ? "granted" : "denied",
                         event.userId(), event.action(), event.resource()),
            Map.of(
                "allowed", event.allowed(),
                "resource", event.resource(),
                "action", event.action(),
                "denialReason", Objects.toString(event.denialReason(), ""),
                "denialCode", Objects.toString(event.denialCode(), ""),
                "authzMetadata", event.authzMetadata()
            )
        );

        storeAuditEvent(auditEvent);
        
        log.debug("Logged authorization event: {} for user {} accessing {} ({})", 
                 event.allowed() ? "GRANTED" : "DENIED", event.userId(), event.resource(), event.action());
    }

    @Override
    public void logRateLimitEvent(RateLimitAuditEvent event) {
        if (!securityConfig.getAuditLogging().isLogRateLimitViolations()) {
            return;
        }

        AuditEvent auditEvent = new AuditEvent(
            event.eventId(),
            "RATE_LIMIT",
            event.userId(),
            event.sessionId(),
            event.ipAddress(),
            event.timestamp(),
            String.format("Rate limit %s for user %s: %s (%d/%d)", 
                         event.violated() ? "violated" : "checked",
                         event.userId(), event.limitType(), 
                         event.currentCount(), event.limitThreshold()),
            Map.of(
                "violated", event.violated(),
                "limitType", event.limitType(),
                "currentCount", event.currentCount(),
                "limitThreshold", event.limitThreshold(),
                "action", event.action(),
                "rateLimitMetadata", event.rateLimitMetadata()
            )
        );

        storeAuditEvent(auditEvent);
        
        log.debug("Logged rate limit event: {} for user {} (type: {}, count: {}/{})", 
                 event.violated() ? "VIOLATION" : "CHECK", event.userId(), 
                 event.limitType(), event.currentCount(), event.limitThreshold());
    }

    @Override
    public void logSecurityViolation(SecurityViolationEvent event) {
        if (!securityConfig.getAuditLogging().isLogSecurityViolations()) {
            return;
        }

        AuditEvent auditEvent = new AuditEvent(
            event.eventId(),
            "SECURITY_VIOLATION",
            event.userId(),
            event.sessionId(),
            event.ipAddress(),
            event.timestamp(),
            String.format("Security violation: %s - %s", event.violationType(), event.description()),
            Map.of(
                "violationType", event.violationType(),
                "description", event.description(),
                "severity", event.severity(),
                "action", event.action(),
                "violationMetadata", event.violationMetadata()
            )
        );

        storeAuditEvent(auditEvent);
        
        log.warn("Logged security violation: {} for user {} from IP {} - {} (severity: {})", 
                event.violationType(), event.userId(), event.ipAddress(), 
                event.description(), event.severity());
    }

    @Override
    public void logSubscriptionEvent(SubscriptionAuditEvent event) {
        if (!securityConfig.getAuditLogging().isLogSubscriptions()) {
            return;
        }

        AuditEvent auditEvent = new AuditEvent(
            event.eventId(),
            "SUBSCRIPTION",
            event.userId(),
            event.sessionId(),
            null,
            event.timestamp(),
            String.format("Subscription %s %s for topic %s", 
                         event.action(),
                         event.success() ? "succeeded" : "failed", 
                         event.topic()),
            Map.of(
                "success", event.success(),
                "topic", event.topic(),
                "action", event.action(),
                "filter", Objects.toString(event.filter(), ""),
                "failureReason", Objects.toString(event.failureReason(), ""),
                "subscriptionMetadata", event.subscriptionMetadata()
            )
        );

        storeAuditEvent(auditEvent);
        
        log.debug("Logged subscription event: {} {} for user {} to topic {}", 
                 event.action(), event.success() ? "SUCCESS" : "FAILURE", 
                 event.userId(), event.topic());
    }

    @Override
    public void logMessageEvent(MessageAuditEvent event) {
        if (!securityConfig.getAuditLogging().isLogMessages()) {
            return;
        }

        AuditEvent auditEvent = new AuditEvent(
            event.eventId(),
            "MESSAGE",
            event.userId(),
            event.sessionId(),
            null,
            event.timestamp(),
            String.format("Message %s: %s (%d bytes) %s", 
                         event.direction(), event.messageType(), 
                         event.messageSize(), 
                         event.topic() != null ? "to/from " + event.topic() : ""),
            Map.of(
                "messageType", event.messageType(),
                "direction", event.direction(),
                "topic", Objects.toString(event.topic(), ""),
                "messageSize", event.messageSize(),
                "messageMetadata", event.messageMetadata()
            )
        );

        storeAuditEvent(auditEvent);
        
        log.debug("Logged message event: {} {} for user {} (type: {}, size: {} bytes)", 
                 event.direction(), event.messageType(), event.userId(), 
                 event.messageType(), event.messageSize());
    }

    @Override
    public List<AuditEvent> getUserAuditEvents(Long userId, LocalDateTime startTime, LocalDateTime endTime, List<String> eventTypes) {
        List<String> userEventIds = userEvents.getOrDefault(userId, List.of());
        
        return userEventIds.stream()
            .map(auditEvents::get)
            .filter(Objects::nonNull)
            .filter(event -> isInTimeRange(event, startTime, endTime))
            .filter(event -> eventTypes == null || eventTypes.isEmpty() || eventTypes.contains(event.eventType()))
            .sorted(Comparator.comparing(AuditEvent::timestamp).reversed())
            .collect(Collectors.toList());
    }

    @Override
    public List<AuditEvent> getSessionAuditEvents(String sessionId) {
        List<String> sessionEventIds = sessionEvents.getOrDefault(sessionId, List.of());
        
        return sessionEventIds.stream()
            .map(auditEvents::get)
            .filter(Objects::nonNull)
            .sorted(Comparator.comparing(AuditEvent::timestamp))
            .collect(Collectors.toList());
    }

    @Override
    public SecurityViolationSummary getSecurityViolationSummary(LocalDateTime startTime, LocalDateTime endTime) {
        List<AuditEvent> securityEvents = auditEvents.values().stream()
            .filter(event -> "SECURITY_VIOLATION".equals(event.eventType()))
            .filter(event -> isInTimeRange(event, startTime, endTime))
            .collect(Collectors.toList());

        Map<String, Integer> violationsByType = securityEvents.stream()
            .collect(Collectors.groupingBy(
                event -> (String) event.metadata().getOrDefault("violationType", "UNKNOWN"),
                Collectors.collectingAndThen(Collectors.counting(), Math::toIntExact)
            ));

        Map<String, Integer> violationsBySeverity = securityEvents.stream()
            .collect(Collectors.groupingBy(
                event -> (String) event.metadata().getOrDefault("severity", "UNKNOWN"),
                Collectors.collectingAndThen(Collectors.counting(), Math::toIntExact)
            ));

        List<String> topViolatingIps = securityEvents.stream()
            .filter(event -> event.ipAddress() != null)
            .collect(Collectors.groupingBy(AuditEvent::ipAddress, Collectors.counting()))
            .entrySet().stream()
            .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
            .limit(10)
            .map(Map.Entry::getKey)
            .collect(Collectors.toList());

        List<Long> topViolatingUsers = securityEvents.stream()
            .filter(event -> event.userId() != null)
            .collect(Collectors.groupingBy(AuditEvent::userId, Collectors.counting()))
            .entrySet().stream()
            .sorted(Map.Entry.<Long, Long>comparingByValue().reversed())
            .limit(10)
            .map(Map.Entry::getKey)
            .collect(Collectors.toList());

        Map<String, Integer> actionsTaken = securityEvents.stream()
            .collect(Collectors.groupingBy(
                event -> (String) event.metadata().getOrDefault("action", "NONE"),
                Collectors.collectingAndThen(Collectors.counting(), Math::toIntExact)
            ));

        return new SecurityViolationSummary(
            startTime, endTime, securityEvents.size(),
            violationsByType, violationsBySeverity,
            topViolatingIps, topViolatingUsers, actionsTaken
        );
    }

    @Override
    public RateLimitViolationSummary getRateLimitViolationSummary(LocalDateTime startTime, LocalDateTime endTime) {
        List<AuditEvent> rateLimitEvents = auditEvents.values().stream()
            .filter(event -> "RATE_LIMIT".equals(event.eventType()))
            .filter(event -> (Boolean) event.metadata().getOrDefault("violated", false))
            .filter(event -> isInTimeRange(event, startTime, endTime))
            .collect(Collectors.toList());

        Map<String, Integer> violationsByType = rateLimitEvents.stream()
            .collect(Collectors.groupingBy(
                event -> (String) event.metadata().getOrDefault("limitType", "UNKNOWN"),
                Collectors.collectingAndThen(Collectors.counting(), Math::toIntExact)
            ));

        List<String> topViolatingIps = rateLimitEvents.stream()
            .filter(event -> event.ipAddress() != null)
            .collect(Collectors.groupingBy(AuditEvent::ipAddress, Collectors.counting()))
            .entrySet().stream()
            .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
            .limit(10)
            .map(Map.Entry::getKey)
            .collect(Collectors.toList());

        List<Long> topViolatingUsers = rateLimitEvents.stream()
            .filter(event -> event.userId() != null)
            .collect(Collectors.groupingBy(AuditEvent::userId, Collectors.counting()))
            .entrySet().stream()
            .sorted(Map.Entry.<Long, Long>comparingByValue().reversed())
            .limit(10)
            .map(Map.Entry::getKey)
            .collect(Collectors.toList());

        Map<String, Integer> actionsTaken = rateLimitEvents.stream()
            .collect(Collectors.groupingBy(
                event -> (String) event.metadata().getOrDefault("action", "NONE"),
                Collectors.collectingAndThen(Collectors.counting(), Math::toIntExact)
            ));

        return new RateLimitViolationSummary(
            startTime, endTime, rateLimitEvents.size(),
            violationsByType, topViolatingIps, topViolatingUsers,
            0, // usersCurrentlyLimited - would need additional tracking
            actionsTaken
        );
    }

    @Override
    public int cleanupOldAuditEvents() {
        LocalDateTime cutoff = LocalDateTime.now()
            .minus(securityConfig.getAuditLogging().getRetentionPeriod());
        
        Set<String> expiredEventIds = auditEvents.entrySet().stream()
            .filter(entry -> entry.getValue().timestamp().isBefore(cutoff))
            .map(Map.Entry::getKey)
            .collect(Collectors.toSet());
        
        expiredEventIds.forEach(this::removeAuditEvent);
        
        log.info("Cleaned up {} expired audit events", expiredEventIds.size());
        return expiredEventIds.size();
    }

    @Override
    public String exportAuditEvents(LocalDateTime startTime, LocalDateTime endTime, String format) {
        List<AuditEvent> events = auditEvents.values().stream()
            .filter(event -> isInTimeRange(event, startTime, endTime))
            .sorted(Comparator.comparing(AuditEvent::timestamp))
            .collect(Collectors.toList());

        if ("CSV".equalsIgnoreCase(format)) {
            return exportToCsv(events);
        } else {
            return exportToJson(events);
        }
    }

    // ── Private Helper Methods ───────────────────────────────────────────────

    private void storeAuditEvent(AuditEvent event) {
        auditEvents.put(event.eventId(), event);
        
        // Index by user
        if (event.userId() != null) {
            userEvents.computeIfAbsent(event.userId(), k -> new ArrayList<>()).add(event.eventId());
        }
        
        // Index by session
        if (event.sessionId() != null) {
            sessionEvents.computeIfAbsent(event.sessionId(), k -> new ArrayList<>()).add(event.eventId());
        }
    }

    private void removeAuditEvent(String eventId) {
        AuditEvent event = auditEvents.remove(eventId);
        if (event != null) {
            // Remove from user index
            if (event.userId() != null) {
                List<String> userEventList = userEvents.get(event.userId());
                if (userEventList != null) {
                    userEventList.remove(eventId);
                    if (userEventList.isEmpty()) {
                        userEvents.remove(event.userId());
                    }
                }
            }
            
            // Remove from session index
            if (event.sessionId() != null) {
                List<String> sessionEventList = sessionEvents.get(event.sessionId());
                if (sessionEventList != null) {
                    sessionEventList.remove(eventId);
                    if (sessionEventList.isEmpty()) {
                        sessionEvents.remove(event.sessionId());
                    }
                }
            }
        }
    }

    private boolean isInTimeRange(AuditEvent event, LocalDateTime startTime, LocalDateTime endTime) {
        return !event.timestamp().isBefore(startTime) && !event.timestamp().isAfter(endTime);
    }

    private String exportToCsv(List<AuditEvent> events) {
        StringBuilder csv = new StringBuilder();
        csv.append("EventId,EventType,UserId,SessionId,IpAddress,Timestamp,Details\n");
        
        for (AuditEvent event : events) {
            csv.append(String.format("%s,%s,%s,%s,%s,%s,\"%s\"\n",
                event.eventId(),
                event.eventType(),
                Objects.toString(event.userId(), ""),
                Objects.toString(event.sessionId(), ""),
                Objects.toString(event.ipAddress(), ""),
                event.timestamp(),
                event.details().replace("\"", "\"\"")
            ));
        }
        
        return csv.toString();
    }

    private String exportToJson(List<AuditEvent> events) {
        // Simple JSON export - in production, use proper JSON library
        StringBuilder json = new StringBuilder();
        json.append("[\n");
        
        for (int i = 0; i < events.size(); i++) {
            AuditEvent event = events.get(i);
            json.append("  {\n");
            json.append(String.format("    \"eventId\": \"%s\",\n", event.eventId()));
            json.append(String.format("    \"eventType\": \"%s\",\n", event.eventType()));
            json.append(String.format("    \"userId\": %s,\n", event.userId()));
            json.append(String.format("    \"sessionId\": \"%s\",\n", Objects.toString(event.sessionId(), "")));
            json.append(String.format("    \"ipAddress\": \"%s\",\n", Objects.toString(event.ipAddress(), "")));
            json.append(String.format("    \"timestamp\": \"%s\",\n", event.timestamp()));
            json.append(String.format("    \"details\": \"%s\"\n", event.details().replace("\"", "\\\"")));
            json.append("  }");
            if (i < events.size() - 1) {
                json.append(",");
            }
            json.append("\n");
        }
        
        json.append("]");
        return json.toString();
    }
}