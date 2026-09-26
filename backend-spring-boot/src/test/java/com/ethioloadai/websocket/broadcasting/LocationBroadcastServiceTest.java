package com.ethioloadai.websocket.broadcasting;

import com.ethioloadai.websocket.security.WebSocketRateLimitService;
import com.ethioloadai.websocket.service.WebSocketSessionManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Phase 11 / Phase 19 – LocationBroadcastService unit tests.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LocationBroadcastServiceTest {

    @Mock private WebSocketSessionManager sessionManager;
    @Mock private WebSocketRateLimitService rateLimitService;

    private LocationBroadcastServiceImpl broadcastService;

    @BeforeEach
    void setUp() {
        broadcastService = new LocationBroadcastServiceImpl(sessionManager, rateLimitService);
    }

    // ── LocationUpdate fan-out ────────────────────────────────────────────────

    @Test
    void broadcastLocationUpdate_fansOutToThreeTopics() {
        when(rateLimitService.canSendLocationUpdate(anyLong(), anyLong())).thenReturn(true);
        when(sessionManager.getSessionStatistics()).thenReturn(emptyStats());

        broadcastService.broadcastLocationUpdate(sampleUpdate());

        // trip topic, vehicle topic, fleet topic
        verify(sessionManager).broadcastToTopic(eq("trip.1.location"),    any());
        verify(sessionManager).broadcastToTopic(eq("vehicle.10.location"), any());
        verify(sessionManager).broadcastToTopic(eq("fleet.vehicles"),      any());
        verify(rateLimitService).recordLocationUpdate(eq(1L), eq(10L), any());
    }

    @Test
    void broadcastLocationUpdate_skipsWhenRateLimited() {
        when(rateLimitService.canSendLocationUpdate(anyLong(), anyLong())).thenReturn(false);

        broadcastService.broadcastLocationUpdate(sampleUpdate());

        verifyNoInteractions(sessionManager);
        verify(rateLimitService, never()).recordLocationUpdate(any(), any(), any());
    }

    @Test
    void broadcastLocationUpdate_incrementsCounter() {
        when(rateLimitService.canSendLocationUpdate(anyLong(), anyLong())).thenReturn(true);
        when(sessionManager.getSessionStatistics()).thenReturn(emptyStats());

        broadcastService.broadcastLocationUpdate(sampleUpdate());
        broadcastService.broadcastLocationUpdate(sampleUpdate());

        LocationBroadcastService.BroadcastStats stats = broadcastService.getStats();
        assertThat(stats.locationUpdatesTotal()).isEqualTo(2);
    }

    // ── StopEvent ─────────────────────────────────────────────────────────────

    @Test
    void broadcastStopEvent_notifiesTripAndFleetTopics() {
        broadcastService.broadcastStopEvent(sampleStopEvent());

        verify(sessionManager).broadcastToTopic(eq("trip.1.location"), any());
        verify(sessionManager).broadcastToTopic(eq("fleet.vehicles"),  any());
    }

    // ── TripStatusEvent ───────────────────────────────────────────────────────

    @Test
    void broadcastTripStatus_notifiesAllParticipants() {
        broadcastService.broadcastTripStatusChange(sampleTripStatusEvent());

        verify(sessionManager).broadcastToTopic(eq("trip.1.location"),            any());
        verify(sessionManager).broadcastToTopic(eq("user.20.notifications"),       any()); // shipper
        verify(sessionManager).broadcastToTopic(eq("user.30.notifications"),       any()); // driver
        verify(sessionManager).broadcastToTopic(eq("fleet.vehicles"),              any());
    }

    @Test
    void broadcastTripStatus_skipsNullShipper() {
        LocationBroadcastService.TripStatusEvent event = new LocationBroadcastService.TripStatusEvent(
            1L, 10L, 30L, null, "PENDING", "STARTED", null, LocalDateTime.now());

        broadcastService.broadcastTripStatusChange(event);

        verify(sessionManager, never()).broadcastToTopic(startsWith("user.null"), any());
    }

    // ── VehicleStatusEvent ────────────────────────────────────────────────────

    @Test
    void broadcastVehicleStatus_includesTripTopicWhenOnTrip() {
        broadcastService.broadcastVehicleStatusUpdate(sampleVehicleStatusEvent());

        verify(sessionManager).broadcastToTopic(eq("vehicle.10.location"), any());
        verify(sessionManager).broadcastToTopic(eq("fleet.vehicles"),       any());
        verify(sessionManager).broadcastToTopic(eq("trip.1.location"),     any());
    }

    @Test
    void broadcastVehicleStatus_skipsTripTopicWhenNotOnTrip() {
        LocationBroadcastService.VehicleStatusEvent event = new LocationBroadcastService.VehicleStatusEvent(
            10L, 5L, null, null, "ON_TRIP", "IDLE", null, null, LocalDateTime.now());

        broadcastService.broadcastVehicleStatusUpdate(event);

        verify(sessionManager, never()).broadcastToTopic(startsWith("trip."), any());
    }

    // ── Stats ─────────────────────────────────────────────────────────────────

    @Test
    void getStats_returnsNonNullWithNonNegativeCounts() {
        when(sessionManager.getSessionStatistics()).thenReturn(emptyStats());

        LocationBroadcastService.BroadcastStats stats = broadcastService.getStats();

        assertThat(stats).isNotNull();
        assertThat(stats.locationUpdatesTotal()).isGreaterThanOrEqualTo(0);
        assertThat(stats.stopEventsTotal()).isGreaterThanOrEqualTo(0);
        assertThat(stats.tripStatusEventsTotal()).isGreaterThanOrEqualTo(0);
        assertThat(stats.vehicleStatusEventsTotal()).isGreaterThanOrEqualTo(0);
        assertThat(stats.since()).isNotNull();
    }

    // ── Payload builders ──────────────────────────────────────────────────────

    private LocationBroadcastService.LocationUpdate sampleUpdate() {
        return new LocationBroadcastService.LocationUpdate(
            1L, 10L, 30L,
            new BigDecimal("9.0300000"), new BigDecimal("38.7400000"),
            null, new BigDecimal("60.0"), null, new BigDecimal("15.0"),
            LocalDateTime.now(), "DRIVER_APP"
        );
    }

    private LocationBroadcastService.StopEvent sampleStopEvent() {
        return new LocationBroadcastService.StopEvent(
            1L, 10L, 10L, 30L, "Merkato",
            "ARRIVED", LocalDateTime.now(),
            new BigDecimal("9.0300000"), new BigDecimal("38.7400000")
        );
    }

    private LocationBroadcastService.TripStatusEvent sampleTripStatusEvent() {
        return new LocationBroadcastService.TripStatusEvent(
            1L, 10L, 30L, 20L, "PENDING", "STARTED", null, LocalDateTime.now());
    }

    private LocationBroadcastService.VehicleStatusEvent sampleVehicleStatusEvent() {
        return new LocationBroadcastService.VehicleStatusEvent(
            10L, 5L, 30L, 1L, "IDLE", "ON_TRIP",
            new BigDecimal("9.0300000"), new BigDecimal("38.7400000"),
            LocalDateTime.now()
        );
    }

    private WebSocketSessionManager.SessionStatistics emptyStats() {
        return new WebSocketSessionManager.SessionStatistics(
            0, 0, 0, Map.of(), Map.of(), Map.of());
    }
}
