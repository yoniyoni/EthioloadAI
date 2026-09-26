package com.ethioloadai.trip.service;

import com.ethioloadai.tracking.service.LocationHistoryService;
import com.ethioloadai.trip.entity.Trip;
import com.ethioloadai.trip.repository.TripRepository;
import com.ethioloadai.websocket.broadcasting.LocationBroadcastService;
import com.ethioloadai.websocket.service.WebSocketSessionManager;
import com.ethioloadai.websocket.dto.WebSocketMessage;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Phase 16 implementation of trip progress calculation.
 */
@Service
@RequiredArgsConstructor
public class TripProgressServiceImpl implements TripProgressService {
    private static final Logger log = LoggerFactory.getLogger(TripProgressServiceImpl.class);

    private static final BigDecimal HUNDRED   = BigDecimal.valueOf(100);
    private static final BigDecimal NEAR_DEST = BigDecimal.valueOf(90);

    private final TripRepository           tripRepository;
    private final LocationHistoryService   locationHistoryService;
    private final WebSocketSessionManager  sessionManager;

    @Override
    @Transactional(readOnly = true)
    public ProgressSnapshot calculateProgress(Long tripId) {
        log.debug("Calculating progress for trip {}", tripId);

        Trip trip = tripRepository.findById(tripId)
            .orElseThrow(() -> new IllegalArgumentException("Trip not found: " + tripId));

        // Distance covered from GPS history
        BigDecimal covered = locationHistoryService.calculateTripDistance(tripId);

        // Current vehicle position
        LocationHistoryService.LocationPoint last =
            locationHistoryService.getLastKnownLocation(
                trip.getBooking().getVehicle().getId());

        // Total route points and stop counts
        List<LocationHistoryService.LocationPoint> route =
            locationHistoryService.getTripRoute(tripId);

        int totalStops = trip.getStops() != null ? trip.getStops().size() : 0;
        int stopsCompleted = countCompletedStops(trip);

        // Estimate total distance from full route (not just covered so far)
        BigDecimal totalEstimate = estimateTotalDistance(trip, covered, stopsCompleted, totalStops);

        // Percent complete
        BigDecimal percent = BigDecimal.ZERO;
        BigDecimal remaining = totalEstimate;
        if (totalEstimate.compareTo(BigDecimal.ZERO) > 0) {
            percent = covered.multiply(HUNDRED)
                             .divide(totalEstimate, 1, RoundingMode.HALF_UP)
                             .min(HUNDRED);
            remaining = totalEstimate.subtract(covered).max(BigDecimal.ZERO);
        } else if (stopsCompleted >= totalStops && totalStops > 0) {
            percent  = HUNDRED;
            remaining = BigDecimal.ZERO;
        }

        String status = deriveStatus(percent, trip);

        return new ProgressSnapshot(
            tripId,
            percent,
            covered,
            totalEstimate.compareTo(BigDecimal.ZERO) == 0 ? null : totalEstimate,
            remaining,
            stopsCompleted,
            totalStops,
            null,                    // ETA calculation is out of scope for this phase
            last != null ? last.latitude()  : null,
            last != null ? last.longitude() : null,
            last != null ? last.speedKmh()  : null,
            status,
            LocalDateTime.now()
        );
    }

    @Override
    public ProgressSnapshot calculateAndBroadcast(Long tripId) {
        ProgressSnapshot snap = calculateProgress(tripId);

        WebSocketMessage message = WebSocketMessage.system("TRIP_PROGRESS", Map.of(
            "tripId",              snap.tripId(),
            "percentComplete",     snap.percentComplete(),
            "distanceCoveredKm",   snap.distanceCoveredKm(),
            "remainingDistanceKm", snap.remainingDistanceKm() != null ? snap.remainingDistanceKm() : "",
            "stopsCompleted",      snap.stopsCompleted(),
            "totalStops",          snap.totalStops(),
            "status",              snap.status(),
            "currentLatitude",     snap.currentLatitude()  != null ? snap.currentLatitude()  : "",
            "currentLongitude",    snap.currentLongitude() != null ? snap.currentLongitude() : "",
            "calculatedAt",        snap.calculatedAt()
        ));

        sessionManager.broadcastToTopic("trip." + tripId + ".location", message);

        log.debug("Trip {} progress broadcast: {}%", tripId, snap.percentComplete());
        return snap;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private int countCompletedStops(Trip trip) {
        if (trip.getStops() == null) return 0;
        return (int) trip.getStops().stream()
            .filter(s -> s.getArrivalTime() != null)
            .count();
    }

    /**
     * Very rough total-distance estimator.
     * If we have stops, we linearly extrapolate from covered vs stops ratio.
     * Otherwise, we return covered as the lower bound.
     */
    private BigDecimal estimateTotalDistance(Trip trip, BigDecimal covered,
                                             int stopsCompleted, int totalStops) {
        if (totalStops > 0 && stopsCompleted > 0 && covered.compareTo(BigDecimal.ZERO) > 0) {
            // proportion: covered / stopsCompleted * totalStops
            return covered
                .multiply(BigDecimal.valueOf(totalStops))
                .divide(BigDecimal.valueOf(stopsCompleted), 3, RoundingMode.HALF_UP);
        }
        return covered; // can only grow
    }

    private String deriveStatus(BigDecimal percent, Trip trip) {
        if (trip.getEndTime() != null)              return "COMPLETED";
        if (percent.compareTo(NEAR_DEST) >= 0)      return "NEAR_DESTINATION";
        if (trip.getStartTime() != null)             return "IN_PROGRESS";
        return "PENDING";
    }
}
