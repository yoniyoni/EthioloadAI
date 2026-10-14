package com.ethioloadai.websocket.security;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Implementation of WebSocket rate limiting service.
 * 
 * Uses in-memory counters with sliding window approach for rate limiting.
 * In production, consider using Redis for distributed rate limiting.
 */
@Service
@RequiredArgsConstructor
public class WebSocketRateLimitServiceImpl implements WebSocketRateLimitService {
    private static final Logger log = LoggerFactory.getLogger(WebSocketRateLimitServiceImpl.class);

    private final WebSocketSecurityConfig securityConfig;

    // Rate limiting counters: userId -> RateLimitEntry
    private final ConcurrentHashMap<Long, UserRateLimitEntry> userRateLimits = new ConcurrentHashMap<>();
    
    // IP-based connection limits: ipAddress -> ConnectionEntry
    private final ConcurrentHashMap<String, IpConnectionEntry> ipConnections = new ConcurrentHashMap<>();
    
    // Trip-based location update limits: tripId -> LocationUpdateEntry
    private final ConcurrentHashMap<Long, LocationUpdateEntry> tripLocationUpdates = new ConcurrentHashMap<>();
    private final Map<String, ActiveConnection> activeConnectionSessions = new HashMap<>();
    
    // Global statistics
    private final AtomicInteger totalConnections = new AtomicInteger(0);
    private final AtomicInteger activeConnections = new AtomicInteger(0);
    private final AtomicInteger rateLimitedUsers = new AtomicInteger(0);
    private volatile LocalDateTime lastCleanup = LocalDateTime.now();

    @Override
    public synchronized boolean canConnect(Long userId, String ipAddress) {
        LocalDateTime now = LocalDateTime.now();
        
        // Check global connection limit
        if (activeConnections.get() >= securityConfig.getConnectionLimit().getMaxTotalConnections()) {
            log.warn("Connection denied for user {} from IP {}: Global connection limit exceeded", userId, ipAddress);
            return false;
        }

        // Check user connection limit
        UserRateLimitEntry userEntry = getUserRateLimitEntry(userId);
        if (userEntry.activeConnections >= securityConfig.getConnectionLimit().getMaxConnectionsPerUser()) {
            log.warn("Connection denied for user {}: User connection limit exceeded", userId);
            return false;
        }
        
        // Check IP connection limit
        IpConnectionEntry ipEntry = getIpConnectionEntry(ipAddress);
        if (ipEntry.activeConnections >= securityConfig.getConnectionLimit().getMaxConnectionsPerIp()) {
            log.warn("Connection denied for user {} from IP {}: IP connection limit exceeded", userId, ipAddress);
            return false;
        }
        
        // Check if user is currently rate limited
        if (isUserRateLimited(userEntry, now)) {
            log.warn("Connection denied for user {}: User is rate limited", userId);
            return false;
        }
        
        return true;
    }

    @Override
    public synchronized boolean tryAcquireConnection(Long userId, String ipAddress, String connectionId) {
        if (activeConnectionSessions.containsKey(connectionId) || !canConnect(userId, ipAddress)) {
            return false;
        }

        LocalDateTime now = LocalDateTime.now();
        UserRateLimitEntry userEntry = getUserRateLimitEntry(userId);
        IpConnectionEntry ipEntry = getIpConnectionEntry(ipAddress);

        activeConnectionSessions.put(connectionId, new ActiveConnection(userId, ipAddress));
        totalConnections.incrementAndGet();
        activeConnections.incrementAndGet();
        userEntry.activeConnections++;
        userEntry.lastActivity = now;
        ipEntry.activeConnections++;
        ipEntry.lastActivity = now;
        log.debug("WebSocket connection acquired: user={}, connection={}, ip={}", userId, connectionId, ipAddress);
        return true;
    }

    @Override
    public synchronized boolean releaseConnection(String connectionId) {
        ActiveConnection connection = activeConnectionSessions.remove(connectionId);
        if (connection == null) {
            return false;
        }

        LocalDateTime now = LocalDateTime.now();
        UserRateLimitEntry userEntry = userRateLimits.get(connection.userId());
        if (userEntry != null) {
            userEntry.activeConnections = Math.max(0, userEntry.activeConnections - 1);
            userEntry.lastActivity = now;
        }

        IpConnectionEntry ipEntry = ipConnections.get(connection.ipAddress());
        if (ipEntry != null) {
            ipEntry.activeConnections = Math.max(0, ipEntry.activeConnections - 1);
            ipEntry.lastActivity = now;
        }

        activeConnections.updateAndGet(count -> Math.max(0, count - 1));
        log.debug("WebSocket connection released: connection={}, user={}, ip={}",
                connectionId, connection.userId(), connection.ipAddress());
        return true;
    }

