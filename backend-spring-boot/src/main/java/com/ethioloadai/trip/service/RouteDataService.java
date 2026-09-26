package com.ethioloadai.trip.service;

import com.ethioloadai.trip.entity.Trip;
import com.ethioloadai.user.entity.User;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Service for managing GPS route data and tracking with safe boundaries.
 * Prevents unbounded growth of JSONB route_data field while maintaining tracking functionality.
 */
public interface RouteDataService {

    /**
     * Add GPS location update to trip route data.
     * Enforces growth boundaries and data quality validation.
     *
     * @param trip trip to update
     * @param gpsData GPS location data
     * @param user user performing update (must be driver)
     * @return updated trip with new GPS data
     */
    Trip addGpsLocation(Trip trip, GpsLocationData gpsData, User user);

    /**
     * Get current trip location (most recent GPS point).
     *
     * @param trip trip to get location for
     * @return current location or null if no GPS data
     */
    GpsLocationData getCurrentLocation(Trip trip);

    /**
     * Get route history with optional filtering and pagination.
     *
     * @param trip trip to get route for
     * @param limit maximum number of points to return
     * @param fromTime optional start time filter
     * @param toTime optional end time filter
     * @return filtered route history
     */
    List<GpsLocationData> getRouteHistory(Trip trip, Integer limit, 
                                         LocalDateTime fromTime, LocalDateTime toTime);

    /**
     * Calculate trip distance from GPS breadcrumbs.
     *
     * @param trip trip to calculate distance for
     * @return total distance in kilometers
     */
    BigDecimal calculateTripDistance(Trip trip);

    /**
     * Calculate average speed for trip.
     *
     * @param trip trip to calculate speed for
     * @return average speed in km/h
     */
    BigDecimal calculateAverageSpeed(Trip trip);

    /**
     * Get route statistics and analytics.
     *
     * @param trip trip to analyze
     * @return comprehensive route analytics
     */
    RouteAnalytics getRouteAnalytics(Trip trip);

    /**
     * Archive old GPS data to prevent unbounded growth.
     * Removes old breadcrumbs while keeping essential waypoints.
     *
     * @param trip trip to archive data for
     * @return number of points archived
     */
    int archiveOldGpsData(Trip trip);

    /**
     * Compress route data by removing redundant points.
     * Uses Douglas-Peucker algorithm to maintain route shape with fewer points.
     *
     * @param trip trip to compress route for
     * @param tolerance compression tolerance (larger = more compression)
     * @return number of points removed
     */
    int compressRouteData(Trip trip, BigDecimal tolerance);

    /**
     * Validate GPS location data quality.
     *
     * @param gpsData GPS data to validate
     * @return validation result with issues
     */
    GpsValidationResult validateGpsData(GpsLocationData gpsData);

    /**
     * Get route data storage statistics.
     *
     * @param trip trip to get stats for
     * @return storage usage and limits
     */
    RouteStorageStats getRouteStorageStats(Trip trip);

    /**
     * Export route data in standard format.
     *
     * @param trip trip to export
     * @param format export format (GPX, KML, JSON)
     * @return exported route data
     */
    String exportRouteData(Trip trip, String format);

    /**
     * Detect route anomalies (sudden jumps, unrealistic speeds).
     *
     * @param trip trip to analyze
     * @return detected anomalies
     */
    List<RouteAnomaly> detectRouteAnomalies(Trip trip);

    // ── Value Objects ────────────────────────────────────────────────────────

    record GpsLocationData(
        BigDecimal latitude,
        BigDecimal longitude,
        BigDecimal altitude,
        BigDecimal accuracy,
        BigDecimal speed,
        BigDecimal heading,
        LocalDateTime timestamp,
        Map<String, Object> metadata
    ) {}

    record RouteAnalytics(
        BigDecimal totalDistanceKm,
        BigDecimal averageSpeedKmh,
        BigDecimal maxSpeedKmh,
        BigDecimal drivingTimeHours,
        BigDecimal stoppedTimeHours,
        int totalGpsPoints,
        LocalDateTime firstPoint,
        LocalDateTime lastPoint,
        List<RouteSegment> segments,
        Map<String, Object> statistics
    ) {}

    record RouteSegment(
        LocalDateTime startTime,
        LocalDateTime endTime,
        BigDecimal distanceKm,
        BigDecimal averageSpeedKmh,
        String segmentType, // driving, stopped, loading, etc.
        GpsLocationData startLocation,
        GpsLocationData endLocation
    ) {}

    record GpsValidationResult(
        boolean isValid,
        List<String> issues,
        List<String> warnings,
        BigDecimal qualityScore
    ) {}

    record RouteStorageStats(
        int currentPoints,
        int maxAllowedPoints,
        BigDecimal storageUsedKb,
        BigDecimal storageLimit,
        BigDecimal compressionRatio,
        LocalDateTime oldestPoint,
        LocalDateTime newestPoint
    ) {}

    record RouteAnomaly(
        String type,
        String description,
        LocalDateTime timestamp,
        GpsLocationData location,
        BigDecimal severity,
        Map<String, Object> details
    ) {}
}