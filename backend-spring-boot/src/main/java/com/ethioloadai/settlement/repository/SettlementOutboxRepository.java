package com.ethioloadai.settlement.repository;

import com.ethioloadai.settlement.entity.SettlementOutboxRecord;
import com.ethioloadai.settlement.entity.SettlementOutboxRecord.Status;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface SettlementOutboxRepository extends JpaRepository<SettlementOutboxRecord, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM SettlementOutboxRecord o WHERE o.bookingId = :bookingId")
    Optional<SettlementOutboxRecord> findByBookingIdForUpdate(@Param("bookingId") Long bookingId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM SettlementOutboxRecord o WHERE o.id = :id")
    Optional<SettlementOutboxRecord> findByIdForUpdate(@Param("id") Long id);

    List<SettlementOutboxRecord> findTop100ByStatusAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
            Status status, LocalDateTime dueAt);
}
