package com.ethioloadai.websocket.security;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Configuration properties for WebSocket security settings.
 * 
 * Provides centralized configuration for rate limiting, connection limits,
 * and security policies for real-time GPS tracking WebSocket connections.
 */
@Configuration
@ConfigurationProperties(prefix = "websocket.security")
@Data
public class WebSocketSecurityConfig {

    /**
     * Rate limiting configuration.
     */
    private RateLimit rateLimit = new RateLimit();

    /**
     * Connection limit configuration.
     */
    private ConnectionLimit connectionLimit = new ConnectionLimit();

    /**
     * Security policies configuration.
     */
    private SecurityPolicy securityPolicy = new SecurityPolicy();

    /**
     * Audit logging configuration.
     */
    private AuditLogging auditLogging = new AuditLogging();

    @Data
    public static class RateLimit {
        /**
         * Maximum messages per user per minute.
         */
        private int messagesPerMinute = 60;

        /**
         * Maximum subscription requests per user per minute.
         */
        private int subscriptionsPerMinute = 10;

        /**
         * Maximum location updates per trip per minute.
         */
        private int locationUpdatesPerMinute = 120;

        /**
         * Rate limit window duration.
         */
        private Duration windowDuration = Duration.ofMinutes(1);

        /**
         * Rate limit violation penalty duration.
         */
        private Duration penaltyDuration = Duration.ofMinutes(5);
    }

    @Data
    public static class ConnectionLimit {
        /**
         * Maximum concurrent connections per user.
         */
        private int maxConnectionsPerUser = 3;

        /**
         * Maximum concurrent connections per IP address.
         */
        private int maxConnectionsPerIp = 10;

        /**
         * Maximum total concurrent connections.
         */
        private int maxTotalConnections = 1000;

        /**
         * Connection idle timeout.
         */
        private Duration idleTimeout = Duration.ofMinutes(30);
    }

    @Data
    public static class SecurityPolicy {
        /**
         * Allowed origins for WebSocket connections.
         */
        private List<String> allowedOrigins = List.of("*");

        /**
         * Required minimum role for WebSocket access.
         */
        private String minimumRole = "DRIVER";

        /**
         * Topic access rules by role.
         */
        private Map<String, List<String>> roleTopicAccess = Map.of(
            "ADMIN", List.of("*"),
            "FLEET_OWNER", List.of("fleet.*", "trip.*", "vehicle.*", "user.*"),
            "DRIVER", List.of("trip.*", "user.*", "*.driver.*"),
            "SHIPPER", List.of("trip.*", "user.*", "*.shipper.*")
        );

        /**
         * Enable IP-based access control.
         */
        private boolean enableIpWhitelist = false;

        /**
         * Allowed IP addresses (when IP whitelist is enabled).
         */
        private List<String> allowedIpAddresses = List.of();

        /**
         * Enable geolocation-based access control.
         */
        private boolean enableGeolocationControl = false;

        /**
         * Allowed countries for connections.
         */
        private List<String> allowedCountries = List.of("ET"); // Ethiopia
    }

    @Data
    public static class AuditLogging {
        /**
         * Enable audit logging for WebSocket events.
         */
        private boolean enabled = true;

        /**
         * Log connection events (connect, disconnect).
         */
        private boolean logConnections = true;

        /**
         * Log subscription events (subscribe, unsubscribe).
         */
        private boolean logSubscriptions = true;

        /**
         * Log message events (send, receive).
         */
        private boolean logMessages = false; // Can be verbose

        /**
         * Log security violations.
         */
        private boolean logSecurityViolations = true;

        /**
         * Log rate limit violations.
         */
        private boolean logRateLimitViolations = true;

        /**
         * Audit log retention period.
         */
        private Duration retentionPeriod = Duration.ofDays(90);
    }
}