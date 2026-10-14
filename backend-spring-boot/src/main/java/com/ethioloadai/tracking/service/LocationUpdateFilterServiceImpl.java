package com.ethioloadai.tracking.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ethioloadai.tracking.dto.LocationUpdateRequest;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Phase 12 implementation: stateful, per-trip location filtering.
 *
 * Thread-safe – multiple GPS fixes for the same trip may arrive concurrently.
 */
@Service
public class LocationUpdateFilterServiceImpl implements LocationUpdateFilterService {
    private static final Logger log = LoggerFactory.getLogger(LocationUpdateFilterServiceImpl.class);

    // ── Ethiopia bounding box (WGS-84) ────────────────────────────────────────
    private static final BigDecimal LAT_MIN = new BigDecimal("3.4");
    private static final BigDecimal LAT_MAX = new BigDecimal("15.0");
    private static final BigDecimal LON_MIN = new BigDecimal("33.0");
    private static final BigDecimal LON_MAX = new BigDecimal("48.0");

    // ── Thresholds ────────────────────────────────────────────────────────────
    /** Max allowed implied speed between successive fixes (km/h). */
    private static final double MAX_IMPLIED_SPEED_KMH = 200.0;
    /** Minimum GPS accuracy accepted without a warning (metres). */
    private static final double ACCURACY_WARNING_THRESHOLD_M = 100.0;
    /** Minimum GPS accuracy that triggers an outright discard (metres). */
    private static final double ACCURACY_DISCARD_THRESHOLD_M = 500.0;
    /** Minimum time gap between accepted fixes for the same trip (seconds). */
    private static final long MIN_FIX_GAP_SECONDS = 2;

    // Per-trip last-state cache
    private final ConcurrentHashMap<Long, TripLocationState> tripStates = new ConcurrentHashMap<>();

    // ── Earth radius for Haversine ─────────────────────────────────────────────
    private static final double EARTH_RADIUS_KM = 6371.0;

    @Override
    public FilterResult filter(Long tripId, LocationUpdateRequest req) {

        // 1. Bounding-box check
        FilterResult bbox = checkBoundingBox(req.getLatitude(), req.getLongitude());
        if (!bbox.isAccepted()) return bbox;

        // 2. Accuracy check
        FilterResult acc = checkAccuracy(req.getAccuracy());
        if (!acc.isAccepted()) return acc;

        // 3. Stateful checks against the previous fix
        TripLocationState prev = tripStates.get(tripId);
        if (prev != null) {
            // 3a. Out-of-order: incoming fix is older than the last accepted fix
            if (req.getTimestamp() != null && prev.lastTimestamp() != null
                    && req.getTimestamp().isBefore(prev.lastTimestamp())) {
                return FilterResult.discard(
                    "GPS fix is older than last accepted fix (out-of-order delivery)",
                    "OUT_OF_ORDER");
            }

            // 3b. Duplicate / stale fix
            FilterResult dup = checkDuplicate(prev, req);
            if (!dup.isAccepted()) return dup;

            // 3c. Teleportation / speed anomaly
            FilterResult teleport = checkTeleport(prev, req);
            if (!teleport.isAccepted()) return teleport;
        }

        // All checks passed — update state and accept
        updateState(tripId, prev, req);

        if (acc.action() == FilterAction.WARN_AND_ACCEPT) return acc; // propagate accuracy warning
        return FilterResult.accept();
    }

    @Override
    public void resetTripState(Long tripId) {
        tripStates.remove(tripId);
        log.debug("Reset location filter state for trip {}", tripId);
    }

    // ── Private checks ────────────────────────────────────────────────────────

    private FilterResult checkBoundingBox(BigDecimal lat, BigDecimal lon) {
        if (lat.compareTo(LAT_MIN) < 0 || lat.compareTo(LAT_MAX) > 0
                || lon.compareTo(LON_MIN) < 0 || lon.compareTo(LON_MAX) > 0) {
            log.warn("GPS fix outside Ethiopia bounding box: lat={}, lon={}", lat, lon);
            return FilterResult.discard(
                "GPS coordinates outside Ethiopia bounding box", "OUT_OF_BOUNDS");
        }
        return FilterResult.accept();
    }

