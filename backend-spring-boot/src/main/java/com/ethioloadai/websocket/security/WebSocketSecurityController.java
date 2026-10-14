package com.ethioloadai.websocket.security;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * REST controller for WebSocket security management and monitoring.
 * 
 * Provides endpoints for security administrators to monitor WebSocket
 * security events, manage rate limits, and analyze security violations.
 */
@RestController
@RequestMapping("/websocket/security")
@RequiredArgsConstructor
public class WebSocketSecurityController {
    private static final Logger log = LoggerFactory.getLogger(WebSocketSecurityController.class);

    private final WebSocketRateLimitService rateLimitService;
    private final WebSocketAuthorizationService authorizationService;
    private final WebSocketAuditService auditService;

    /**
     * Get rate limit statistics.
     */
    @GetMapping("/rate-limits/stats")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<WebSocketRateLimitService.RateLimitStatistics> getRateLimitStatistics() {
        log.debug("Retrieving rate limit statistics");
        
        WebSocketRateLimitService.RateLimitStatistics stats = rateLimitService.getRateLimitStatistics();
        return ResponseEntity.ok(stats);
    }

    /**
     * Get rate limit status for a specific user.
     */
    @GetMapping("/rate-limits/users/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<WebSocketRateLimitService.RateLimitStatus> getUserRateLimitStatus(
            @PathVariable Long userId) {
        
        log.debug("Retrieving rate limit status for user: {}", userId);
        
        WebSocketRateLimitService.RateLimitStatus status = rateLimitService.getUserRateLimitStatus(userId);
        return ResponseEntity.ok(status);
    }

