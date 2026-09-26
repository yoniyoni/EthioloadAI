package com.ethioloadai.tracking.service;

import com.ethioloadai.tracking.dto.LocationUpdateRequest;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Phase 12: Location update validation and filtering.
 *
 * Applies domain-specific filters on top of the raw GPS validation
 * from {@link GpsValidationService} to catch:
 * <ul>
 *   <li>Duplicate / stale fixes (same coords, same timestamp)</li>
 *   <li>Speed anomalies (teleportation between successive fixes)</li>
 *   <li>Out-of-Ethiopia bounding-box fixes</li>
 *   <li>Accuracy threshold enforcement</li>
 * </ul>
 */
public interface LocationUpdateFilterService {

    /**
     * Apply all enabled filters to an incoming location update.
     *
     * @param tripId  the trip this update belongs to
     * @param request the incoming GPS fix
     * @return filter result describing whether the fix should be persisted and broadcast
     */
    FilterResult filter(Long tripId, LocationUpdateRequest request);

    /**
     * Reset state held for a specific trip (call on trip completion).
     */
    void resetTripState(Long tripId);

    // ── Value objects ─────────────────────────────────────────────────────────

    enum FilterAction { ACCEPT, DISCARD, WARN_AND_ACCEPT }

    record FilterResult(
        FilterAction action,
        String       reason,     // human-readable explanation
        String       filterCode  // machine-readable code e.g. "DUPLICATE", "TELEPORT"
    ) {
        public boolean isAccepted() {
            return action == FilterAction.ACCEPT || action == FilterAction.WARN_AND_ACCEPT;
        }

        public static FilterResult accept() {
            return new FilterResult(FilterAction.ACCEPT, "OK", "OK");
        }

        public static FilterResult warnAndAccept(String reason, String code) {
            return new FilterResult(FilterAction.WARN_AND_ACCEPT, reason, code);
        }

        public static FilterResult discard(String reason, String code) {
            return new FilterResult(FilterAction.DISCARD, reason, code);
        }
    }

    /** Last known state for a trip – used for duplicate / teleport detection. */
    record TripLocationState(
        Long          tripId,
        BigDecimal    lastLatitude,
        BigDecimal    lastLongitude,
        LocalDateTime lastTimestamp,
        BigDecimal    lastSpeedKmh,
        int           updateCount
    ) {}
}
