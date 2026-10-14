package com.ethioloadai.websocket.monitoring;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * Phase 14 metrics implementation using lock-free atomic counters.
 */
@Service
public class WebSocketMetricsServiceImpl implements WebSocketMetricsService {
    private static final Logger log = LoggerFactory.getLogger(WebSocketMetricsServiceImpl.class);

    private final AtomicLong messagesSent     = new AtomicLong();
    private final AtomicLong bytesTransferred = new AtomicLong();
    private final AtomicLong latencySum       = new AtomicLong();
    private final AtomicLong latencyCount     = new AtomicLong();
    private final AtomicLong maxLatency       = new AtomicLong();
    private final AtomicLong rateLimitHits    = new AtomicLong();

    private final ConcurrentHashMap<String, LongAdder> messagesByTopic    = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, LongAdder> latencySumByTopic  = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, LongAdder> latencyCountByTopic = new ConcurrentHashMap<>();

    private volatile LocalDateTime since = LocalDateTime.now();

    @Override
    public void recordMessageSent(String topic, int sizeBytes) {
        messagesSent.incrementAndGet();
        bytesTransferred.addAndGet(sizeBytes);
        messagesByTopic.computeIfAbsent(topic, k -> new LongAdder()).increment();
    }

    @Override
    public void recordMessageDeliveryLatencyMs(String topic, long latencyMs) {
        latencySum.addAndGet(latencyMs);
        latencyCount.incrementAndGet();
        maxLatency.updateAndGet(cur -> Math.max(cur, latencyMs));
        latencySumByTopic.computeIfAbsent(topic,  k -> new LongAdder()).add(latencyMs);
        latencyCountByTopic.computeIfAbsent(topic, k -> new LongAdder()).increment();
    }

    @Override
    public void recordBroadcastFanOut(String topic, int subscriberCount) {
        // Future: histogram of fan-out sizes; record for now
        log.debug("Fan-out broadcast: topic={}, subscribers={}", topic, subscriberCount);
    }

    @Override
    public void recordRateLimitHit(String limitType) {
        rateLimitHits.incrementAndGet();
        log.debug("Rate limit hit: type={}", limitType);
    }

    @Override
    public MetricsSnapshot getSnapshot() {
        long cnt = latencyCount.get();
        double avgLatency = cnt == 0 ? 0.0 : (double) latencySum.get() / cnt;

        Map<String, Long> byTopic = new ConcurrentHashMap<>();
        messagesByTopic.forEach((k, v) -> byTopic.put(k, v.sum()));

        Map<String, Double> avgLatByTopic = new ConcurrentHashMap<>();
        latencyCountByTopic.forEach((k, v) -> {
            long c = v.sum();
            if (c > 0) {
                LongAdder s = latencySumByTopic.get(k);
                avgLatByTopic.put(k, s == null ? 0.0 : (double) s.sum() / c);
            }
        });

        return new MetricsSnapshot(
            messagesSent.get(),
            bytesTransferred.get(),
            avgLatency,
            maxLatency.get(),
            rateLimitHits.get(),
            byTopic,
            avgLatByTopic,
            since,
            LocalDateTime.now()
        );
    }

    @Override
    public void reset() {
        messagesSent.set(0); bytesTransferred.set(0);
        latencySum.set(0);   latencyCount.set(0); maxLatency.set(0);
        rateLimitHits.set(0);
        messagesByTopic.clear(); latencySumByTopic.clear(); latencyCountByTopic.clear();
        since = LocalDateTime.now();
        log.info("WebSocket metrics reset");
    }
}
