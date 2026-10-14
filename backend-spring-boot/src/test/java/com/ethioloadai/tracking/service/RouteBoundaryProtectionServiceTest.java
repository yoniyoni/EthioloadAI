package com.ethioloadai.tracking.service;

import com.ethioloadai.trip.entity.Trip;
import com.ethioloadai.trip.repository.TripRepository;
import com.ethioloadai.trip.service.RouteDataService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Route Boundary Protection Service Tests")
class RouteBoundaryProtectionServiceTest {

    @Mock
    private TripRepository tripRepository;
    
    @Mock
    private RouteDataService routeDataService;

    @Mock
    private EntityManager entityManager;

    @Mock
    private JdbcTemplate jdbcTemplate;
    
    @InjectMocks
    private RouteBoundaryProtectionServiceImpl boundaryProtectionService;
    
    private Trip testTrip;
    private List<Map<String, Object>> sampleRouteData;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(boundaryProtectionService, "entityManager", entityManager);
        testTrip = Trip.builder()
            .id(1L)
            .tripType(Trip.TripType.SINGLE)
            .routeData(new ArrayList<>())
            .build();
        lenient().when(tripRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(testTrip));
        lenient().doNothing().when(entityManager).refresh(testTrip, LockModeType.PESSIMISTIC_WRITE);
        