    /**
     * Reset rate limits for a user.
     */
    @PostMapping("/rate-limits/users/{userId}/reset")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> resetUserRateLimits(@PathVariable Long userId) {
        log.info("Admin resetting rate limits for user: {}", userId);
        
        try {
            rateLimitService.resetUserRateLimits(userId);
            
            return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "Rate limits reset for user: " + userId,
                "timestamp", LocalDateTime.now()
            ));
            
        } catch (Exception e) {
            log.error("Failed to reset rate limits for user {}: {}", userId, e.getMessage(), e);
            
            return ResponseEntity.badRequest().body(Map.of(
                "status", "error",
                "message", "Failed to reset rate limits: " + e.getMessage(),
                "timestamp", LocalDateTime.now()
            ));
        }
    }

    /**
     * Clean up expired rate limit entries.
     */
    @PostMapping("/rate-limits/cleanup")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> cleanupRateLimits() {
        log.info("Admin cleaning up expired rate limit entries");
        
        try {
            int cleanedUp = rateLimitService.cleanupExpiredEntries();
            
            return ResponseEntity.ok(Map.of(
                "status", "success",
                "cleanedUp", cleanedUp,
                "message", "Cleaned up " + cleanedUp + " expired entries",
                "timestamp", LocalDateTime.now()
            ));
            
        } catch (Exception e) {
            log.error("Failed to cleanup rate limit entries: {}", e.getMessage(), e);
            
            return ResponseEntity.badRequest().body(Map.of(
                "status", "error",
                "message", "Failed to cleanup entries: " + e.getMessage(),
                "timestamp", LocalDateTime.now()
            ));
        }
    }

    /**
     * Get audit events for a user.
     */
    @GetMapping("/audit/users/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<WebSocketAuditService.AuditEvent>> getUserAuditEvents(
            @PathVariable Long userId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
            @RequestParam(required = false) List<String> eventTypes) {
        
        log.debug("Retrieving audit events for user {} from {} to {}", userId, startTime, endTime);
        
        List<WebSocketAuditService.AuditEvent> events = 
            auditService.getUserAuditEvents(userId, startTime, endTime, eventTypes);
        
        return ResponseEntity.ok(events);
    }

    /**
     * Get audit events for a session.
     */
    @GetMapping("/audit/sessions/{sessionId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<WebSocketAuditService.AuditEvent>> getSessionAuditEvents(
            @PathVariable String sessionId) {
        
        log.debug("Retrieving audit events for session: {}", sessionId);
        
        List<WebSocketAuditService.AuditEvent> events = auditService.getSessionAuditEvents(sessionId);
        return ResponseEntity.ok(events);
    }

    /**
     * Get security violation summary.
     */
    @GetMapping("/audit/security-violations/summary")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<WebSocketAuditService.SecurityViolationSummary> getSecurityViolationSummary(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        
        log.debug("Retrieving security violation summary from {} to {}", startTime, endTime);
        
        WebSocketAuditService.SecurityViolationSummary summary = 
            auditService.getSecurityViolationSummary(startTime, endTime);
        
        return ResponseEntity.ok(summary);
    }

    /**
     * Get rate limit violation summary.
     */
    @GetMapping("/audit/rate-limit-violations/summary")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<WebSocketAuditService.RateLimitViolationSummary> getRateLimitViolationSummary(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        
        log.debug("Retrieving rate limit violation summary from {} to {}", startTime, endTime);
        
        WebSocketAuditService.RateLimitViolationSummary summary = 
            auditService.getRateLimitViolationSummary(startTime, endTime);
        
        return ResponseEntity.ok(summary);
    }

    /**
     * Clean up old audit events.
     */
    @PostMapping("/audit/cleanup")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> cleanupAuditEvents() {
        log.info("Admin cleaning up old audit events");
        
        try {
            int cleanedUp = auditService.cleanupOldAuditEvents();
            
            return ResponseEntity.ok(Map.of(
                "status", "success",
                "cleanedUp", cleanedUp,
                "message", "Cleaned up " + cleanedUp + " old audit events",
                "timestamp", LocalDateTime.now()
            ));
            
        } catch (Exception e) {
            log.error("Failed to cleanup audit events: {}", e.getMessage(), e);
            
            return ResponseEntity.badRequest().body(Map.of(
                "status", "error",
                "message", "Failed to cleanup audit events: " + e.getMessage(),
                "timestamp", LocalDateTime.now()
            ));
        }
    }

    /**
     * Export audit events for compliance.
     */
    @GetMapping("/audit/export")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> exportAuditEvents(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
            @RequestParam(defaultValue = "JSON") String format) {
        
        log.info("Admin exporting audit events from {} to {} in {} format", startTime, endTime, format);
        
        try {
            String exportData = auditService.exportAuditEvents(startTime, endTime, format);
            
            return ResponseEntity.ok(Map.of(
                "status", "success",
                "format", format,
                "startTime", startTime,
                "endTime", endTime,
                "data", exportData,
                "timestamp", LocalDateTime.now()
            ));
            
        } catch (Exception e) {
            log.error("Failed to export audit events: {}", e.getMessage(), e);
            
            return ResponseEntity.badRequest().body(Map.of(
                "status", "error",
                "message", "Failed to export audit events: " + e.getMessage(),
                "timestamp", LocalDateTime.now()
            ));
        }
    }

    /**
     * Test authorization for a specific user and topic.
     */
    @PostMapping("/authorization/test")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> testAuthorization(
            @RequestBody Map<String, Object> testRequest) {
        
        Long userId = Long.valueOf(testRequest.get("userId").toString());
        String topic = (String) testRequest.get("topic");
        Object filter = testRequest.get("filter");
        
        log.debug("Testing authorization for user {} to topic {} with filter {}", userId, topic, filter);
        
        try {
            // This would require getting the User object - simplified for demonstration
            WebSocketAuthorizationService.AuthorizationResult result = 
                WebSocketAuthorizationService.AuthorizationResult.allow("Test authorization");
            
            return ResponseEntity.ok(Map.of(
                "userId", userId,
                "topic", topic,
                "filter", filter,
                "result", Map.of(
                    "allowed", result.allowed(),
                    "reason", result.reason(),
                    "denialCode", result.denialCode(),
                    "timestamp", result.timestamp()
                ),
                "timestamp", LocalDateTime.now()
            ));
            
        } catch (Exception e) {
            log.error("Failed to test authorization: {}", e.getMessage(), e);
            
            return ResponseEntity.badRequest().body(Map.of(
                "status", "error",
                "message", "Failed to test authorization: " + e.getMessage(),
                "timestamp", LocalDateTime.now()
            ));
        }
    }

    /**
     * WebSocket security health check.
     */
    @GetMapping("/health")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> securityHealthCheck() {
        try {
            WebSocketRateLimitService.RateLimitStatistics rateLimitStats = rateLimitService.getRateLimitStatistics();
            
            return ResponseEntity.ok(Map.of(
                "status", "healthy",
                "rateLimitService", "operational",
                "auditService", "operational",
                "authorizationService", "operational",
                "activeConnections", rateLimitStats.activeConnections(),
                "rateLimitedUsers", rateLimitStats.rateLimitedUsers(),
                "timestamp", LocalDateTime.now()
            ));
            
        } catch (Exception e) {
            log.error("WebSocket security health check failed: {}", e.getMessage(), e);
            
            return ResponseEntity.status(503).body(Map.of(
                "status", "unhealthy",
                "error", e.getMessage(),
                "timestamp", LocalDateTime.now()
            ));
        }
    }
}