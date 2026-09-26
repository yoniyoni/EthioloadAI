package com.ethioloadai.tracking.service;

import com.ethioloadai.tracking.dto.LocationUpdateRequest;
import com.ethioloadai.tracking.entity.CurrentVehicleLocation;
import com.ethioloadai.tracking.entity.VehicleLocationHistory;
import com.ethioloadai.trip.entity.Trip;
import com.ethioloadai.user.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Service for managing vehicle location persistence across current and historical storage tiers.
 * 
 * Implements two-tier architecture:
 * - Current locations: Real-time access to latest positions
 * - Historical locations: Complete GPS history for analytics
 */
public interface LocationPersistenceService {

    /**
     * Process complete GPS location update.
     * Handles both current location updates and historical archival.
     * 
     * @param locationRequest GPS location update request
     * @param trip current trip (null if not on trip)
     * @param user requesting user for authorization
     * @return processing result with performance metrics
     */
    LocationUpdateResult processLocationUpdate(LocationUpdateRequest locationRequest, Trip trip, User user);

    /**
     * Get current location for a vehicle.
     * Optimized for real-time tracking applications.
     * 
     * @param vehicleId vehicle identifier
     * @return current location if available
     */
    Optional<CurrentVehicleLocation> getCurrentLocation(Long vehicleId);

    /**
     * Get current locations for multiple vehicles.
     * Bulk operation for fleet management dashboards.
     * 
     * @param vehicleIds list of vehicle identifiers
     * @return current locations (missing vehicles will be absent from result)
     */
    List<CurrentVehicleLocation> getCurrentLocations(List<Long> vehicleIds);

    /**
     * Get vehicles within proximity of a location.
     * Uses geospatial indexing for efficient proximity searches.
     * 
     * @param latitude center point latitude
     * @param longitude center point longitude
     * @param radiusKm search radius in kilometers
     * @return nearby vehicles ordered by distance
     */
    List<CurrentVehicleLocation> getNearbyVehicles(BigDecimal latitude, BigDecimal longitude, BigDecimal radiusKm);

    /**
     * Get location history for a vehicle.
     * Supports filtering and pagination for large datasets.
     * 
     * @param vehicleId vehicle identifier
     * @param startTime history range start (null for all history)
     * @param endTime history range end (null for all history)
     * @param pageable pagination parameters
     * @return location history ordered by timestamp
     */
    Page<VehicleLocationHistory> getLocationHistory(Long vehicleId, LocalDateTime startTime, 
                                                   LocalDateTime endTime, Pageable pageable);

    /**
     * Get trip route from location history.
     * Optimized for route visualization and analysis.
     * 
     * @param tripId trip identifier
     * @param accuracyFilter maximum GPS accuracy threshold (null to include all)
     * @param pageable pagination parameters
     * @return trip route points ordered chronologically
     */
    Page<VehicleLocationHistory> getTripRoute(Long tripId, BigDecimal accuracyFilter, Pageable pageable);

    /**
     * Calculate route analytics from location history.
     * Provides comprehensive trip statistics and insights.
     * 
     * @param vehicleId vehicle identifier
     * @param startTime analysis period start
     * @param endTime analysis period end
     * @return route analytics with distance, speed, stops, etc.
     */
    RouteAnalytics calculateRouteAnalytics(Long vehicleId, LocalDateTime startTime, LocalDateTime endTime);

    /**
     * Detect location anomalies in GPS data.
     * Identifies potential data quality issues or unusual patterns.
     * 
     * @param vehicleId vehicle identifier
     * @param startTime analysis period start
     * @param endTime analysis period end
     * @return detected anomalies with severity levels
     */
    List<LocationAnomaly> detectLocationAnomalies(Long vehicleId, LocalDateTime startTime, LocalDateTime endTime);

    /**
     * Archive old location data.
     * Implements retention policy to manage storage growth.
     * 
     * @param beforeDate archive data older than this date
     * @return archival summary with counts and metrics
     */
    ArchivalResult archiveOldLocations(LocalDateTime beforeDate);

    /**
     * Clean up stale current locations.
     * Removes current location records for inactive vehicles.
     * 
     * @param staleThreshold locations older than this are considered stale
     * @return cleanup summary
     */
    CleanupResult cleanupStaleCurrentLocations(LocalDateTime staleThreshold);

