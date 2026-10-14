package com.ethioloadai.websocket.broadcasting;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Service that broadcasts real-time GPS location updates to WebSocket subscribers.
 *
 * This is the central pipeline:
 *   Driver GPS update → validation → persistence → broadcast to topic subscribers
 *
 * Topics notified:
 *   - trip.{tripId}.location    → shipper + fleet-owner watching the trip
 *   - vehicle.{vehicleId}.location → fleet-owner dashboard
 */
public interface LocationBroadcastService {

    /**
     * Accept a validated GPS fix for a trip and fan it out to all subscribers.
     *
     * @param update the GPS location update payload
     */
    void broadcastLocationUpdate(LocationUpdate update);

    /**
     * Broadcast a driver's departure from / arrival at a trip stop.
     *
     * @param event stop event payload
     */
    void broadcastStopEvent(StopEvent event);

    /**
     * Broadcast a trip status change (STARTED, COMPLETED, CANCELLED …).
     *
     * @param event trip status event payload
     */
    void broadcastTripStatusChange(TripStatusEvent event);

    /**
     * Broadcast a fleet-level vehicle status update (IDLE, ON_TRIP, OFFLINE …).
     *
     * @param event vehicle status event payload
     */
    void broadcastVehicleStatusUpdate(VehicleStatusEvent event);

    // ── Payload types ─────────────────────────────────────────────────────────

    /**
     * Immutable GPS location fix pushed by a driver.
     */
    record LocationUpdate(
        Long   tripId,
        Long   vehicleId,
        Long   driverId,
        BigDecimal latitude,
        BigDecimal longitude,
        BigDecimal altitude,          // metres, nullable
        BigDecimal speedKmh,          // nullable
        BigDecimal headingDegrees,    // 0-359, nullable
        BigDecimal accuracyMetres,    // GPS accuracy, nullable
        LocalDateTime timestamp,
        String source                 // "DRIVER_APP", "FLEET_DEVICE", …
    ) {}

    /**
     * A trip-stop arrival or departure event.
     */
    record StopEvent(
        Long   tripId,
        Long   stopId,
        Long   vehicleId,
        Long   driverId,
        String stopName,
        String eventType,             // "ARRIVED", "DEPARTED"
        LocalDateTime timestamp,
        BigDecimal latitude,
        BigDecimal longitude
    ) {}

    /**
     * A change in a trip's lifecycle status.
     */
    record TripStatusEvent(
        Long   tripId,
        Long   vehicleId,
        Long   driverId,
        Long   shipperId,
        String previousStatus,
        String newStatus,
        String reason,
        LocalDateTime timestamp
    ) {}

    /**
     * A change in a vehicle's operational status.
     */
    record VehicleStatusEvent(
        Long   vehicleId,
        Long   fleetOwnerId,
        Long   driverId,             // nullable
        Long   tripId,               // nullable
        String previousStatus,
        String newStatus,
        BigDecimal latitude,         // nullable – last known position
        BigDecimal longitude,        // nullable
        LocalDateTime timestamp
    ) {}

    /**
     * Broadcast statistics snapshot.
     */
    record BroadcastStats(
        long locationUpdatesTotal,
        long locationUpdatesLastMinute,
        long stopEventsTotal,
        long tripStatusEventsTotal,
        long vehicleStatusEventsTotal,
        int  activeTopics,
        int  activeSubscribers,
        LocalDateTime since
    ) {}
}
