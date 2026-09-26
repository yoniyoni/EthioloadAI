package com.ethioloadai.tracking.service;

import com.ethioloadai.trip.entity.Trip;
import com.ethioloadai.trip.service.RouteDataService;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Service for managing route data growth boundaries and preventing unbounded GPS point accumulation.
 * 
 * Implements intelligent data retention policies that balance real-time tracking needs
 * with storage efficiency and query performance.
 */
public interface RouteBoundaryProtectionService {
    
    /**
     * Enforce route data boundaries for a trip.
     * Applies compression, archival, and cleanup strategies as needed.
     * 
     * @param trip trip to enforce boundaries on
     * @return boundary enforcement result with actions taken
     */
    BoundaryEnforcementResult enforceRouteBoundaries(Trip trip);
    
    /**
     * Check if route data exceeds configured limits.
     * 
     * @param trip trip to check
     * @return boundary check result with current metrics
     */
    BoundaryCheckResult checkRouteBoundaries(Trip trip);
    
    /**
     * Apply intelligent route compression to reduce point count while preserving route shape.
     * Uses Douglas-Peucker algorithm with configurable tolerance.
     * 
     * @param trip trip to compress
     * @param compressionLevel compression aggressiveness (1-10, 10 = most aggressive)
     * @return compression result with points removed and quality impact
     */
    RouteCompressionResult compressRoute(Trip trip, int compressionLevel);
    
    /**
     * Archive old route points beyond retention period.
     * Moves old data to separate storage while keeping essential waypoints.
     * 
     * @param trip trip to archive data for
     * @param retentionHours how many hours of data to keep in active route
     * @return archival result with data moved
     */
    RouteArchivalResult archiveOldRouteData(Trip trip, int retentionHours);
    
    /**
     * Get route data health metrics.
     * Provides insights into storage usage, quality, and optimization opportunities.
     * 
     * @param trip trip to analyze
     * @return comprehensive health metrics
     */
    RouteHealthMetrics getRouteHealthMetrics(Trip trip);
    
    /**
     * Apply emergency cleanup for trips with excessive data.
     * Aggressive data reduction for trips exceeding critical thresholds.
     * 
     * @param trip trip requiring emergency cleanup
     * @return emergency cleanup result
     */
    EmergencyCleanupResult performEmergencyCleanup(Trip trip);
    
    /**
     * Get recommended actions for route optimization.
     * AI-driven suggestions for improving route data efficiency.
     * 
     * @param trip trip to analyze
     * @return optimization recommendations
     */
    List<OptimizationRecommendation> getOptimizationRecommendations(Trip trip);
    
    /**
     * Configure boundary protection settings for specific trip types.
     * 
     * @param tripType type of trip (SINGLE, MULTI_STOP, etc.)
     * @param settings boundary protection configuration
     */
    void configureBoundarySettings(Trip.TripType tripType, BoundarySettings settings);
    
    // ── Value Objects ────────────────────────────────────────────────────────

    /**
     * Result of boundary enforcement operation.
     */
    record BoundaryEnforcementResult(
        boolean boundariesExceeded,
        List<String> actionsPerformed,
        int pointsRemoved,
        int pointsArchived,
        int pointsCompressed,
        RouteHealthMetrics healthBefore,
        RouteHealthMetrics healthAfter,
        long processingTimeMs,
        String status
    ) {}

    /**
     * Current boundary check status.
     */
    record BoundaryCheckResult(
        int currentPointCount,
        int maxAllowedPoints,
        boolean exceedsLimit,
        double utilizationPercent,
        String riskLevel, // LOW, MEDIUM, HIGH, CRITICAL
        List<String> concerns,
        BoundarySettings appliedSettings
    ) {}

    /**
     * Route compression operation result.
     */
    record RouteCompressionResult(
        int originalPoints,
        int compressedPoints,
        int pointsRemoved,
        double compressionRatio,
        double qualityRetention, // 0.0 to 1.0
        String algorithm, // DOUGLAS_PEUCKER, TIME_BASED, DISTANCE_BASED
        long processingTimeMs,
        List<String> preservedFeatures // TURNS, STOPS, SPEED_CHANGES
    ) {}

    /**
     * Route archival operation result.
     */
    record RouteArchivalResult(
        int pointsArchived,
        int pointsRetained,
        LocalDateTime oldestArchivedTimestamp,
        LocalDateTime retentionCutoff,
        long storageFreedBytes,
        String archivalLocation,
        boolean success
    ) {}

