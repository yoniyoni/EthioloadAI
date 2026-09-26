package com.ethioloadai.websocket.monitoring;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Phase 14: WebSocket performance monitoring and metrics collection.
 */
public interface WebSocketMetricsService {

    void recordMessageSent(String topic, int sizeBytes);
    void recordMessageDeliveryLatencyMs(String topic, long latencyMs);
    void recordBroadcastFanOut(String topic, int subscriberCount);
    void recordRateLimitHit(String limitType);

    MetricsSnapshot getSnapshot();
    void reset();

    record MetricsSnapshot(
        long   messagesSentTotal,
        long   bytesTransferredTotal,
        double avgDeliveryLatencyMs,
        long   maxDeliveryLatencyMs,
        long   rateLimitHitsTotal,
        Map<String, Long>   messagesByTopic,
        Map<String, Double> avgLatencyByTopic,
        LocalDateTime       since,
        LocalDateTime       snapshotTime
    ) {}
}