    /**
     * Get location persistence statistics.
     * Provides operational metrics and health indicators.
     * 
     * @return comprehensive statistics
     */
    PersistenceStatistics getStatistics();

    /**
     * Batch process location updates.
     * High-performance bulk operation for GPS data ingestion.
     * 
     * @param locationUpdates batch of location updates
     * @return batch processing result
     */
    BatchLocationResult processBatchLocationUpdates(List<LocationUpdateRequest> locationUpdates);

    // ── Value Objects ────────────────────────────────────────────────────────

    /**
     * Result of processing a single location update.
     */
    record LocationUpdateResult(
        boolean success,
        String message,
        CurrentVehicleLocation currentLocation,
        VehicleLocationHistory historyLocation,
        long processingTimeMs,
        boolean currentLocationUpdated,
        boolean historyLocationCreated
    ) {
        public static LocationUpdateResult success(CurrentVehicleLocation current, 
                                                 VehicleLocationHistory history, 
                                                 long processingTimeMs) {
            return new LocationUpdateResult(true, "Location updated successfully", 
                                          current, history, processingTimeMs, true, true);
        }
        
        public static LocationUpdateResult failure(String message, long processingTimeMs) {
            return new LocationUpdateResult(false, message, null, null, processingTimeMs, false, false);
        }
    }

    /**
     * Route analytics with comprehensive metrics.
     */
    record RouteAnalytics(
        Long vehicleId,
        LocalDateTime startTime,
        LocalDateTime endTime,
        BigDecimal totalDistanceKm,
        BigDecimal drivingDistanceKm,
        BigDecimal averageSpeedKmh,
        BigDecimal maxSpeedKmh,
        long drivingTimeMinutes,
        long stoppedTimeMinutes,
        int totalGpsPoints,
        int qualityGpsPoints,
        List<RouteSegment> segments,
        List<StopEvent> stops
    ) {}

    /**
     * Route segment analysis.
     */
    record RouteSegment(
        LocalDateTime startTime,
        LocalDateTime endTime,
        BigDecimal startLatitude,
        BigDecimal startLongitude,
        BigDecimal endLatitude,
        BigDecimal endLongitude,
        BigDecimal distanceKm,
        BigDecimal averageSpeedKmh,
        String segmentType // DRIVING, STOPPED, LOADING, UNLOADING
    ) {}

    /**
     * Vehicle stop detection.
     */
    record StopEvent(
        LocalDateTime startTime,
        LocalDateTime endTime,
        BigDecimal latitude,
        BigDecimal longitude,
        long durationMinutes,
        String stopType, // SCHEDULED, UNSCHEDULED, LOADING, BREAK
        BigDecimal confidence // 0.0 to 1.0
    ) {}

    /**
     * Location data anomaly detection.
     */
    record LocationAnomaly(
        Long vehicleId,
        LocalDateTime timestamp,
        BigDecimal latitude,
        BigDecimal longitude,
        String anomalyType, // SPEED_JUMP, POSITION_JUMP, ACCURACY_DROP, TIME_GAP
        BigDecimal severity, // 0.0 to 1.0
        String description,
        BigDecimal detectedValue,
        BigDecimal expectedValue
    ) {}

    /**
     * Data archival operation result.
     */
    record ArchivalResult(
        long totalRecordsArchived,
        long storageFreedBytes,
        LocalDateTime oldestArchivedDate,
        LocalDateTime newestArchivedDate,
        long processingTimeMs,
        String status
    ) {}

    /**
     * Cleanup operation result.
     */
    record CleanupResult(
        long recordsDeleted,
        long vehiclesAffected,
        LocalDateTime oldestDeletedTimestamp,
        long processingTimeMs,
        String status
    ) {}

    /**
     * Location persistence system statistics.
     */
    record PersistenceStatistics(
        long currentLocationCount,
        long historyLocationCount,
        long activeVehicleCount,
        long totalVehicleCount,
        BigDecimal averageAccuracyMeters,
        BigDecimal storageUsedGB,
        LocalDateTime oldestHistoryDate,
        LocalDateTime newestHistoryDate,
        double dataQualityScore, // 0.0 to 1.0
        long locationsPerHour
    ) {}

    /**
     * Batch processing result.
     */
    record BatchLocationResult(
        int totalRequests,
        int successfulUpdates,
        int failedUpdates,
        long processingTimeMs,
        List<String> errors
    ) {}
}