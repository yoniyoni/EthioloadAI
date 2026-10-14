package com.ethioloadai.settlement.service;

import com.ethioloadai.settlement.entity.SettlementOutboxRecord;
import com.ethioloadai.settlement.repository.SettlementOutboxRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class SettlementOutboxWorker {

    private final SettlementOutboxRepository outboxRepository;
    private final CarrierSettlementService settlementService;

    @Transactional
    public void process(Long outboxId) {
        SettlementOutboxRecord record = outboxRepository.findByIdForUpdate(outboxId)
                .orElse(null);
        if (record == null || record.getStatus() == SettlementOutboxRecord.Status.PROCESSED) {
            return;
        }

        settlementService.createSettlement(record.getBookingId(), record.getProofOfDeliveryId());
        record.setStatus(SettlementOutboxRecord.Status.PROCESSED);
        record.setProcessedAt(LocalDateTime.now());
        record.setLastError(null);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(Long outboxId, RuntimeException failure) {
        SettlementOutboxRecord record = outboxRepository.findByIdForUpdate(outboxId)
                .orElse(null);
        if (record == null || record.getStatus() == SettlementOutboxRecord.Status.PROCESSED) {
            return;
        }

        int attempts = record.getAttemptCount() + 1;
        long backoffMinutes = Math.min(60L, 1L << Math.min(attempts - 1, 6));
        record.setAttemptCount(attempts);
        record.setNextAttemptAt(LocalDateTime.now().plusMinutes(backoffMinutes));
        record.setLastError(failure.getClass().getSimpleName());
    }
}
