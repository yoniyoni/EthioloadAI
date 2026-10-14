package com.ethioloadai.tracking.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Phase 17: Geofencing for trip stops.
 *
 * Detects when a vehicle enters or exits the radius of a trip-stop and
 * fires arrival/departure events that are broadcast over WebSocket.
 */
public interface GeofencingService {

    /**
     * Check whether the given GPS fix triggers any geofence event for the trip.
     * Called on every accepted location update.
     *
     * @param tripId    the trip
     * @param vehicleId the vehicle
     * @param latitude  current latitude
     * @param longitude current longitude
     * @param timestamp fix timestamp
     * @return list of events fired (usually 0 or 1)
     */
    List<GeofenceEvent> checkGeofences(Long tripId, Long vehicleId,
                                       BigDecimal latitude, BigDecimal longitude,
                                       LocalDateTime timestamp);

    /**
     * Register or update a geofence for a trip stop.
     */
    void registerStopGeofence(Long tripId, Long stopId, String stopName,
                              BigDecimal latitude, BigDecimal longitude,
                              double radiusMetres);

    /**
     * Remove all geofences for a completed/cancelled trip.
     */
    void removeAllGeofences(Long tripId);

    // ── Value objects ─────────────────────────────────────────────────────────

    enum GeofenceEventType { ENTERED, EXITED }

    record GeofenceEvent(
        Long              tripId,
        Long              stopId,
        String            stopName,
        Long              vehicleId,
        GeofenceEventType eventType,
        BigDecimal        latitude,
        BigDecimal        longitude,
        double            distanceMetres,
        LocalDateTime     timestamp
    ) {}

    record StopGeofence(
        Long       tripId,
        Long       stopId,
        String     stopName,
        BigDecimal centerLat,
        BigDecimal centerLon,
        double     radiusMetres
    ) {}
}
