package com.ethioloadai.tracking.service;

import com.ethioloadai.tracking.entity.VehicleLocationHistory;
import com.ethioloadai.tracking.repository.VehicleLocationHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Phase 15 implementation of location history management.
 */
@Service
@RequiredArgsConstructor
public class LocationHistoryServiceImpl implements LocationHistoryService {
    private static final Logger log = LoggerFactory.getLogger(LocationHistoryServiceImpl.class);

    private static final int    MAX_ROUTE_POINTS = 500;
    private static final double EARTH_RADIUS_KM  = 6371.0;

    private final VehicleLocationHistoryRepository historyRepository;

    @Override
    @Transactional(readOnly = true)
    public List<LocationPoint> getTripRoute(Long tripId) {
        log.debug("Fetching route for trip {}", tripId);

        List<VehicleLocationHistory> rows = historyRepository
            .findByTripIdOrderByTimestampAsc(tripId, MAX_ROUTE_POINTS);

        List<LocationPoint> points = rows.stream().map(this::toPoint).collect(Collectors.toList());
        log.debug("Trip {} route: {} points", tripId, points.size());
        return points;
    }

    @Override
    @Transactional(readOnly = true)
    public List<LocationPoint> getVehicleHistory(Long vehicleId,
                                                  LocalDateTime from,
                                                  LocalDateTime to) {
        log.debug("Fetching history for vehicle {} from {} to {}", vehicleId, from, to);

        List<VehicleLocationHistory> rows = historyRepository
            .findByVehicleIdAndTimestampBetweenOrderByTimestampAsc(vehicleId, from, to);

        return rows.stream().map(this::toPoint).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal calculateTripDistance(Long tripId) {
        List<LocationPoint> route = getTripRoute(tripId);
        if (route.size() < 2) return BigDecimal.ZERO;

        double totalKm = 0.0;
        for (int i = 1; i < route.size(); i++) {
            LocationPoint a = route.get(i - 1);
            LocationPoint b = route.get(i);
            totalKm += haversineKm(
                a.latitude().doubleValue(), a.longitude().doubleValue(),
                b.latitude().doubleValue(), b.longitude().doubleValue()
            );
        }

        return BigDecimal.valueOf(totalKm).setScale(3, RoundingMode.HALF_UP);
    }

    @Override
    @Transactional(readOnly = true)
    public LocationPoint getLastKnownLocation(Long vehicleId) {
        log.debug("Fetching last known location for vehicle {}", vehicleId);

        return historyRepository.findTopByVehicleIdOrderByTimestampDesc(
                vehicleId, PageRequest.of(0, 1))
            .stream()
            .findFirst()
            .map(this::toPoint)
            .orElse(null);
    }

    @Override
    @Transactional
    public int purgeHistory(Long vehicleId, LocalDateTime before) {
        log.info("Purging location history for vehicle {} before {}", vehicleId, before);

        int deleted = historyRepository.deleteByVehicleIdAndTimestampBefore(vehicleId, before);
        log.info("Purged {} history records for vehicle {}", deleted, vehicleId);
        return deleted;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private LocationPoint toPoint(VehicleLocationHistory h) {
        return new LocationPoint(
            h.getVehicleId(), h.getTripId(),
            h.getLatitude(), h.getLongitude(),
            h.getAltitude(), h.getSpeed(),
            h.getHeading(), h.getAccuracy(),
            h.getTimestamp(), null  // source not stored in history entity
        );
    }

    private double haversineKm(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                 + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                 * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return EARTH_RADIUS_KM * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}
