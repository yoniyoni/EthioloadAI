package com.ethioloadai.tracking.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Phase 15: Location history management.
 *
 * Provides retrieval and analytics over the VehicleLocationHistory table
 * created in Phase 7. Keeps business logic out of the repository layer.
 */
public interface LocationHistoryService {

    /**
     * Return the full route for a trip, ordered chronologically.
     * Capped at 500 points per Phase 8 boundary rules.
     */
    List<LocationPoint> getTripRoute(Long tripId);

    /**
     * Return location history for a vehicle within a time window.
     */
    List<LocationPoint> getVehicleHistory(Long vehicleId,
                                          LocalDateTime from,
                                          LocalDateTime to);

    /**
     * Calculate total distance driven on a trip (kilometres).
     */
    BigDecimal calculateTripDistance(Long tripId);

    /**
     * Return the last known location for a vehicle.
     */
    LocationPoint getLastKnownLocation(Long vehicleId);

    /**
     * Delete history older than {@code before} for a vehicle.
     *
     * @return number of records deleted
     */
    int purgeHistory(Long vehicleId, LocalDateTime before);

    // ── Value objects ─────────────────────────────────────────────────────────

    record LocationPoint(
        Long          vehicleId,
        Long          tripId,
        BigDecimal    latitude,
        BigDecimal    longitude,
        BigDecimal    altitude,
        BigDecimal    speedKmh,
        BigDecimal    headingDegrees,
        BigDecimal    accuracyMetres,
        LocalDateTime timestamp,
        String        source
    ) {}

    record TripRouteStats(
        Long       tripId,
        int        pointCount,
        BigDecimal totalDistanceKm,
        BigDecimal avgSpeedKmh,
        BigDecimal maxSpeedKmh,
        LocalDateTime startTime,
        LocalDateTime endTime,
        long       durationMinutes
    ) {}
}