    @Override
    public boolean canSendMessage(Long userId, String sessionId) {
        LocalDateTime now = LocalDateTime.now();
        UserRateLimitEntry entry = getUserRateLimitEntry(userId);
        
        cleanupExpiredCounts(entry, now);
        
        if (entry.messageCount >= securityConfig.getRateLimit().getMessagesPerMinute()) {
            log.warn("Message denied for user {} session {}: Rate limit exceeded", userId, sessionId);
            markUserAsRateLimited(entry, now, "MESSAGE_RATE_LIMIT");
            return false;
        }
        
        return true;
    }

    @Override
    public boolean canSubscribe(Long userId, String sessionId, String topic) {
        LocalDateTime now = LocalDateTime.now();
        UserRateLimitEntry entry = getUserRateLimitEntry(userId);
        
        cleanupExpiredCounts(entry, now);
        
        if (entry.subscriptionCount >= securityConfig.getRateLimit().getSubscriptionsPerMinute()) {
            log.warn("Subscription denied for user {} session {} to topic {}: Rate limit exceeded", 
                    userId, sessionId, topic);
            markUserAsRateLimited(entry, now, "SUBSCRIPTION_RATE_LIMIT");
            return false;
        }
        
        return true;
    }

    @Override
    public boolean canSendLocationUpdate(Long tripId, Long vehicleId) {
        LocalDateTime now = LocalDateTime.now();
        LocationUpdateEntry entry = getLocationUpdateEntry(tripId);
        
        cleanupExpiredLocationUpdates(entry, now);
        
        if (entry.updateCount >= securityConfig.getRateLimit().getLocationUpdatesPerMinute()) {
            log.warn("Location update denied for trip {} vehicle {}: Rate limit exceeded", tripId, vehicleId);
            return false;
        }
        
        return true;
    }

    @Override
    public void recordMessage(Long userId, String sessionId, String messageType) {
        LocalDateTime now = LocalDateTime.now();
        UserRateLimitEntry entry = getUserRateLimitEntry(userId);
        
        entry.messageCount++;
        entry.lastActivity = now;
        
        log.debug("WebSocket message recorded: user={}, session={}, type={}", userId, sessionId, messageType);
    }

    @Override
    public void recordSubscription(Long userId, String sessionId, String topic, boolean success) {
        LocalDateTime now = LocalDateTime.now();
        UserRateLimitEntry entry = getUserRateLimitEntry(userId);
        
        if (success) {
            entry.subscriptionCount++;
            entry.activeSubscriptions++;
        }
        entry.lastActivity = now;
        
        log.debug("WebSocket subscription recorded: user={}, session={}, topic={}, success={}", 
                 userId, sessionId, topic, success);
    }

    @Override
    public void recordLocationUpdate(Long tripId, Long vehicleId, LocalDateTime timestamp) {
        LocationUpdateEntry entry = getLocationUpdateEntry(tripId);
        entry.updateCount++;
        entry.lastUpdate = timestamp;
        
        log.debug("Location update recorded: trip={}, vehicle={}", tripId, vehicleId);
    }

    @Override
    public synchronized int cleanupExpiredEntries() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime cutoff = now.minus(securityConfig.getRateLimit().getWindowDuration().multipliedBy(2));

        java.util.concurrent.atomic.AtomicInteger cleaned = new java.util.concurrent.atomic.AtomicInteger(0);

        // Clean up user rate limit entries
        userRateLimits.entrySet().removeIf(entry -> {
            UserRateLimitEntry rateEntry = entry.getValue();
            if (rateEntry.lastActivity.isBefore(cutoff) && rateEntry.activeConnections == 0) {
                cleaned.incrementAndGet();
                return true;
            }
            return false;
        });

        // Clean up IP connection entries
        ipConnections.entrySet().removeIf(entry -> {
            IpConnectionEntry ipEntry = entry.getValue();
            if (ipEntry.lastActivity.isBefore(cutoff) && ipEntry.activeConnections == 0) {
                cleaned.incrementAndGet();
                return true;
            }
            return false;
        });

        // Clean up trip location update entries
        tripLocationUpdates.entrySet().removeIf(entry -> {
            LocationUpdateEntry locationEntry = entry.getValue();
            if (locationEntry.lastUpdate.isBefore(cutoff)) {
                cleaned.incrementAndGet();
                return true;
            }
            return false;
        });
        
        lastCleanup = now;
        int cleanedCount = cleaned.get();
        log.debug("Cleaned up {} expired rate limit entries", cleanedCount);