    /**
     * Comprehensive route health assessment.
     */
    record RouteHealthMetrics(
        int totalPoints,
        int recentPoints, // Last 2 hours
        double averagePointsPerHour,
        double storageUsedMB,
        double queryPerformanceScore, // 0.0 to 1.0
        double dataQualityScore, // 0.0 to 1.0
        LocalDateTime oldestPoint,
        LocalDateTime newestPoint,
        List<String> healthIssues,
        String overallHealth // EXCELLENT, GOOD, FAIR, POOR, CRITICAL
    ) {}

    /**
     * Emergency cleanup operation result.
     */
    record EmergencyCleanupResult(
        boolean cleanupPerformed,
        String cleanupStrategy, // AGGRESSIVE_COMPRESSION, TIME_TRUNCATION, QUALITY_FILTERING
        int pointsRemoved,
        int pointsRetained,
        double dataLossPercentage,
        List<String> preservedFeatures,
        String impactAssessment,
        boolean routeIntegrityMaintained
    ) {}

    /**
     * AI-driven optimization recommendation.
     */
    record OptimizationRecommendation(
        String type, // COMPRESS, ARCHIVE, CLEANUP, SPLIT
        String priority, // LOW, MEDIUM, HIGH, URGENT
        String description,
        int estimatedPointsAffected,
        double estimatedStorageSavings,
        double qualityImpact,
        List<String> requirements,
        boolean automaticallyApplicable
    ) {}

    /**
     * Boundary protection configuration.
     */
    record BoundarySettings(
        int maxPoints,
        int archiveThreshold,
        int compressionThreshold,
        int emergencyThreshold,
        int retentionHours,
        double compressionTolerance,
        boolean enableAutoCompression,
        boolean enableAutoArchival,
        boolean enableEmergencyCleanup,
        String compressionAlgorithm,
        List<String> preserveFeatures
    ) {
        // Default settings factory methods
        
        public static BoundarySettings singleTripDefaults() {
            return new BoundarySettings(
                500,    // maxPoints
                400,    // archiveThreshold
                350,    // compressionThreshold
                600,    // emergencyThreshold
                24,     // retentionHours
                0.001,  // compressionTolerance (very high precision)
                true,   // enableAutoCompression
                true,   // enableAutoArchival
                false,  // enableEmergencyCleanup (manual approval required)
                "DOUGLAS_PEUCKER",
                List.of("TURNS", "STOPS", "SPEED_CHANGES")
            );
        }
        
        public static BoundarySettings multiStopDefaults() {
            return new BoundarySettings(
                1000,   // maxPoints (larger for longer trips)
                800,    // archiveThreshold
                700,    // compressionThreshold
                1200,   // emergencyThreshold
                48,     // retentionHours (longer retention)
                0.0005, // compressionTolerance (higher precision)
                true,   // enableAutoCompression
                true,   // enableAutoArchival
                false,  // enableEmergencyCleanup
                "DOUGLAS_PEUCKER",
                List.of("TURNS", "STOPS", "LOADING_POINTS", "SPEED_CHANGES")
            );
        }
        
        public static BoundarySettings conservativeDefaults() {
            return new BoundarySettings(
                200,    // maxPoints (very conservative)
                150,    // archiveThreshold
                120,    // compressionThreshold
                250,    // emergencyThreshold
                12,     // retentionHours
                0.002,  // compressionTolerance (moderate precision)
                true,   // enableAutoCompression
                true,   // enableAutoArchival
                true,   // enableEmergencyCleanup
                "TIME_BASED",
                List.of("TURNS", "STOPS")
            );
        }
        
        public static BoundarySettings highPrecisionDefaults() {
            return new BoundarySettings(
                2000,   // maxPoints (high capacity)
                1800,   // archiveThreshold
                1600,   // compressionThreshold
                2500,   // emergencyThreshold
                72,     // retentionHours (long retention)
                0.0001, // compressionTolerance (maximum precision)
                false,  // enableAutoCompression (manual only)
                false,  // enableAutoArchival (manual only)
                false,  // enableEmergencyCleanup (manual only)
                "DISTANCE_BASED",
                List.of("ALL") // Preserve everything
            );
        }
    }
}