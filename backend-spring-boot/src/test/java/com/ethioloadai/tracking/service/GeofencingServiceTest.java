package com.ethioloadai.tracking.service;

import com.ethioloadai.websocket.broadcasting.LocationBroadcastService;
import com.ethioloadai.websocket.broadcasting.LocationBroadcastServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Phase 17 / Phase 19 – GeofencingService unit tests.
 */
@ExtendWith(MockitoExtension.class)
class GeofencingServiceTest {

    @Mock
    private LocationBroadcastServiceImpl broadcastService;

    private GeofencingServiceImpl geofencingService;

    private static final Long TRIP_ID    = 1L;
    private static final Long STOP_ID    = 10L;
    private static final Long VEHICLE_ID = 100L;

    // Meskel Square, Addis Ababa
    private static final BigDecimal STOP_LAT = new BigDecimal("9.0060000");
    private static final BigDecimal STOP_LON = new BigDecimal("38.7614000");

    @BeforeEach
    void setUp() {
        geofencingService = new GeofencingServiceImpl(broadcastService);
        geofencingService.registerStopGeofence(TRIP_ID, STOP_ID, "Meskel Square",
            STOP_LAT, STOP_LON, 200.0);
    }

    // ── Registration ──────────────────────────────────────────────────────────

    @Test
    void noEventsBeforeVehicleApproaches() {
        // Vehicle far away (Bole Airport, ~8 km)
        List<GeofencingService.GeofenceEvent> events = geofencingService.checkGeofences(
            TRIP_ID, VEHICLE_ID,
            new BigDecimal("8.9778"), new BigDecimal("38.7993"),
            LocalDateTime.now());
        assertThat(events).isEmpty();
    }

    // ── ENTERED event ─────────────────────────────────────────────────────────

    @Test
    void firesEnteredEvent_whenVehicleEntersRadius() {
        // Position 50m from stop centre
        BigDecimal nearLat = STOP_LAT.add(new BigDecimal("0.0004")); // ~44m north
        List<GeofencingService.GeofenceEvent> events = geofencingService.checkGeofences(
            TRIP_ID, VEHICLE_ID, nearLat, STOP_LON, LocalDateTime.now());

        assertThat(events).hasSize(1);
        assertThat(events.get(0).eventType())
            .isEqualTo(GeofencingService.GeofenceEventType.ENTERED);
        assertThat(events.get(0).stopId()).isEqualTo(STOP_ID);
        verify(broadcastService).broadcastStopEvent(any(LocationBroadcastService.StopEvent.class));
    }

    @Test
    void noRepeatEnteredEvent_whenAlreadyInside() {
        BigDecimal nearLat = STOP_LAT.add(new BigDecimal("0.0004"));
        // First entry
        geofencingService.checkGeofences(TRIP_ID, VEHICLE_ID, nearLat, STOP_LON, LocalDateTime.now());
        // Second check while still inside — no new event
        List<GeofencingService.GeofenceEvent> events =
            geofencingService.checkGeofences(TRIP_ID, VEHICLE_ID, nearLat, STOP_LON, LocalDateTime.now());
        assertThat(events).isEmpty();
        // broadcast called exactly once
        verify(broadcastService, times(1)).broadcastStopEvent(any());
    }

    // ── EXITED event ──────────────────────────────────────────────────────────

    @Test
    void firesExitedEvent_whenVehicleLeavesRadius() {
        BigDecimal nearLat = STOP_LAT.add(new BigDecimal("0.0004"));
        // Enter
        geofencingService.checkGeofences(TRIP_ID, VEHICLE_ID, nearLat, STOP_LON, LocalDateTime.now());
        // Exit (Bole Airport)
        List<GeofencingService.GeofenceEvent> events = geofencingService.checkGeofences(
            TRIP_ID, VEHICLE_ID,
            new BigDecimal("8.9778"), new BigDecimal("38.7993"),
            LocalDateTime.now());

        assertThat(events).hasSize(1);
        assertThat(events.get(0).eventType())
            .isEqualTo(GeofencingService.GeofenceEventType.EXITED);
        // Total: 1 ARRIVED + 1 DEPARTED
        verify(broadcastService, times(2)).broadcastStopEvent(any());
    }

    // ── Cleanup ───────────────────────────────────────────────────────────────

    @Test
    void noEventsAfterRemoveAllGeofences() {
        geofencingService.removeAllGeofences(TRIP_ID);

        BigDecimal nearLat = STOP_LAT.add(new BigDecimal("0.0004"));
        List<GeofencingService.GeofenceEvent> events =
            geofencingService.checkGeofences(TRIP_ID, VEHICLE_ID, nearLat, STOP_LON, LocalDateTime.now());
        assertThat(events).isEmpty();
        verifyNoInteractions(broadcastService);
    }

    // ── Different trips ───────────────────────────────────────────────────────

    @Test
    void geofenceIsolatedByTrip() {
        BigDecimal nearLat = STOP_LAT.add(new BigDecimal("0.0004"));
        // Check for a different trip — no fences registered for trip 2
        List<GeofencingService.GeofenceEvent> events =
            geofencingService.checkGeofences(2L, VEHICLE_ID, nearLat, STOP_LON, LocalDateTime.now());
        assertThat(events).isEmpty();
    }

    // ── Default radius fallback ───────────────────────────────────────────────

    @Test
    void registersGeofenceWithDefaultRadius_whenZeroPassed() {
        geofencingService.registerStopGeofence(99L, 99L, "Default Radius Stop",
            STOP_LAT, STOP_LON, 0.0);  // 0 → default 200m

        BigDecimal nearLat = STOP_LAT.add(new BigDecimal("0.0004"));
        List<GeofencingService.GeofenceEvent> events =
            geofencingService.checkGeofences(99L, VEHICLE_ID, nearLat, STOP_LON, LocalDateTime.now());
        assertThat(events).hasSize(1);
    }
}
