package com.ethioloadai.websocket.broadcasting;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * REST controller that exposes broadcasting endpoints for internal
 * services and admin tooling.
 *
 * External clients (driver apps, fleet dashboards) receive data via
 * WebSocket — these endpoints exist for service-to-service calls and
 * operational testing only.
 */
@RestController
@RequestMapping("/websocket/broadcast")
@RequiredArgsConstructor
public class BroadcastController {
    private static final Logger log = LoggerFactory.getLogger(BroadcastController.class);

    private final LocationBroadcastServiceImpl broadcastService;

    /** Manual location broadcast (admin / integration testing). */
    @PostMapping("/location")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> broadcastLocation(
            @RequestBody LocationBroadcastService.LocationUpdate update) {

        log.info("Manual location broadcast for trip={}", update.tripId());
        broadcastService.broadcastLocationUpdate(update);

        return ResponseEntity.ok(Map.of(
            "status",    "broadcast_sent",
            "tripId",    update.tripId(),
            "vehicleId", update.vehicleId(),
            "timestamp", LocalDateTime.now()
        ));
    }

    /** Trigger a stop-event broadcast (admin / integration testing). */
    @PostMapping("/stop-event")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> broadcastStopEvent(
            @RequestBody LocationBroadcastService.StopEvent event) {

        log.info("Manual stop-event broadcast: trip={}, type={}", event.tripId(), event.eventType());
        broadcastService.broadcastStopEvent(event);

        return ResponseEntity.ok(Map.of(
            "status",    "broadcast_sent",
            "tripId",    event.tripId(),
            "eventType", event.eventType(),
            "timestamp", LocalDateTime.now()
        ));
    }

    /** Trigger a trip-status broadcast (called internally by TripService). */
    @PostMapping("/trip-status")
    @PreAuthorize("hasAnyRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> broadcastTripStatus(
            @RequestBody LocationBroadcastService.TripStatusEvent event) {

        log.info("Trip status broadcast: trip={} → {}", event.tripId(), event.newStatus());
        broadcastService.broadcastTripStatusChange(event);

        return ResponseEntity.ok(Map.of(
            "status",    "broadcast_sent",
            "tripId",    event.tripId(),
            "newStatus", event.newStatus(),
            "timestamp", LocalDateTime.now()
        ));
    }

    /** Trigger a vehicle-status broadcast. */
    @PostMapping("/vehicle-status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> broadcastVehicleStatus(
            @RequestBody LocationBroadcastService.VehicleStatusEvent event) {

        log.info("Vehicle status broadcast: vehicle={} → {}", event.vehicleId(), event.newStatus());
        broadcastService.broadcastVehicleStatusUpdate(event);

        return ResponseEntity.ok(Map.of(
            "status",    "broadcast_sent",
            "vehicleId", event.vehicleId(),
            "newStatus", event.newStatus(),
            "timestamp", LocalDateTime.now()
        ));
    }

    /** Broadcasting statistics. */
    @GetMapping("/stats")
    @PreAuthorize("hasAnyRole('ADMIN', 'FLEET_OWNER')")
    public ResponseEntity<LocationBroadcastService.BroadcastStats> getStats() {
        return ResponseEntity.ok(broadcastService.getStats());
    }
}
