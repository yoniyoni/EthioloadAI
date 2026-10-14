package com.ethioloadai.payment.repository;

import com.ethioloadai.payment.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    /** Find the payment for a booking. */
    Optional<Payment> findByBooking_Id(Long bookingId);

    /** Check if payment already exists (duplicate prevention). */
    boolean existsByBooking_Id(Long bookingId);

    /** Total commission collected — for admin analytics. */
    @Query("SELECT COALESCE(SUM(p.commissionAmount), 0) FROM Payment p WHERE p.paymentStatus = :status")
    BigDecimal totalCommissionCollected(@Param("status") Payment.PaymentStatus status);

    default BigDecimal totalCommissionCollected() {
        return totalCommissionCollected(Payment.PaymentStatus.PAID);
    }

    /** Total revenue for admin dashboard. */
    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM Payment p WHERE p.paymentStatus = :status")
    BigDecimal totalRevenue(@Param("status") Payment.PaymentStatus status);

    default BigDecimal totalRevenue() {
        return totalRevenue(Payment.PaymentStatus.PAID);
    }

    /** Aggregate paid payment amounts and row counts by creation month. */
    @Query("SELECT EXTRACT(MONTH FROM p.createdAt) AS monthNumber, " +
           "COALESCE(SUM(p.amount), 0) AS amount, COUNT(p) AS paymentCount " +
           "FROM Payment p " +
           "WHERE p.paymentStatus = :status " +
           "AND p.createdAt >= :yearStart AND p.createdAt < :nextYearStart " +
           "GROUP BY EXTRACT(MONTH FROM p.createdAt)")
    List<MonthlyPaymentAggregate> aggregateMonthlyPaidRevenue(
            @Param("status") Payment.PaymentStatus status,
            @Param("yearStart") LocalDateTime yearStart,
            @Param("nextYearStart") LocalDateTime nextYearStart);

    interface MonthlyPaymentAggregate {
        Integer getMonthNumber();
        BigDecimal getAmount();
        Long getPaymentCount();
    }

    /** Find a payment and verify the booking belongs to a given driver (IDOR). */
    @Query("SELECT p FROM Payment p WHERE p.booking.id = :bookingId " +
           "AND p.booking.driver.id = :driverId")
    Optional<Payment> findByBookingIdAndDriverId(@Param("bookingId") Long bookingId,
                                                  @Param("driverId") Long driverId);

    // ── Phase 8-9 additions ───────────────────────────────────────────────────

    /** All payments where the authenticated driver is the booking driver. */
    @Query("SELECT p FROM Payment p WHERE p.booking.driver.id = :driverId " +
           "ORDER BY p.createdAt DESC")
    List<Payment> findByDriver(@Param("driverId") Long driverId);

    /** All payments where the authenticated shipper owns the freight. */
    @Query("SELECT p FROM Payment p " +
           "WHERE p.booking.cargoRequest.shipper.id = :shipperId " +
           "ORDER BY p.createdAt DESC")
    List<Payment> findByShipper(@Param("shipperId") Long shipperId);

    /** All payments for bookings whose driver belongs to this fleet owner. */
    @Query("SELECT p FROM Payment p " +
           "WHERE p.booking.driver.fleetOwnerId = :fleetOwnerId " +
           "ORDER BY p.createdAt DESC")
    List<Payment> findByFleetOwner(@Param("fleetOwnerId") Long fleetOwnerId);

    /**
     * Load payment with a pessimistic write lock for concurrent-completion protection.
     * Uses EntityManager.find(..., PESSIMISTIC_WRITE) at the service layer instead;
     * this named query is kept for JPQL reference.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Payment p WHERE p.id = :id")
    Optional<Payment> findByIdWithLock(@Param("id") Long id);

    /** All payments for a freight shipment via its bookings, ordered newest first. */
    @Query("SELECT p FROM Payment p WHERE p.booking.cargoRequest.id = :freightId " +
           "ORDER BY p.createdAt DESC")
    List<Payment> findByFreightId(@Param("freightId") Long freightId);
}
