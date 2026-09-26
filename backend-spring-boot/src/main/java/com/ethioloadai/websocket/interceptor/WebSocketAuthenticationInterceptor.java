package com.ethioloadai.websocket.interceptor;

import com.ethioloadai.security.jwt.JwtService;
import com.ethioloadai.user.entity.User;
import com.ethioloadai.user.repository.UserRepository;
import com.ethioloadai.websocket.security.WebSocketAuditService;
import com.ethioloadai.websocket.security.WebSocketRateLimitService;
import com.ethioloadai.websocket.security.WebSocketSecurityConfig;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Enhanced WebSocket handshake interceptor with comprehensive security features.
 * 
 * Provides authentication, authorization, rate limiting, IP filtering,
 * and audit logging for WebSocket connections in the GPS tracking system.
 */
@Component
@RequiredArgsConstructor
public class WebSocketAuthenticationInterceptor implements HandshakeInterceptor {
    private static final Logger log = LoggerFactory.getLogger(WebSocketAuthenticationInterceptor.class);

    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final WebSocketSecurityConfig securityConfig;
    private final WebSocketRateLimitService rateLimitService;
    private final WebSocketAuditService auditService;

    // Session attribute keys
    public static final String USER_ID_ATTR = "userId";
    public static final String USERNAME_ATTR = "username";
    public static final String USER_ROLE_ATTR = "userRole";
    public static final String AUTHENTICATED_ATTR = "authenticated";
    public static final String CONNECTION_TIME_ATTR = "connectionTime";
    public static final String IP_ADDRESS_ATTR = "ipAddress";

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                 WebSocketHandler wsHandler, Map<String, Object> attributes) throws Exception {
        
        String eventId = UUID.randomUUID().toString();
        String ipAddress = getClientIpAddress(request);
        String userAgent = getUserAgent(request);
        String endpoint = getEndpoint(request);
        
        try {
            log.debug("WebSocket handshake attempt from IP: {}, endpoint: {}", ipAddress, endpoint);
            
            // Security validation step 1: IP whitelist check (if enabled)
            if (!isIpAllowed(ipAddress)) {
                log.warn("WebSocket connection rejected: IP {} not in whitelist", ipAddress);
                auditConnectionFailure(eventId, null, ipAddress, userAgent, endpoint, 
                                     "IP_NOT_WHITELISTED", "IP address not in whitelist");
                response.setStatusCode(HttpStatus.FORBIDDEN);
                return false;
            }

            // Security validation step 2: Extract and validate JWT token
            String token = extractToken(request);
            if (token == null) {
                log.warn("WebSocket connection rejected: No authentication token provided from IP {}", ipAddress);
                auditConnectionFailure(eventId, null, ipAddress, userAgent, endpoint, 
                                     "NO_TOKEN", "No authentication token provided");
                response.setStatusCode(HttpStatus.UNAUTHORIZED);
                return false;
            }

            // Security validation step 3: JWT token validation
            if (!jwtService.isTokenValid(token)) {
                log.warn("WebSocket connection rejected: Invalid or expired token from IP {}", ipAddress);
                auditConnectionFailure(eventId, null, ipAddress, userAgent, endpoint, 
                                     "INVALID_TOKEN", "Invalid or expired JWT token");
                response.setStatusCode(HttpStatus.UNAUTHORIZED);
                return false;
            }

            // Security validation step 4: User lookup and validation
            String username = jwtService.extractUsername(token);
            Optional<User> userOptional = userRepository.findByEmailOrPhone(username);
            
            if (userOptional.isEmpty()) {
                log.warn("WebSocket connection rejected: User not found for username: {} from IP {}", username, ipAddress);
                auditConnectionFailure(eventId, null, ipAddress, userAgent, endpoint, 
                                     "USER_NOT_FOUND", "User not found for username: " + username);
                response.setStatusCode(HttpStatus.UNAUTHORIZED);
                return false;
            }

            User user = userOptional.get();
            
            // Security validation step 5: User active status check
            if (!Boolean.TRUE.equals(user.getIsActive())) {
                log.warn("WebSocket connection rejected: User {} is not active from IP {}", username, ipAddress);
                auditConnectionFailure(eventId, user.getId(), ipAddress, userAgent, endpoint, 
                                     "USER_INACTIVE", "User account is not active");
                response.setStatusCode(HttpStatus.FORBIDDEN);
                return false;
            }

            // Security validation step 6: Role-based minimum access check
            if (!hasMinimumRole(user)) {
                log.warn("WebSocket connection rejected: User {} does not meet minimum role requirement from IP {}", 
                        username, ipAddress);
                auditConnectionFailure(eventId, user.getId(), ipAddress, userAgent, endpoint, 
                                     "INSUFFICIENT_ROLE", "User does not meet minimum role requirement");
                response.setStatusCode(HttpStatus.FORBIDDEN);
                return false;
            }

            // Security validation step 7: Rate limiting check
            if (!rateLimitService.canConnect(user.getId(), ipAddress)) {
                log.warn("WebSocket connection rejected: Rate limit exceeded for user {} from IP {}", user.getId(), ipAddress);
                auditConnectionFailure(eventId, user.getId(), ipAddress, userAgent, endpoint, 
                                     "RATE_LIMIT_EXCEEDED", "Connection rate limit exceeded");
                response.setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
                return false;
            }

            // Security validation step 8: Geolocation check (if enabled)
            if (securityConfig.getSecurityPolicy().isEnableGeolocationControl()) {
                if (!isLocationAllowed(ipAddress)) {
                    log.warn("WebSocket connection rejected: Geographic location not allowed for IP {}", ipAddress);
                    auditConnectionFailure(eventId, user.getId(), ipAddress, userAgent, endpoint, 
                                         "LOCATION_NOT_ALLOWED", "Geographic location not allowed");
                    response.setStatusCode(HttpStatus.FORBIDDEN);
                    return false;
                }
            }

            // All security checks passed - store user information in session attributes
            LocalDateTime connectionTime = LocalDateTime.now();
            attributes.put(USER_ID_ATTR, user.getId());
            attributes.put(USERNAME_ATTR, user.getFullName());
            attributes.put(USER_ROLE_ATTR, user.getRole().name());
            attributes.put(AUTHENTICATED_ATTR, true);
            attributes.put(CONNECTION_TIME_ATTR, connectionTime);
            attributes.put(IP_ADDRESS_ATTR, ipAddress);

            // Record successful connection for rate limiting
            String sessionId = generateSessionId();
            rateLimitService.recordConnection(user.getId(), ipAddress, sessionId, true);

            // Audit successful connection
            auditConnectionSuccess(eventId, user.getId(), sessionId, ipAddress, userAgent, endpoint, user.getRole().name());
            
            // Audit successful authentication
            auditAuthenticationSuccess(eventId, user.getId(), sessionId, ipAddress, user.getRole().name());

            log.info("WebSocket connection authorized for user: {} (ID: {}, Role: {}) from IP: {}", 
                    user.getFullName(), user.getId(), user.getRole(), ipAddress);
            
            return true;

        } catch (Exception e) {
            log.error("WebSocket handshake error from IP {}: {}", ipAddress, e.getMessage(), e);
            auditConnectionFailure(eventId, null, ipAddress, userAgent, endpoint, 
                                 "SYSTEM_ERROR", "System error during handshake: " + e.getMessage());
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR);
            return false;
        }
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                             WebSocketHandler wsHandler, Exception exception) {
        
        String ipAddress = getClientIpAddress(request);
        
        if (exception != null) {
            log.error("WebSocket handshake failed from IP {}: {}", ipAddress, exception.getMessage(), exception);
        } else {
            log.debug("WebSocket handshake completed successfully from IP {}", ipAddress);
        }
    }

    // ── Security Validation Methods ──────────────────────────────────────────

    /**
     * Check if IP address is allowed (whitelist validation).
     */
    private boolean isIpAllowed(String ipAddress) {
        if (!securityConfig.getSecurityPolicy().isEnableIpWhitelist()) {
            return true; // IP whitelist is disabled
        }
        
        List<String> allowedIps = securityConfig.getSecurityPolicy().getAllowedIpAddresses();
        return allowedIps.isEmpty() || allowedIps.contains(ipAddress) || allowedIps.contains("*");
    }

    /**
     * Check if user meets minimum role requirement.
     */
    private boolean hasMinimumRole(User user) {
        String minimumRole = securityConfig.getSecurityPolicy().getMinimumRole();
        
        return switch (minimumRole) {
            case "ADMIN" -> user.getRole() == User.Role.ADMIN;
            case "FLEET_OWNER" -> user.getRole() == User.Role.ADMIN || user.getRole() == User.Role.FLEET_OWNER;
            case "DRIVER" -> user.getRole() != null; // All valid roles
            case "SHIPPER" -> user.getRole() != null;
            default -> true;
        };
    }

    /**
     * Check if geographic location is allowed (basic implementation).
     */
    private boolean isLocationAllowed(String ipAddress) {
        // This is a simplified implementation
        // In production, you would use a proper GeoIP service
        List<String> allowedCountries = securityConfig.getSecurityPolicy().getAllowedCountries();
        
        if (allowedCountries.isEmpty()) {
            return true;
        }
        
        // For now, allow all connections unless specific blocking is needed
        // Real implementation would do GeoIP lookup
        return true;
    }

    // ── Utility Methods ──────────────────────────────────────────────────────

    /**
     * Extract JWT token from request (query parameter or Authorization header).
     */
    private String extractToken(ServerHttpRequest request) {
        // First, try to get token from query parameter (preferred for WebSocket)
        URI uri = request.getURI();
        String query = uri.getQuery();
        
        if (query != null) {
            String[] params = query.split("&");
            for (String param : params) {
                String[] keyValue = param.split("=");
                if (keyValue.length == 2 && "token".equals(keyValue[0])) {
                    return keyValue[1];
                }
            }
        }

        // Fallback to Authorization header
        List<String> authHeaders = request.getHeaders().get("Authorization");
        if (authHeaders != null && !authHeaders.isEmpty()) {
            String authHeader = authHeaders.get(0);
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                return authHeader.substring(7);
            }
        }

        return null;
    }

    /**
     * Get client IP address from request.
     */
    private String getClientIpAddress(ServerHttpRequest request) {
        // Check X-Forwarded-For header for proxy setups
        List<String> xForwardedFor = request.getHeaders().get("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            String forwardedIp = xForwardedFor.get(0);
            if (forwardedIp != null && !forwardedIp.trim().isEmpty()) {
                // Take first IP in case of multiple proxies
                return forwardedIp.split(",")[0].trim();
            }
        }
        
        // Check X-Real-IP header
        List<String> xRealIp = request.getHeaders().get("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp.get(0);
        }
        
        // Fallback to remote address
        return request.getRemoteAddress() != null ? 
               request.getRemoteAddress().getAddress().getHostAddress() : "unknown";
    }

    /**
     * Get user agent from request.
     */
    private String getUserAgent(ServerHttpRequest request) {
        List<String> userAgentHeaders = request.getHeaders().get("User-Agent");
        return userAgentHeaders != null && !userAgentHeaders.isEmpty() ? 
               userAgentHeaders.get(0) : "unknown";
    }

    /**
     * Extract endpoint from request URI.
     */
    private String getEndpoint(ServerHttpRequest request) {
        String path = request.getURI().getPath();
        if (path.contains("/location")) return "location";
        if (path.contains("/notifications")) return "notifications";
        if (path.contains("/fleet")) return "fleet";
        return "unknown";
    }

    /**
     * Generate unique session ID.
     */
    private String generateSessionId() {
        return UUID.randomUUID().toString();
    }

    // ── Audit Logging Methods ────────────────────────────────────────────────

    private void auditConnectionSuccess(String eventId, Long userId, String sessionId, String ipAddress, 
                                      String userAgent, String endpoint, String userRole) {
        if (securityConfig.getAuditLogging().isLogConnections()) {
            WebSocketAuditService.ConnectionAuditEvent event = 
                new WebSocketAuditService.ConnectionAuditEvent(
                    eventId, userId, sessionId, ipAddress, userAgent, endpoint, true, null, 
                    LocalDateTime.now(), Map.of("userRole", userRole)
                );
            auditService.logConnectionEvent(event);
        }
    }

    private void auditConnectionFailure(String eventId, Long userId, String ipAddress, String userAgent, 
                                      String endpoint, String failureCode, String failureReason) {
        if (securityConfig.getAuditLogging().isLogConnections()) {
            WebSocketAuditService.ConnectionAuditEvent event = 
                new WebSocketAuditService.ConnectionAuditEvent(
                    eventId, userId, null, ipAddress, userAgent, endpoint, false, failureReason, 
                    LocalDateTime.now(), Map.of("failureCode", failureCode)
                );
            auditService.logConnectionEvent(event);
        }
    }

    private void auditAuthenticationSuccess(String eventId, Long userId, String sessionId, 
                                          String ipAddress, String userRole) {
        if (securityConfig.getAuditLogging().isLogConnections()) {
            WebSocketAuditService.AuthenticationAuditEvent event = 
                new WebSocketAuditService.AuthenticationAuditEvent(
                    eventId, userId, sessionId, ipAddress, "JWT", true, null, userRole,
                    LocalDateTime.now(), Map.of()
                );
            auditService.logAuthenticationEvent(event);
        }
    }
}