        sampleRouteData = createSampleRouteData(600); // Exceeds default limit
        testTrip.setRouteData(sampleRouteData);
    }

    @Nested
    @DisplayName("Boundary Check Operations")
    class BoundaryCheckOperations {

        @Test
        @DisplayName("Should detect when boundaries are exceeded")
        void shouldDetectBoundariesExceeded() {
            // Act
            RouteBoundaryProtectionService.BoundaryCheckResult result = 
                boundaryProtectionService.checkRouteBoundaries(testTrip);

            // Assert
            assertTrue(result.exceedsLimit());
            assertEquals(600, result.currentPointCount());
            assertEquals(500, result.maxAllowedPoints()); // Default single trip limit
            assertEquals("CRITICAL", result.riskLevel());
            assertTrue(result.utilizationPercent() > 100);
            assertFalse(result.concerns().isEmpty());
        }

        @Test
        @DisplayName("Should show safe status for trips within limits")
        void shouldShowSafeStatusWithinLimits() {
            // Arrange
            testTrip.setRouteData(createSampleRouteData(100));

            // Act
            RouteBoundaryProtectionService.BoundaryCheckResult result = 
                boundaryProtectionService.checkRouteBoundaries(testTrip);

            // Assert
            assertFalse(result.exceedsLimit());
            assertEquals(100, result.currentPointCount());
            assertEquals("LOW", result.riskLevel());
            assertTrue(result.utilizationPercent() < 50);
        }

        @Test
        @DisplayName("Should handle empty route data")
        void shouldHandleEmptyRouteData() {
            // Arrange
            testTrip.setRouteData(new ArrayList<>());

            // Act
            RouteBoundaryProtectionService.BoundaryCheckResult result = 
                boundaryProtectionService.checkRouteBoundaries(testTrip);

            // Assert
            assertFalse(result.exceedsLimit());
            assertEquals(0, result.currentPointCount());
            assertEquals("LOW", result.riskLevel());
            assertEquals(0.0, result.utilizationPercent());
        }
    }

    @Nested
    @DisplayName("Boundary Enforcement Operations")
    class BoundaryEnforcementOperations {

        @Test
        @DisplayName("Should perform no action when boundaries are not exceeded")
        void shouldPerformNoActionWhenWithinBounds() {
            // Arrange
            testTrip.setRouteData(createSampleRouteData(100));

            // Act
            RouteBoundaryProtectionService.BoundaryEnforcementResult result = 
                boundaryProtectionService.enforceRouteBoundaries(testTrip);

            // Assert
            assertFalse(result.boundariesExceeded());
            assertEquals("NO_ACTION_NEEDED", result.status());
            assertTrue(result.actionsPerformed().isEmpty());
            assertEquals(0, result.pointsRemoved());
            assertEquals(0, result.pointsArchived());
        }

        @Test
        @DisplayName("Should perform archival when threshold exceeded")
        void shouldPerformArchivalWhenThresholdExceeded() {
            // Arrange
            when(tripRepository.findById(1L)).thenReturn(Optional.of(testTrip));
            when(tripRepository.save(any(Trip.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // Act
            RouteBoundaryProtectionService.BoundaryEnforcementResult result = 
                boundaryProtectionService.enforceRouteBoundaries(testTrip);

            // Assert
            assertTrue(result.boundariesExceeded());
            assertTrue(result.actionsPerformed().contains("ARCHIVED_OLD_DATA"));
            assertTrue(result.pointsArchived() > 0);
            assertEquals("BOUNDARIES_ENFORCED", result.status());
        }

        @Test
        @DisplayName("Should perform compression after archival if still needed")
        void shouldPerformCompressionAfterArchival() {
            // Arrange
            when(tripRepository.findById(1L)).thenReturn(Optional.of(testTrip));
            when(tripRepository.save(any(Trip.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // Act
            RouteBoundaryProtectionService.BoundaryEnforcementResult result = 
                boundaryProtectionService.enforceRouteBoundaries(testTrip);

            // Assert
            assertTrue(result.actionsPerformed().contains("ARCHIVED_OLD_DATA"));
            // May also contain compression depending on remaining points
        }

        @Test
        @DisplayName("Internal enforcement can still invoke compression")
        void internalEnforcementCanInvokeCompression() {
            boundaryProtectionService.configureBoundarySettings(
                Trip.TripType.SINGLE,
                new RouteBoundaryProtectionService.BoundarySettings(
                    500, 700, 100, 1000, 24, 0.001,
                    true, false, false, "DOUGLAS_PEUCKER", List.of()));
            when(tripRepository.save(any(Trip.class))).thenAnswer(invocation -> invocation.getArgument(0));

            RouteBoundaryProtectionService.BoundaryEnforcementResult result =
                boundaryProtectionService.enforceRouteBoundaries(testTrip);

            assertTrue(result.actionsPerformed().contains("COMPRESSED_ROUTE"));
            verify(tripRepository, atLeastOnce()).save(testTrip);
        }
    }

    @Nested
    @DisplayName("Route Compression Operations")
    class RouteCompressionOperations {

        @Test
        @DisplayName("Should compress route and reduce point count")
        void shouldCompressRouteAndReducePointCount() {
            // Arrange
            when(tripRepository.save(any(Trip.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // Act
            RouteBoundaryProtectionService.RouteCompressionResult result = 
                boundaryProtectionService.compressRoute(testTrip, 5);

            // Assert
            assertEquals(600, result.originalPoints());
            assertTrue(result.compressedPoints() < result.originalPoints());
            assertTrue(result.pointsRemoved() > 0);
            assertTrue(result.compressionRatio() < 1.0);
            assertEquals("DOUGLAS_PEUCKER", result.algorithm());
            assertTrue(result.processingTimeMs() >= 0);
        }

        @Test
        @DisplayName("Should handle trips with insufficient points for compression")
        void shouldHandleInsufficientPointsForCompression() {
            // Arrange
            testTrip.setRouteData(createSampleRouteData(5));

            // Act
            RouteBoundaryProtectionService.RouteCompressionResult result = 
                boundaryProtectionService.compressRoute(testTrip, 5);

            // Assert
            assertEquals(5, result.originalPoints());
            assertEquals(5, result.compressedPoints());
            assertEquals(0, result.pointsRemoved());
            assertEquals(1.0, result.compressionRatio());
            assertEquals("NONE", result.algorithm());
        }

        @Test
        @DisplayName("Should adjust compression based on level")
        void shouldAdjustCompressionBasedOnLevel() {
            // Arrange
            when(tripRepository.save(any(Trip.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // Act - Light compression
            RouteBoundaryProtectionService.RouteCompressionResult lightResult = 
                boundaryProtectionService.compressRoute(testTrip, 2);
            
            // Reset trip data
            testTrip.setRouteData(createSampleRouteData(600));
            
            // Act - Heavy compression
            RouteBoundaryProtectionService.RouteCompressionResult heavyResult = 
                boundaryProtectionService.compressRoute(testTrip, 8);

            // Assert
            assertTrue(heavyResult.compressedPoints() <= lightResult.compressedPoints());
            assertTrue(heavyResult.compressionRatio() <= lightResult.compressionRatio());
        }
    }

    @Nested
    @DisplayName("Route Archival Operations")
    class RouteArchivalOperations {

        @Test
        @DisplayName("Should archive old data beyond retention period")
        void shouldArchiveOldDataBeyondRetentionPeriod() {
            // Arrange
            when(tripRepository.save(any(Trip.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // Act
            RouteBoundaryProtectionService.RouteArchivalResult result = 
                boundaryProtectionService.archiveOldRouteData(testTrip, 12); // 12 hours retention

            // Assert
            assertTrue(result.success());
            assertTrue(result.pointsArchived() > 0);
            assertTrue(result.pointsRetained() >= 10); // Minimum points preserved
            assertNotNull(result.retentionCutoff());
            assertTrue(result.storageFreedBytes() > 0);
        }

        @Test
        @DisplayName("Should preserve minimum points even if all are old")
        void shouldPreserveMinimumPointsEvenIfAllOld() {
            // Arrange - All points are old (2 days ago)
            List<Map<String, Object>> oldRouteData = createOldRouteData(50, LocalDateTime.now().minusDays(2));
            testTrip.setRouteData(oldRouteData);
            when(tripRepository.save(any(Trip.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // Act
            RouteBoundaryProtectionService.RouteArchivalResult result = 
                boundaryProtectionService.archiveOldRouteData(testTrip, 12); // 12 hours retention

            // Assert
            assertTrue(result.pointsRetained() >= 10); // Minimum preserved
            assertTrue(result.pointsArchived() >= 0);
        }

        @Test
        @DisplayName("Should handle empty route data gracefully")
        void shouldHandleEmptyRouteDataGracefully() {
            // Arrange
            testTrip.setRouteData(new ArrayList<>());

            // Act
            RouteBoundaryProtectionService.RouteArchivalResult result = 
                boundaryProtectionService.archiveOldRouteData(testTrip, 12);

            // Assert
            assertTrue(result.success());
            assertEquals(0, result.pointsArchived());
            assertEquals(0, result.pointsRetained());
            assertNull(result.oldestArchivedTimestamp());
        }
    }

    @Nested
    @DisplayName("Route Health Metrics")
    class RouteHealthMetrics {

        @Test
        @DisplayName("Should calculate comprehensive health metrics")
        void shouldCalculateComprehensiveHealthMetrics() {
            // Act
            RouteBoundaryProtectionService.RouteHealthMetrics metrics = 
                boundaryProtectionService.getRouteHealthMetrics(testTrip);

            // Assert
            assertEquals(600, metrics.totalPoints());
            assertTrue(metrics.averagePointsPerHour() > 0);
            assertTrue(metrics.storageUsedMB() > 0);
            assertTrue(metrics.queryPerformanceScore() >= 0 && metrics.queryPerformanceScore() <= 1.0);
            assertTrue(metrics.dataQualityScore() >= 0 && metrics.dataQualityScore() <= 1.0);
            assertNotNull(metrics.oldestPoint());
            assertNotNull(metrics.newestPoint());
            assertNotNull(metrics.overallHealth());
        }

        @Test
        @DisplayName("Should identify health issues for large datasets")
        void shouldIdentifyHealthIssuesForLargeDatasets() {
            // Arrange - Very large dataset
            testTrip.setRouteData(createSampleRouteData(2000));

            // Act
            RouteBoundaryProtectionService.RouteHealthMetrics metrics = 
                boundaryProtectionService.getRouteHealthMetrics(testTrip);

            // Assert
            assertFalse(metrics.healthIssues().isEmpty());
            assertTrue(metrics.healthIssues().stream()
                .anyMatch(issue -> issue.contains("large route dataset")));
            assertTrue(List.of("POOR", "CRITICAL").contains(metrics.overallHealth()));
        }

        @Test
        @DisplayName("Should show excellent health for optimal datasets")
        void shouldShowExcellentHealthForOptimalDatasets() {
            // Arrange - Optimal dataset size
            testTrip.setRouteData(createHighQualityRouteData(150));

            // Act
            RouteBoundaryProtectionService.RouteHealthMetrics metrics = 
                boundaryProtectionService.getRouteHealthMetrics(testTrip);

            // Assert
            assertTrue(metrics.healthIssues().isEmpty());
            assertEquals("EXCELLENT", metrics.overallHealth());
            assertTrue(metrics.queryPerformanceScore() > 0.8);
            assertTrue(metrics.dataQualityScore() > 0.8);
        }
    }

    @Nested
    @DisplayName("Emergency Cleanup Operations")
    class EmergencyCleanupOperations {

        @Test
        @DisplayName("Should perform emergency cleanup for critical datasets")
        void shouldPerformEmergencyCleanupForCriticalDatasets() {
            // Arrange
            testTrip.setRouteData(createSampleRouteData(1500)); // Well above emergency threshold
            when(tripRepository.save(any(Trip.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // Act
            RouteBoundaryProtectionService.EmergencyCleanupResult result = 
                boundaryProtectionService.performEmergencyCleanup(testTrip);

            // Assert
            assertTrue(result.cleanupPerformed());
            assertEquals("RECENT_POINTS_AND_WAYPOINTS", result.cleanupStrategy());
            assertTrue(result.pointsRemoved() > 0);
            assertTrue(result.pointsRetained() <= 100); // Emergency threshold
            assertTrue(result.dataLossPercentage() > 0);
            assertTrue(result.routeIntegrityMaintained());
            verify(jdbcTemplate).update(
                contains("INSERT INTO trip_audit_log"),
                eq(1L),
                eq("ROUTE_EMERGENCY_CLEANUP"),
                contains("source=internal"),
                isNull());
        }

        @Test
        @DisplayName("Should skip cleanup for datasets within limits")
        void shouldSkipCleanupForDatasetsWithinLimits() {
            // Arrange - Small dataset
            testTrip.setRouteData(createSampleRouteData(50));

            // Act
            RouteBoundaryProtectionService.EmergencyCleanupResult result = 
                boundaryProtectionService.performEmergencyCleanup(testTrip);

            // Assert
            assertFalse(result.cleanupPerformed());
            assertEquals("NO_CLEANUP_NEEDED", result.cleanupStrategy());
            assertEquals(0, result.pointsRemoved());
            assertEquals(50, result.pointsRetained());
            assertEquals(0.0, result.dataLossPercentage());
            verifyNoInteractions(jdbcTemplate);
        }

        @Test
        @DisplayName("Should record the administrator on direct cleanup audit events")
        void shouldRecordDirectCleanupActor() {
            testTrip.setRouteData(createSampleRouteData(1500));
            when(tripRepository.save(any(Trip.class))).thenAnswer(invocation -> invocation.getArgument(0));

            RouteBoundaryProtectionService.EmergencyCleanupResult result =
                boundaryProtectionService.performEmergencyCleanup(testTrip, 42L);

            assertTrue(result.cleanupPerformed());
            verify(jdbcTemplate).update(
                contains("INSERT INTO trip_audit_log"),
                eq(1L),
                eq("ROUTE_EMERGENCY_CLEANUP"),
                contains("source=admin"),
                eq(42L));
        }
    }

    @Nested
    @DisplayName("Configuration Management")
    class ConfigurationManagement {

        @Test
        @DisplayName("Should configure boundary settings for trip types")
        void shouldConfigureBoundarySettingsForTripTypes() {
            // Arrange
            RouteBoundaryProtectionService.BoundarySettings customSettings = 
                RouteBoundaryProtectionService.BoundarySettings.conservativeDefaults();

            // Act
            assertDoesNotThrow(() -> 
                boundaryProtectionService.configureBoundarySettings(Trip.TripType.SINGLE, customSettings));

            // Verify settings are applied
            RouteBoundaryProtectionService.BoundaryCheckResult result = 
                boundaryProtectionService.checkRouteBoundaries(testTrip);
            
            // Should use custom settings (conservative = 200 max points)
            assertEquals(200, result.maxAllowedPoints());
        }
    }

    // ── Helper Methods ───────────────────────────────────────────────────────

    private List<Map<String, Object>> createSampleRouteData(int pointCount) {
        List<Map<String, Object>> routeData = new ArrayList<>();
        LocalDateTime baseTime = LocalDateTime.now().minusHours(pointCount / 10); // Spread over time
        
        for (int i = 0; i < pointCount; i++) {
            Map<String, Object> point = new HashMap<>();
            point.put("lat", 9.0 + (i * 0.001)); // Simulate movement
            point.put("lng", 38.7 + (i * 0.001));
            point.put("ts", baseTime.plusMinutes(i * 5).toString()); // Every 5 minutes
            
            if (i % 3 == 0) { // Add some quality data
                point.put("accuracy", 10.0);
                point.put("speed", 50.0);
            }
            
            routeData.add(point);
        }
        
        return routeData;
    }

    private List<Map<String, Object>> createOldRouteData(int pointCount, LocalDateTime baseTime) {
        List<Map<String, Object>> routeData = new ArrayList<>();
        
        for (int i = 0; i < pointCount; i++) {
            Map<String, Object> point = new HashMap<>();
            point.put("lat", 9.0 + (i * 0.001));
            point.put("lng", 38.7 + (i * 0.001));
            point.put("ts", baseTime.plusMinutes(i * 5).toString());
            
            routeData.add(point);
        }
        
        return routeData;
    }

    private List<Map<String, Object>> createHighQualityRouteData(int pointCount) {
        List<Map<String, Object>> routeData = new ArrayList<>();
        LocalDateTime baseTime = LocalDateTime.now().minusHours(2);
        
        for (int i = 0; i < pointCount; i++) {
            Map<String, Object> point = new HashMap<>();
            point.put("lat", 9.0 + (i * 0.001));
            point.put("lng", 38.7 + (i * 0.001));
            point.put("ts", baseTime.plusMinutes(i * 2).toString());
            point.put("accuracy", 5.0); // High quality
            point.put("speed", 45.0);
            point.put("heading", 90.0);
            
            routeData.add(point);
        }
        
        return routeData;
    }
}