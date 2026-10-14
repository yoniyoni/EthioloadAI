package com.ethioloadai.settlement.service;

import com.ethioloadai.settlement.entity.SettlementOutboxRecord;
import com.ethioloadai.settlement.repository.SettlementOutboxRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "settlement.outbox.enabled", havingValue = "true", matchIfMissing = true)
public class SettlementOutboxScheduler {

    private static final Logger log = LoggerFactory.getLogger(SettlementOutboxScheduler.class);
    private final SettlementOutboxRepository outboxRepository;
    private final SettlementOutboxWorker worker;

    @Scheduled(fixedDelayString = "${settlement.outbox.poll-interval-ms:60000}")
    public void processDueItems() {
        List<Long> dueIds = outboxRepository
                .findTop100ByStatusAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
                        SettlementOutboxRecord.Status.PENDING, LocalDateTime.now())
                .stream()
                .map(SettlementOutboxRecord::getId)
                .toList();

        for (Long outboxId : dueIds) {
            try {
                worker.process(outboxId);
            } catch (RuntimeException failure) {
                log.error("Settlement outbox item {} failed with {}",
                        outboxId, failure.getClass().getSimpleName());
                try {
                    worker.recordFailure(outboxId, failure);
                } catch (RuntimeException recordingFailure) {
                    log.error("Could not record failure for settlement outbox item {} ({})",
                            outboxId, recordingFailure.getClass().getSimpleName());
                }
            }
        }
    }
}
