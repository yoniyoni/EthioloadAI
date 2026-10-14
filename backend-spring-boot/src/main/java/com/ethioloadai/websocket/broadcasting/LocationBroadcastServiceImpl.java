package com.ethioloadai.websocket.broadcasting;

import com.ethioloadai.websocket.dto.WebSocketMessage;
import com.ethioloadai.websocket.security.WebSocketRateLimitService;
import com.ethioloadai.websocket.service.WebSocketSessionManager;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Real-time location broadcasting implementation.
 *
 * Receives validated GPS updates and pushes them to subscribers for the
 * relevant trip and vehicle topics.
 *
 * Fan-out topics for a single LocationUpdate:
 *   trip.{id}.location and vehicle.{id}.location
 */
@Service
@RequiredArgsConstructor
public class LocationBroadcastServiceImpl implements LocationBroadcastService {
    private static final Logger log = LoggerFactory.getLogger(LocationBroadcastServiceImpl.class);

    private final WebSocketSessionManager sessionManager;
    private final WebSocketRateLimitService rateLimitService;

    // Simple counters for broadcast statistics
    private final AtomicLong locationUpdatesTotal  = new AtomicLong(0);
    private final AtomicLong stopEventsTotal        = new AtomicLong(0);
    private final AtomicLong tripStatusEventsTotal  = new AtomicLong(0);
    private final AtomicLong vehicleStatusEventsTotal = new AtomicLong(0);
    private volatile LocalDateTime statsStartTime   = LocalDateTime.now();

    // ── LocationUpdate ────────────────────────────────────────────────────────

    @Override
    public void broadcastLocationUpdate(LocationUpdate update) {
        log.debug("Broadcasting location update: trip={}, vehicle={}, lat={}, lon={}",
                update.tripId(), update.vehicleId(), update.latitude(), update.longitude());

        // Rate-limiting gate
        if (!rateLimitService.canSendLocationUpdate(update.tripId(), update.vehicleId())) {
            log.warn("Location update rate-limited for trip={} vehicle={}",
                    update.tripId(), update.vehicleId());
            return;
        }

        // Record usage
        rateLimitService.recordLocationUpdate(update.tripId(), update.vehicleId(), update.timestamp());
        locationUpdatesTotal.incrementAndGet();

        // Build the outbound payload once and reuse
        Map<String, Object> payload = buildLocationPayload(update);
        WebSocketMessage message = WebSocketMessage.locationUpdate(payload);

        // Fan-out: trip topic  (primary – shippers + fleet owners watching this trip)
        String tripTopic = "trip." + update.tripId() + ".location";
        sessionManager.broadcastToTopic(tripTopic, message);

        // Fan-out: vehicle topic  (fleet-owner dashboards)
        String vehicleTopic = "vehicle." + update.vehicleId() + ".location";
        sessionManager.broadcastToTopic(vehicleTopic, message);

        log.debug("Location update broadcast complete for trip={}", update.tripId());
    }

    // ── StopEvent ─────────────────────────────────────────────────────────────

    @Override
    public void broadcastStopEvent(StopEvent event) {
        log.info("Broadcasting stop event: trip={}, stop={}, type={}",
                event.tripId(), event.stopId(), event.eventType());

        stopEventsTotal.incrementAndGet();

        Map<String, Object> payload = Map.of(
            "tripId",    event.tripId(),
            "stopId",    event.stopId(),
            "vehicleId", event.vehicleId(),
            "driverId",  event.driverId(),
            "stopName",  event.stopName(),
            "eventType", event.eventType(),
            "latitude",  event.latitude(),
            "longitude", event.longitude(),
            "timestamp", event.timestamp()
        );

        WebSocketMessage message = WebSocketMessage.system("STOP_EVENT", payload);

        // Trip topic – everyone watching the trip needs stop events
        sessionManager.broadcastToTopic("trip." + event.tripId() + ".location", message);
    }

    // ── TripStatusEvent ───────────────────────────────────────────────────────

