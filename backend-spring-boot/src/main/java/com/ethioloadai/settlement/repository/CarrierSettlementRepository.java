package com.ethioloadai.settlement.repository;

import com.ethioloadai.settlement.entity.CarrierSettlement;
import com.ethioloadai.settlement.entity.CarrierSettlement.SettlementStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CarrierSettlementRepository extends JpaRepository<CarrierSettlement, Long> {

    /** One settlement per booking (unique DB constraint). */
    Optional<CarrierSettlement> findByBooking_Id(Long bookingId);

    /** Pessimistic write lock for concurrent settlement creation guard. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM CarrierSettlement s WHERE s.booking.id = :bookingId")
    Optional<CarrierSettlement> findByBookingIdForUpdate(@Param("bookingId") Long bookingId);

    boolean existsByBooking_Id(Long bookingId);

    /** Carrier's own settlements for financial dashboard. */
    @Query("SELECT s FROM CarrierSettlement s " +
           "WHERE s.carrierCompany.id = :carrierCompanyId " +
           "ORDER BY s.createdAt DESC")
    List<CarrierSettlement> findByCarrierCompanyId(@Param("carrierCompanyId") Long carrierCompanyId);

    /** Carrier settlements filtered by status. */
    @Query("SELECT s FROM CarrierSettlement s " +
           "WHERE s.carrierCompany.id = :carrierCompanyId AND s.status = :status " +
           "ORDER BY s.createdAt DESC")
    List<CarrierSettlement> findByCarrierCompanyIdAndStatus(
            @Param("carrierCompanyId") Long carrierCompanyId,
            @Param("status") SettlementStatus status);

    /** Admin: all settlements, newest first. */
    @Query("SELECT s FROM CarrierSettlement s ORDER BY s.createdAt DESC")
    List<CarrierSettlement> findAllOrderByCreatedAtDesc();

    /** Admin: settlements in a specific status. */
    List<CarrierSettlement> findByStatusOrderByCreatedAtDesc(SettlementStatus status);
}
