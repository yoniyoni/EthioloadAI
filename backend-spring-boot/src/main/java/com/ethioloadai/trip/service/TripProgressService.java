package com.ethioloadai.trip.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Phase 16: Trip progress calculation.
 *
 * Computes what fraction of a trip has been completed using the GPS
 * route history and trip-stop waypoints, and broadcasts the result
 * to subscribed WebSocket clients.
 */
public interface TripProgressService {

    /**
     * Calculate the current progress snapshot for a trip.
     *
     * @param tripId the trip
     * @return progress snapshot, never null
     */
    ProgressSnapshot calculateProgress(Long tripId);

    /**
     * Calculate and immediately broadcast the progress to topic subscribers.
     *
     * @param tripId the trip
     * @return the calculated snapshot (also broadcast)
     */
    ProgressSnapshot calculateAndBroadcast(Long tripId);

    // ── Value objects ─────────────────────────────────────────────────────────

    /**
     * Percentage-based trip progress.
     *
     * <pre>
     *   percentComplete = distanceCoveredKm / totalRouteDistanceKm * 100
     * </pre>
     */
    record ProgressSnapshot(
        Long          tripId,
        BigDecimal    percentComplete,         // 0-100
        BigDecimal    distanceCoveredKm,
        BigDecimal    totalRouteDistanceKm,    // best estimate; may be null early on
        BigDecimal    remainingDistanceKm,
        int           stopsCompleted,
        int           totalStops,
        LocalDateTime estimatedArrival,        // nullable
        BigDecimal    currentLatitude,         // current vehicle position, nullable
        BigDecimal    currentLongitude,
        BigDecimal    currentSpeedKmh,         // nullable
        String        status,                  // PENDING, IN_PROGRESS, NEAR_DESTINATION, COMPLETED
        LocalDateTime calculatedAt
    ) {
        /** Convenience – is the trip effectively done? */
        public boolean isComplete() {
            return percentComplete != null
                && percentComplete.compareTo(BigDecimal.valueOf(99)) >= 0;
        }
    }
}