    private FilterResult checkAccuracy(BigDecimal accuracy) {
        if (accuracy == null) return FilterResult.accept(); // no accuracy info → accept

        double acc = accuracy.doubleValue();
        if (acc > ACCURACY_DISCARD_THRESHOLD_M) {
            return FilterResult.discard(
                "GPS accuracy too poor: " + acc + "m > " + ACCURACY_DISCARD_THRESHOLD_M + "m",
                "ACCURACY_TOO_POOR");
        }
        if (acc > ACCURACY_WARNING_THRESHOLD_M) {
            return FilterResult.warnAndAccept(
                "GPS accuracy degraded: " + acc + "m", "ACCURACY_DEGRADED");
        }
        return FilterResult.accept();
    }

    private FilterResult checkDuplicate(TripLocationState prev, LocationUpdateRequest req) {
        // Same timestamp → definitely duplicate
        if (req.getTimestamp() != null
                && req.getTimestamp().equals(prev.lastTimestamp())) {
            return FilterResult.discard("Duplicate GPS fix (same timestamp)", "DUPLICATE_TIMESTAMP");
        }

        // Too soon after previous fix
        if (req.getTimestamp() != null && prev.lastTimestamp() != null) {
            long gapSec = Duration.between(prev.lastTimestamp(), req.getTimestamp()).getSeconds();
            if (gapSec < MIN_FIX_GAP_SECONDS) {
                return FilterResult.discard(
                    "GPS fix arrived too soon (" + gapSec + "s < " + MIN_FIX_GAP_SECONDS + "s)",
                    "TOO_FREQUENT");
            }
        }

        // Identical coordinates with close timestamp → discard as stationary duplicate
        if (req.getLatitude().compareTo(prev.lastLatitude()) == 0
                && req.getLongitude().compareTo(prev.lastLongitude()) == 0) {
            return FilterResult.discard("Duplicate GPS fix (same coordinates)", "DUPLICATE_COORDS");
        }

        return FilterResult.accept();
    }

    private FilterResult checkTeleport(TripLocationState prev, LocationUpdateRequest req) {
        if (prev.lastTimestamp() == null || req.getTimestamp() == null) return FilterResult.accept();

        double distKm = haversineKm(
            prev.lastLatitude().doubleValue(),  prev.lastLongitude().doubleValue(),
            req.getLatitude().doubleValue(),    req.getLongitude().doubleValue()
        );

        long gapSec = Math.max(1,
            Duration.between(prev.lastTimestamp(), req.getTimestamp()).abs().getSeconds());
        double impliedSpeedKmh = distKm / (gapSec / 3600.0);

        if (impliedSpeedKmh > MAX_IMPLIED_SPEED_KMH) {
            log.warn("Teleportation detected for trip state: dist={}km in {}s ({}km/h)",
                    String.format("%.2f", distKm), gapSec,
                    String.format("%.0f", impliedSpeedKmh));
            return FilterResult.discard(
                String.format("Implied speed %.0f km/h exceeds limit %.0f km/h",
                              impliedSpeedKmh, MAX_IMPLIED_SPEED_KMH),
                "TELEPORTATION");
        }

        return FilterResult.accept();
    }

    private void updateState(Long tripId, TripLocationState prev, LocationUpdateRequest req) {
        int count = prev == null ? 1 : prev.updateCount() + 1;
        tripStates.put(tripId, new TripLocationState(
            tripId,
            req.getLatitude(),
            req.getLongitude(),
            req.getTimestamp(),
            req.getSpeed(),
            count
        ));
    }

    // ── Haversine distance ─────────────────────────────────────────────────────

    private double haversineKm(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                 + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                 * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return EARTH_RADIUS_KM * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}