    @Override
    public void broadcastTripStatusChange(TripStatusEvent event) {
        log.info("Broadcasting trip status change: trip={} {} → {}",
                event.tripId(), event.previousStatus(), event.newStatus());

        tripStatusEventsTotal.incrementAndGet();

        Map<String, Object> payload = Map.of(
            "tripId",         event.tripId(),
            "vehicleId",      event.vehicleId() != null ? event.vehicleId() : "",
            "driverId",       event.driverId()  != null ? event.driverId()  : "",
            "shipperId",      event.shipperId() != null ? event.shipperId() : "",
            "previousStatus", event.previousStatus() != null ? event.previousStatus() : "",
            "newStatus",      event.newStatus(),
            "reason",         event.reason() != null ? event.reason() : "",
            "timestamp",      event.timestamp()
        );

        WebSocketMessage message = WebSocketMessage.system("TRIP_STATUS_CHANGED", payload);

        // Push to the trip-location topic (shipper + fleet-owner are subscribed)
        sessionManager.broadcastToTopic("trip." + event.tripId() + ".location", message);

        // Push to the shipper's personal notification topic
        if (event.shipperId() != null) {
            sessionManager.broadcastToTopic("user." + event.shipperId() + ".notifications", message);
        }

        // Push to the driver's personal topic
        if (event.driverId() != null) {
            sessionManager.broadcastToTopic("user." + event.driverId() + ".notifications", message);
        }

    }

    // ── VehicleStatusEvent ────────────────────────────────────────────────────

    @Override
    public void broadcastVehicleStatusUpdate(VehicleStatusEvent event) {
        log.info("Broadcasting vehicle status: vehicle={} {} → {}",
                event.vehicleId(), event.previousStatus(), event.newStatus());

        vehicleStatusEventsTotal.incrementAndGet();

        Map<String, Object> payload = Map.ofEntries(
            Map.entry("vehicleId",      event.vehicleId()),
            Map.entry("fleetOwnerId",   event.fleetOwnerId()),
            Map.entry("driverId",       event.driverId()  != null ? event.driverId()  : ""),
            Map.entry("tripId",         event.tripId()    != null ? event.tripId()    : ""),
            Map.entry("previousStatus", event.previousStatus()),
            Map.entry("newStatus",      event.newStatus()),
            Map.entry("latitude",       event.latitude()  != null ? event.latitude()  : ""),
            Map.entry("longitude",      event.longitude() != null ? event.longitude() : ""),
            Map.entry("timestamp",      event.timestamp())
        );

        WebSocketMessage message = WebSocketMessage.system("VEHICLE_STATUS_CHANGED", payload);

        // Vehicle topic
        sessionManager.broadcastToTopic("vehicle." + event.vehicleId() + ".location", message);

        // Active-trip topic if the vehicle is on a trip
        if (event.tripId() != null) {
            sessionManager.broadcastToTopic("trip." + event.tripId() + ".location", message);
        }
    }

    // ── Stats ─────────────────────────────────────────────────────────────────

    /**
     * Return a statistics snapshot.  Called by the monitoring controller.
     */
    public BroadcastStats getStats() {
        WebSocketSessionManager.SessionStatistics sesStats = sessionManager.getSessionStatistics();
        return new BroadcastStats(
            locationUpdatesTotal.get(),
            /* lastMinute – simplified */ locationUpdatesTotal.get(),
            stopEventsTotal.get(),
            tripStatusEventsTotal.get(),
            vehicleStatusEventsTotal.get(),
            sesStats.subscriptionsByTopic().size(),
            sesStats.activeSessions(),
            statsStartTime
        );
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private Map<String, Object> buildLocationPayload(LocationUpdate u) {
        return Map.ofEntries(
            Map.entry("tripId",          u.tripId()),
            Map.entry("vehicleId",       u.vehicleId()),
            Map.entry("driverId",        u.driverId()),
            Map.entry("latitude",        u.latitude()),
            Map.entry("longitude",       u.longitude()),
            Map.entry("altitude",        u.altitude()        != null ? u.altitude()        : ""),
            Map.entry("speedKmh",        u.speedKmh()        != null ? u.speedKmh()        : ""),
            Map.entry("headingDegrees",  u.headingDegrees()  != null ? u.headingDegrees()  : ""),
            Map.entry("accuracyMetres",  u.accuracyMetres()  != null ? u.accuracyMetres()  : ""),
            Map.entry("timestamp",       u.timestamp()),
            Map.entry("source",          u.source())
        );
    }
}
