package com.ethioloadai.settlement.service;

import com.ethioloadai.booking.entity.Booking;
import com.ethioloadai.booking.repository.BookingRepository;
import com.ethioloadai.settlement.entity.SettlementOutboxRecord;
import com.ethioloadai.settlement.repository.CarrierSettlementRepository;
import com.ethioloadai.settlement.repository.SettlementOutboxRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class SettlementOutboxService {

    private final BookingRepository bookingRepository;
    private final CarrierSettlementRepository settlementRepository;
    private final SettlementOutboxRepository outboxRepository;

    @Transactional
    public void enqueueVerifiedDelivery(Long bookingId, Long proofId) {
        Booking booking = bookingRepository.findByIdForUpdate(bookingId)
                .orElseThrow(() -> new IllegalStateException("Verified delivery booking no longer exists"));
        if (booking.getCarrierCompanyId() == null || settlementRepository.existsByBooking_Id(bookingId)) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        SettlementOutboxRecord record = outboxRepository.findByBookingIdForUpdate(bookingId)
                .orElseGet(() -> SettlementOutboxRecord.builder()
                        .bookingId(bookingId)
                        .proofOfDeliveryId(proofId)
                        .status(SettlementOutboxRecord.Status.PENDING)
                        .attemptCount(0)
                        .createdAt(now)
                        .nextAttemptAt(now)
                        .build());
        if (record.getId() != null && record.getStatus() == SettlementOutboxRecord.Status.PROCESSED) {
            record.setStatus(SettlementOutboxRecord.Status.PENDING);
            record.setProofOfDeliveryId(proofId);
            record.setAttemptCount(0);
            record.setLastError(null);
            record.setProcessedAt(null);
            record.setNextAttemptAt(now);
        }
        outboxRepository.save(record);
    }
}
