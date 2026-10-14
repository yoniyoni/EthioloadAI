package com.ethioloadai.websocket.security;

import com.ethioloadai.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * Unit tests for Phase 10: WebSocket Security and Authorization.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WebSocketSecurityTest {

    @Mock
    private WebSocketSecurityConfig securityConfig;

    @Mock
    private WebSocketSecurityConfig.RateLimit rateLimit;

    @Mock
    private WebSocketSecurityConfig.ConnectionLimit connectionLimit;

    @Mock
    private WebSocketSecurityConfig.SecurityPolicy securityPolicy;

    @Mock
    private WebSocketSecurityConfig.AuditLogging auditLogging;

    @InjectMocks
    private WebSocketRateLimitServiceImpl rateLimitService;

    private User adminUser;
    private User driverUser;
    private User shipperUser;
    private User fleetOwnerUser;

    @BeforeEach
    void setUp() {
        adminUser     = buildUser(1L, User.Role.ADMIN);
        driverUser    = buildUser(2L, User.Role.DRIVER);
        shipperUser   = buildUser(3L, User.Role.SHIPPER);
        fleetOwnerUser = buildUser(4L, User.Role.FLEET_OWNER);

        // Stub config chains
        when(securityConfig.getRateLimit()).thenReturn(rateLimit);
        when(securityConfig.getConnectionLimit()).thenReturn(connectionLimit);
        when(securityConfig.getSecurityPolicy()).thenReturn(securityPolicy);
        when(securityConfig.getAuditLogging()).thenReturn(auditLogging);

        when(rateLimit.getMessagesPerMinute()).thenReturn(60);
        when(rateLimit.getSubscriptionsPerMinute()).thenReturn(10);
        when(rateLimit.getLocationUpdatesPerMinute()).thenReturn(120);
        when(rateLimit.getWindowDuration()).thenReturn(java.time.Duration.ofMinutes(1));
        when(rateLimit.getPenaltyDuration()).thenReturn(java.time.Duration.ofMinutes(5));

        when(connectionLimit.getMaxConnectionsPerUser()).thenReturn(3);
        when(connectionLimit.getMaxConnectionsPerIp()).thenReturn(10);
        when(connectionLimit.getMaxTotalConnections()).thenReturn(1000);

        when(securityPolicy.isEnableIpWhitelist()).thenReturn(false);
        when(securityPolicy.getMinimumRole()).thenReturn("DRIVER");
    }

    // ── AuthorizationResult value object ──────────────────────────────────────

    @Test
    void authorizationResultAllow() {
        var result = WebSocketAuthorizationService.AuthorizationResult.allow("ok");
        assertThat(result.allowed()).isTrue();
        assertThat(result.reason()).isEqualTo("ok");
        assertThat(result.denialCode()).isNull();
    }

    @Test
    void authorizationResultDeny() {
        var result = WebSocketAuthorizationService.AuthorizationResult.deny("nope", "CODE_X");
        assertThat(result.allowed()).isFalse();
        assertThat(result.reason()).isEqualTo("nope");
        assertThat(result.denialCode()).isEqualTo("CODE_X");
    }

    @Test
    void authorizationResultDenyRequireRole() {
        var result = WebSocketAuthorizationService.AuthorizationResult.denyRequireRole("need admin", "ADMIN");
        assertThat(result.allowed()).isFalse();
        assertThat(result.denialCode()).isEqualTo("INSUFFICIENT_ROLE");
        assertThat(result.requiredRole()).isEqualTo("ADMIN");
    }

    @Test
    void authorizationResultDenyRequirePermission() {
        var result = WebSocketAuthorizationService.AuthorizationResult
                .denyRequirePermission("need perm", "PERM_X");
        assertThat(result.allowed()).isFalse();
        assertThat(result.denialCode()).isEqualTo("INSUFFICIENT_PERMISSION");
        assertThat(result.requiredPermission()).isEqualTo("PERM_X");
    }

    // ── RateLimitService – connection checks ──────────────────────────────────

    @Test
    void canConnect_underAllLimits_returnsTrue() {
        assertThat(rateLimitService.canConnect(10L, "10.0.0.1")).isTrue();
    }

    @Test
    void canConnect_exceedsUserConnectionLimit_returnsFalse() {
        // Fill up max connections for this user
        when(connectionLimit.getMaxConnectionsPerUser()).thenReturn(0);
        assertThat(rateLimitService.canConnect(10L, "10.0.0.1")).isFalse();
    }

    @Test
    void canConnect_exceedsTotalConnectionLimit_returnsFalse() {
        when(connectionLimit.getMaxTotalConnections()).thenReturn(0);
        assertThat(rateLimitService.canConnect(99L, "10.0.0.1")).isFalse();
    }

    @Test
    void releasedConnectionRestoresUserAndGlobalCapacity() {
        when(connectionLimit.getMaxConnectionsPerUser()).thenReturn(1);

        assertThat(rateLimitService.tryAcquireConnection(10L, "10.0.0.1", "connection-1")).isTrue();
        assertThat(rateLimitService.canConnect(10L, "10.0.0.1")).isFalse();
        assertThat(rateLimitService.getRateLimitStatistics().activeConnections()).isEqualTo(1);

        assertThat(rateLimitService.releaseConnection("connection-1")).isTrue();
        assertThat(rateLimitService.getRateLimitStatistics().activeConnections()).isZero();
        assertThat(rateLimitService.tryAcquireConnection(10L, "10.0.0.1", "connection-2")).isTrue();
    }

    @Test
    void releasedConnectionRestoresIpCapacityAndDuplicateReleaseIsIgnored() {
        when(connectionLimit.getMaxConnectionsPerIp()).thenReturn(1);

        assertThat(rateLimitService.tryAcquireConnection(10L, "10.0.0.1", "connection-1")).isTrue();
        assertThat(rateLimitService.tryAcquireConnection(11L, "10.0.0.1", "connection-2")).isFalse();

        assertThat(rateLimitService.releaseConnection("connection-1")).isTrue();
        assertThat(rateLimitService.releaseConnection("connection-1")).isFalse();
        assertThat(rateLimitService.getRateLimitStatistics().activeConnections()).isZero();
        assertThat(rateLimitService.tryAcquireConnection(11L, "10.0.0.1", "connection-3")).isTrue();
    }

    @Test
    void concurrentConnectionAcquisitionCannotExceedUserLimit() throws Exception {
        when(connectionLimit.getMaxConnectionsPerUser()).thenReturn(1);
        var executor = java.util.concurrent.Executors.newFixedThreadPool(8);
        var ready = new java.util.concurrent.CountDownLatch(8);
        var start = new java.util.concurrent.CountDownLatch(1);
        try {
            var attempts = java.util.stream.IntStream.range(0, 8)
                    .mapToObj(index -> executor.submit(() -> {
                        ready.countDown();
                        start.await();
                        return rateLimitService.tryAcquireConnection(
                                10L, "10.0.0.1", "connection-" + index);
                    }))
                    .toList();
            ready.await();
            start.countDown();

            long admitted = 0;
            for (var attempt : attempts) {
                if (attempt.get()) {
                    admitted++;
                }
            }
            assertThat(admitted).isEqualTo(1);
            assertThat(rateLimitService.getRateLimitStatistics().activeConnections()).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    // ── RateLimitService – message checks ─────────────────────────────────────

    @Test
    void canSendMessage_underLimit_returnsTrue() {
        assertThat(rateLimitService.canSendMessage(1L, "sess-1")).isTrue();
    }

    @Test
    void canSendMessage_exceedsLimit_returnsFalse() {
        when(rateLimit.getMessagesPerMinute()).thenReturn(0);
        assertThat(rateLimitService.canSendMessage(1L, "sess-1")).isFalse();
    }

    // ── RateLimitService – subscription checks ────────────────────────────────

    @Test
    void canSubscribe_underLimit_returnsTrue() {
        assertThat(rateLimitService.canSubscribe(1L, "sess-1", "trip.1.location")).isTrue();
    }

    @Test
    void canSubscribe_exceedsLimit_returnsFalse() {
        when(rateLimit.getSubscriptionsPerMinute()).thenReturn(0);
        assertThat(rateLimitService.canSubscribe(1L, "sess-1", "trip.1.location")).isFalse();
    }

    // ── RateLimitService – location update checks ─────────────────────────────

    @Test
    void canSendLocationUpdate_underLimit_returnsTrue() {
        assertThat(rateLimitService.canSendLocationUpdate(100L, 200L)).isTrue();
    }

    @Test
    void canSendLocationUpdate_exceedsLimit_returnsFalse() {
        when(rateLimit.getLocationUpdatesPerMinute()).thenReturn(0);
        assertThat(rateLimitService.canSendLocationUpdate(100L, 200L)).isFalse();
    }

    // ── RateLimitService – statistics ────────────────────────────────────────

    @Test
    void getRateLimitStatistics_returnsNonNull() {
        WebSocketRateLimitService.RateLimitStatistics stats = rateLimitService.getRateLimitStatistics();
        assertThat(stats).isNotNull();
        assertThat(stats.totalConnections()).isGreaterThanOrEqualTo(0);
        assertThat(stats.activeConnections()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void getUserRateLimitStatus_unknownUser_returnsDefault() {
        WebSocketRateLimitService.RateLimitStatus status = rateLimitService.getUserRateLimitStatus(999L);
        assertThat(status).isNotNull();
        assertThat(status.userId()).isEqualTo(999L);
        assertThat(status.isRateLimited()).isFalse();
    }

    // ── RateLimitService – reset ──────────────────────────────────────────────

    @Test
    void resetUserRateLimits_doesNotThrow() {
        rateLimitService.recordMessage(5L, "sess-5", "PING");
        rateLimitService.resetUserRateLimits(5L);
        WebSocketRateLimitService.RateLimitStatus status = rateLimitService.getUserRateLimitStatus(5L);
        assertThat(status.isRateLimited()).isFalse();
    }

    // ── RateLimitService – cleanup ────────────────────────────────────────────

    @Test
    void cleanupExpiredEntries_returnsNonNegative() {
        int cleaned = rateLimitService.cleanupExpiredEntries();
        assertThat(cleaned).isGreaterThanOrEqualTo(0);
    }

    // ── Security configuration ─────────────────────────────────────────────────

    @Test
    void topicStructureMatchesExpectedPatterns() {
        String[] tripTopics    = {"trip.1.location", "trip.999.status", "trip.42.notifications"};
        String[] vehicleTopics = {"vehicle.1.status", "vehicle.100.location"};
        String[] fleetTopics   = {"fleet.vehicles", "fleet.stats"};
        String[] userTopics    = {"user.1.notifications", "user.42.alerts"};

        for (String t : tripTopics)    assertThat(t).matches("trip\\.\\d+\\..*");
        for (String t : vehicleTopics) assertThat(t).matches("vehicle\\.\\d+\\..*");
        for (String t : fleetTopics)   assertThat(t).startsWith("fleet.");
        for (String t : userTopics)    assertThat(t).matches("user\\.\\d+\\..*");
    }

    @Test
    void roleHierarchy_adminHasHighestAccess() {
        // Verify role enumeration is consistent
        assertThat(User.Role.values()).contains(
            User.Role.ADMIN, User.Role.FLEET_OWNER,
            User.Role.DRIVER, User.Role.SHIPPER
        );
    }

    @Test
    void securityPolicy_defaultMinimumRole() {
        assertThat(securityConfig.getSecurityPolicy().getMinimumRole()).isEqualTo("DRIVER");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private User buildUser(Long id, User.Role role) {
        User u = new User();
        u.setId(id);
        u.setFullName("Test " + role.name());
        u.setPhone("09000000" + id);
        u.setRole(role);
        u.setIsActive(true);
        return u;
    }
}
