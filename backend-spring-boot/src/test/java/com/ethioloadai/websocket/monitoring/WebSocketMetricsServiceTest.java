package com.ethioloadai.websocket.monitoring;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 14 / Phase 19 – WebSocketMetricsService unit tests.
 */
class WebSocketMetricsServiceTest {

    private WebSocketMetricsServiceImpl metricsService;

    @BeforeEach
    void setUp() {
        metricsService = new WebSocketMetricsServiceImpl();
    }

    @Test
    void initialSnapshot_allZeros() {
        WebSocketMetricsService.MetricsSnapshot snap = metricsService.getSnapshot();
        assertThat(snap.messagesSentTotal()).isZero();
        assertThat(snap.bytesTransferredTotal()).isZero();
        assertThat(snap.avgDeliveryLatencyMs()).isZero();
        assertThat(snap.maxDeliveryLatencyMs()).isZero();
        assertThat(snap.rateLimitHitsTotal()).isZero();
        assertThat(snap.messagesByTopic()).isEmpty();
        assertThat(snap.since()).isNotNull();
        assertThat(snap.snapshotTime()).isNotNull();
    }

    @Test
    void recordMessageSent_incrementsCountersAndBytes() {
        metricsService.recordMessageSent("trip.1.location", 512);
        metricsService.recordMessageSent("trip.1.location", 256);
        metricsService.recordMessageSent("fleet.vehicles",  128);

        WebSocketMetricsService.MetricsSnapshot snap = metricsService.getSnapshot();
        assertThat(snap.messagesSentTotal()).isEqualTo(3);
        assertThat(snap.bytesTransferredTotal()).isEqualTo(896);
        assertThat(snap.messagesByTopic().get("trip.1.location")).isEqualTo(2L);
        assertThat(snap.messagesByTopic().get("fleet.vehicles")).isEqualTo(1L);
    }

    @Test
    void recordLatency_calculatesAverageAndMax() {
        metricsService.recordMessageDeliveryLatencyMs("trip.1.location", 10);
        metricsService.recordMessageDeliveryLatencyMs("trip.1.location", 30);
        metricsService.recordMessageDeliveryLatencyMs("fleet.vehicles",  50);

        WebSocketMetricsService.MetricsSnapshot snap = metricsService.getSnapshot();
        assertThat(snap.avgDeliveryLatencyMs()).isEqualTo(30.0); // (10+30+50)/3
        assertThat(snap.maxDeliveryLatencyMs()).isEqualTo(50L);
        assertThat(snap.avgLatencyByTopic().get("trip.1.location")).isEqualTo(20.0); // (10+30)/2
    }

    @Test
    void recordRateLimitHit_incrementsCounter() {
        metricsService.recordRateLimitHit("MESSAGE_RATE_LIMIT");
        metricsService.recordRateLimitHit("SUBSCRIPTION_RATE_LIMIT");

        assertThat(metricsService.getSnapshot().rateLimitHitsTotal()).isEqualTo(2);
    }

    @Test
    void reset_clearsAllCounters() {
        metricsService.recordMessageSent("trip.1.location", 100);
        metricsService.recordMessageDeliveryLatencyMs("trip.1.location", 25);
        metricsService.recordRateLimitHit("X");

        metricsService.reset();

        WebSocketMetricsService.MetricsSnapshot snap = metricsService.getSnapshot();
        assertThat(snap.messagesSentTotal()).isZero();
        assertThat(snap.bytesTransferredTotal()).isZero();
        assertThat(snap.maxDeliveryLatencyMs()).isZero();
        assertThat(snap.rateLimitHitsTotal()).isZero();
        assertThat(snap.messagesByTopic()).isEmpty();
    }

    @Test
    void recordBroadcastFanOut_doesNotThrow() {
        metricsService.recordBroadcastFanOut("fleet.vehicles", 42);
        // no assertion needed — just verifying it doesn't blow up
    }

    @Test
    void snapshotTime_isAfterOrEqualSince() {
        WebSocketMetricsService.MetricsSnapshot snap = metricsService.getSnapshot();
        assertThat(snap.snapshotTime()).isAfterOrEqualTo(snap.since());
    }
}