        return cleanedCount;
    }

    @Override
    public RateLimitStatistics getRateLimitStatistics() {
        LocalDateTime now = LocalDateTime.now();
        
        // Calculate messages per minute
        int messagesThisMinute = userRateLimits.values().stream()
            .mapToInt(entry -> {
                cleanupExpiredCounts(entry, now);
                return entry.messageCount;
            })
            .sum();
        
        // Calculate subscriptions per minute
        int subscriptionsThisMinute = userRateLimits.values().stream()
            .mapToInt(entry -> {
                cleanupExpiredCounts(entry, now);
                return entry.subscriptionCount;
            })
            .sum();
        
        // Calculate location updates per minute
        int locationUpdatesThisMinute = tripLocationUpdates.values().stream()
            .mapToInt(entry -> {
                cleanupExpiredLocationUpdates(entry, now);
                return entry.updateCount;
            })
            .sum();
        
        // Count rate limited users
        int currentRateLimitedUsers = (int) userRateLimits.values().stream()
            .filter(entry -> isUserRateLimited(entry, now))
            .count();
        
        rateLimitedUsers.set(currentRateLimitedUsers);
        
        return new RateLimitStatistics(
            totalConnections.get(),
            activeConnections.get(),
            rateLimitedUsers.get(),
            messagesThisMinute,
            subscriptionsThisMinute,
            locationUpdatesThisMinute,
            lastCleanup
        );
    }

    @Override
    public void resetUserRateLimits(Long userId) {
        UserRateLimitEntry entry = userRateLimits.get(userId);
        if (entry != null) {
            entry.messageCount = 0;
            entry.subscriptionCount = 0;
            entry.rateLimitedUntil = null;
            entry.rateLimitReason = null;
            entry.windowStart = LocalDateTime.now();
            
            log.info("Reset rate limits for user: {}", userId);
        }
    }

    @Override
    public RateLimitStatus getUserRateLimitStatus(Long userId) {
        LocalDateTime now = LocalDateTime.now();
        UserRateLimitEntry entry = userRateLimits.get(userId);
        
        if (entry == null) {
            return new RateLimitStatus(userId, false, null, null, 0, 0, 0, now);
        }
        
        cleanupExpiredCounts(entry, now);
        
        boolean isRateLimited = isUserRateLimited(entry, now);
        
        return new RateLimitStatus(
            userId,
            isRateLimited,
            entry.rateLimitReason,
            entry.rateLimitedUntil,
            entry.activeConnections,
            entry.messageCount,
            entry.subscriptionCount,
            entry.windowStart
        );
    }

    // ── Private Helper Methods ───────────────────────────────────────────────

    private UserRateLimitEntry getUserRateLimitEntry(Long userId) {
        return userRateLimits.computeIfAbsent(userId, id -> new UserRateLimitEntry());
    }

    private IpConnectionEntry getIpConnectionEntry(String ipAddress) {
        return ipConnections.computeIfAbsent(ipAddress, ip -> new IpConnectionEntry());
    }

    private LocationUpdateEntry getLocationUpdateEntry(Long tripId) {
        return tripLocationUpdates.computeIfAbsent(tripId, id -> new LocationUpdateEntry());
    }

    private void cleanupExpiredCounts(UserRateLimitEntry entry, LocalDateTime now) {
        long windowDurationMinutes = securityConfig.getRateLimit().getWindowDuration().toMinutes();
        
        if (entry.windowStart.isBefore(now.minus(windowDurationMinutes, ChronoUnit.MINUTES))) {
            entry.messageCount = 0;
            entry.subscriptionCount = 0;
            entry.windowStart = now;
        }
    }

    private void cleanupExpiredLocationUpdates(LocationUpdateEntry entry, LocalDateTime now) {
        long windowDurationMinutes = securityConfig.getRateLimit().getWindowDuration().toMinutes();
        
        if (entry.windowStart.isBefore(now.minus(windowDurationMinutes, ChronoUnit.MINUTES))) {
            entry.updateCount = 0;
            entry.windowStart = now;
        }
    }

    private boolean isUserRateLimited(UserRateLimitEntry entry, LocalDateTime now) {
        return entry.rateLimitedUntil != null && entry.rateLimitedUntil.isAfter(now);
    }

    private void markUserAsRateLimited(UserRateLimitEntry entry, LocalDateTime now, String reason) {
        entry.rateLimitedUntil = now.plus(securityConfig.getRateLimit().getPenaltyDuration());
        entry.rateLimitReason = reason;
        
        log.warn("User {} rate limited until {} for reason: {}", 
                getUserIdFromEntry(entry), entry.rateLimitedUntil, reason);
    }

    private Long getUserIdFromEntry(UserRateLimitEntry entry) {
        // Helper method to find user ID - in practice, you'd store this in the entry
        return userRateLimits.entrySet().stream()
            .filter(e -> e.getValue() == entry)
            .map(e -> e.getKey())
            .findFirst()
            .orElse(null);
    }

    // ── Internal Data Structures ─────────────────────────────────────────────

    private static class UserRateLimitEntry {
        int activeConnections = 0;
        int activeSubscriptions = 0;
        int messageCount = 0;
        int subscriptionCount = 0;
        LocalDateTime windowStart = LocalDateTime.now();
        LocalDateTime lastActivity = LocalDateTime.now();
        LocalDateTime rateLimitedUntil = null;
        String rateLimitReason = null;
    }

    private static class IpConnectionEntry {
        int activeConnections = 0;
        LocalDateTime lastActivity = LocalDateTime.now();
    }

    private static class LocationUpdateEntry {
        int updateCount = 0;
        LocalDateTime windowStart = LocalDateTime.now();
        LocalDateTime lastUpdate = LocalDateTime.now();
    }

    private record ActiveConnection(Long userId, String ipAddress) {}
}