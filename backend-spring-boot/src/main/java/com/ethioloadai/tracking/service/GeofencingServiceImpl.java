package com.ethioloadai.tracking.service;

import com.ethioloadai.websocket.broadcasting.LocationBroadcastService;
import com.ethioloadai.websocket.broadcasting.LocationBroadcastServiceImpl;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Phase 17 implementation of geofencing.
 *
 * Maintains an in-memory map of active geofences keyed by tripId,
 * tracks which stops have already been entered (to suppress duplicate
 * ENTERED events), and fires stop-event broadcasts on state changes.
 */
@Service
@RequiredArgsConstructor
public class GeofencingServiceImpl implements GeofencingService {
    private static final Logger log = LoggerFactory.getLogger(GeofencingServiceImpl.class);

    private static final double EARTH_RADIUS_M = 6_371_000.0;
    private static final double DEFAULT_STOP_RADIUS_M = 200.0;

    private final LocationBroadcastServiceImpl broadcastService;

    // tripId → list of geofences for that trip
    private final ConcurrentHashMap<Long, List<StopGeofence>> tripGeofences =
        new ConcurrentHashMap<>();

    // tripId → set of stopIds currently "inside" (entered but not yet exited)
    private final ConcurrentHashMap<Long, Set<Long>> insideStops =
        new ConcurrentHashMap<>();

    @Override
    public List<GeofenceEvent> checkGeofences(Long tripId, Long vehicleId,
                                               BigDecimal latitude, BigDecimal longitude,
                                               LocalDateTime timestamp) {

        List<StopGeofence> fences = tripGeofences.get(tripId);
        if (fences == null || fences.isEmpty()) return List.of();

        Set<Long> inside = insideStops.computeIfAbsent(tripId, k -> ConcurrentHashMap.newKeySet());
        List<GeofenceEvent> events = new ArrayList<>();

        for (StopGeofence fence : fences) {
            double distM = haversineMetres(
                latitude.doubleValue(), longitude.doubleValue(),
                fence.centerLat().doubleValue(), fence.centerLon().doubleValue()
            );

            boolean withinRadius = distM <= fence.radiusMetres();
            boolean wasInside    = inside.contains(fence.stopId());

            if (withinRadius && !wasInside) {
                // ENTER event
                inside.add(fence.stopId());
                GeofenceEvent ev = new GeofenceEvent(
                    tripId, fence.stopId(), fence.stopName(), vehicleId,
                    GeofenceEventType.ENTERED, latitude, longitude, distM, timestamp);
                events.add(ev);
                log.info("Geofence ENTERED: trip={} stop={} ({}) dist={}m",
                         tripId, fence.stopId(), fence.stopName(), String.format("%.1f", distM));

                broadcastService.broadcastStopEvent(new LocationBroadcastService.StopEvent(
                    tripId, fence.stopId(), vehicleId, null,
                    fence.stopName(), "ARRIVED",
                    timestamp, latitude, longitude
                ));

            } else if (!withinRadius && wasInside) {
                // EXIT event
                inside.remove(fence.stopId());
                GeofenceEvent ev = new GeofenceEvent(
                    tripId, fence.stopId(), fence.stopName(), vehicleId,
                    GeofenceEventType.EXITED, latitude, longitude, distM, timestamp);
                events.add(ev);
                log.info("Geofence EXITED: trip={} stop={} ({}) dist={}m",
                         tripId, fence.stopId(), fence.stopName(), String.format("%.1f", distM));

                broadcastService.broadcastStopEvent(new LocationBroadcastService.StopEvent(
                    tripId, fence.stopId(), vehicleId, null,
                    fence.stopName(), "DEPARTED",
                    timestamp, latitude, longitude
                ));
            }
        }

        return events;
    }

    @Override
    public void registerStopGeofence(Long tripId, Long stopId, String stopName,
                                      BigDecimal latitude, BigDecimal longitude,
                                      double radiusMetres) {

        StopGeofence fence = new StopGeofence(tripId, stopId, stopName,
                                               latitude, longitude,
                                               radiusMetres > 0 ? radiusMetres : DEFAULT_STOP_RADIUS_M);

        tripGeofences.computeIfAbsent(tripId, k -> Collections.synchronizedList(new ArrayList<>()))
                     .removeIf(f -> f.stopId().equals(stopId)); // replace if already registered
        tripGeofences.get(tripId).add(fence);

        log.debug("Registered geofence: trip={}, stop={} ({}), radius={}m",
                 tripId, stopId, stopName, fence.radiusMetres());
    }

    @Override
    public void removeAllGeofences(Long tripId) {
        tripGeofences.remove(tripId);
        insideStops.remove(tripId);
        log.debug("Removed all geofences for trip {}", tripId);
    }

    // ── Haversine (metres) ────────────────────────────────────────────────────

    private double haversineMetres(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                 + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                 * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return EARTH_RADIUS_M * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}
