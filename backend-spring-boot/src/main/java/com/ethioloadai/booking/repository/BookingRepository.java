package com.ethioloadai.booking.repository;

import com.ethioloadai.booking.entity.Booking;
import com.ethioloadai.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Collection;
import java.util.Optional;

@Repository
public interface BookingRepository extends JpaRepository<Booking, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM Booking b WHERE b.id = :bookingId")
    Optional<Booking> findByIdForUpdate(@Param("bookingId") Long bookingId);

    /** Driver's own bookings. */
    List<Booking> findByDriver_Id(Long driverId);

    boolean existsByDriver_Id(Long driverId);

    /** Bookings for a specific freight item. */
    List<Booking> findByCargoRequest_Id(Long freightId);

    Optional<Booking> findFirstByCargoRequest_IdAndBookingStatusNotOrderByIdDesc(
            Long freightId, Booking.BookingStatus excludedStatus);

    @Query("SELECT b.driver FROM Booking b " +
           "WHERE b.cargoRequest.id = :freightId " +
           "AND b.bookingStatus = :status " +
           "GROUP BY b.driver " +
           "ORDER BY MAX(b.id) DESC")
    List<User> findDistinctDriversByCargoRequest_IdAndBookingStatus(
            @Param("freightId") Long freightId,
            @Param("status") Booking.BookingStatus status,
            Pageable pageable);

    @Query("SELECT b FROM Booking b " +
           "WHERE b.cargoRequest.id = :freightId " +
           "AND b.id <> :currentBookingId " +
           "AND b.vehicle IS NOT NULL " +
           "AND b.vehicle.id = :vehicleId " +
           "AND b.bookingStatus IN :statuses " +
           "ORDER BY CASE WHEN b.bookingStatus = :acceptedStatus THEN 0 ELSE 1 END, " +
           "b.updatedAt DESC, b.id DESC")
    List<Booking> findVehicleConflict(
            @Param("freightId") Long freightId,
            @Param("currentBookingId") Long currentBookingId,
            @Param("vehicleId") Long vehicleId,
            @Param("statuses") Collection<Booking.BookingStatus> statuses,
            @Param("acceptedStatus") Booking.BookingStatus acceptedStatus,
            Pageable pageable);

    /** Find a booking and verify it belongs to the given driver (IDOR protection). */
    Optional<Booking> findByIdAndDriver_Id(Long bookingId, Long driverId);

    @Query("SELECT b.cargoRequest.id FROM Booking b " +
            "WHERE b.id = :bookingId AND b.driver.id = :driverId")
    Optional<Long> findFreightIdByIdAndDriverId(
            @Param("bookingId") Long bookingId, @Param("driverId") Long driverId);

    /** Admin: all bookings with a specific status. */
    List<Booking> findByBookingStatus(Booking.BookingStatus status);

    /** Check if the freight already has an active booking (prevents double-booking). */
    boolean existsByCargoRequest_IdAndBookingStatusIn(
            Long freightId, java.util.Collection<Booking.BookingStatus> statuses);

    /** Shipper's bookings — bookings for freight they own. */
    @Query("SELECT b FROM Booking b WHERE b.cargoRequest.shipper.id = :shipperId")
    List<Booking> findByShipperId(@Param("shipperId") Long shipperId);

    boolean existsByCargoRequest_Shipper_Id(Long shipperId);

    @Query("SELECT b.id AS id FROM Booking b WHERE b.cargoRequest.shipper.id = :shipperId")
    Slice<BookingIdProjection> findPageIdsByShipperId(
            @Param("shipperId") Long shipperId, Pageable pageable);

    Slice<BookingIdProjection> findPageIdsByDriver_Id(Long driverId, Pageable pageable);

    @Query("SELECT b.id AS id FROM Booking b")
    Slice<BookingIdProjection> findAllPageIds(Pageable pageable);

    @Query("SELECT COUNT(b) FROM Booking b WHERE b.driver.id = :driverId")
    long countByDriver_IdForAdmin(@Param("driverId") Long driverId);

    @Query("SELECT COUNT(b) AS totalBookings, " +
           "COALESCE(SUM(CASE WHEN b.bookingStatus = :confirmedStatus THEN 1 ELSE 0 END), 0) AS confirmedBookings, " +
           "COALESCE(SUM(CASE WHEN b.bookingStatus = :completedStatus THEN 1 ELSE 0 END), 0) AS completedBookings, " +
           "COALESCE(SUM(CASE WHEN b.bookingStatus = :pendingStatus THEN 1 ELSE 0 END), 0) AS pendingBookings " +
           "FROM Booking b WHERE b.driver.id = :driverId")
    DriverDashboardCountsProjection findDriverDashboardCounts(
            @Param("driverId") Long driverId,
            @Param("confirmedStatus") Booking.BookingStatus confirmedStatus,
            @Param("completedStatus") Booking.BookingStatus completedStatus,
            @Param("pendingStatus") Booking.BookingStatus pendingStatus);

    @EntityGraph(attributePaths = {"cargoRequest", "vehicle", "driver", "bid"})
    List<Booking> findTop3ByDriver_IdAndBookingStatusOrderByCreatedAtDescIdDesc(
            Long driverId, Booking.BookingStatus bookingStatus);

    interface DriverDashboardCountsProjection {
        Long getTotalBookings();
        Long getConfirmedBookings();
        Long getCompletedBookings();
        Long getPendingBookings();
    }

    @Query("SELECT b.id AS id FROM Booking b WHERE b.driver.id = :driverId")
    Slice<BookingIdProjection> findPageIdsByDriverIdForAdmin(
            @Param("driverId") Long driverId, Pageable pageable);

    @EntityGraph(attributePaths = {"cargoRequest", "vehicle", "driver", "bid"})
    List<Booking> findByIdIn(Collection<Long> ids);

    interface BookingIdProjection {
        Long getId();
    }

    @Query("SELECT COUNT(b) FROM Booking b " +
           "WHERE b.cargoRequest.shipper.id = :shipperId " +
           "AND b.bookingStatus NOT IN :excludedStatuses")
    long countActiveByShipperId(
            @Param("shipperId") Long shipperId,
            @Param("excludedStatuses") java.util.Collection<Booking.BookingStatus> excludedStatuses);

    @Query("SELECT COUNT(b) FROM Booking b WHERE b.cargoRequest.shipper.id = :shipperId")
    long countByShipperId(@Param("shipperId") Long shipperId);

    @Query("SELECT COUNT(b) FROM Booking b " +
           "WHERE b.cargoRequest.shipper.id = :shipperId " +
           "AND b.bookingStatus = :status")
    long countByShipperIdAndStatus(
            @Param("shipperId") Long shipperId,
            @Param("status") Booking.BookingStatus status);

    @Query("SELECT b.id AS id, b.bookingStatus AS bookingStatus " +
           "FROM Booking b WHERE b.cargoRequest.shipper.id = :shipperId " +
           "ORDER BY b.createdAt DESC, b.id DESC")
    List<RecentBookingProjection> findRecentDashboardBookings(
            @Param("shipperId") Long shipperId, Pageable pageable);

    interface RecentBookingProjection {
        Long getId();
        Booking.BookingStatus getBookingStatus();
    }

    /**
     * Fleet dashboard count using the existing active-booking predicate.
     */
    @Query("SELECT COUNT(b) FROM Booking b " +
           "WHERE b.driver.id IN :driverIds " +
           "AND b.bookingStatus NOT IN :excludedStatuses")
    long countActiveByDriverIdIn(
            @Param("driverIds") java.util.Collection<Long> driverIds,
            @Param("excludedStatuses") java.util.Collection<Booking.BookingStatus> excludedStatuses);

    /**
     * Bounded fleet dashboard projection. Slice pagination fetches one lookahead row
     * for hasNext without materializing Booking entities or issuing a page count.
     */
    @Query("SELECT b.id AS bookingId, d.id AS driverId, d.fullName AS driverName, " +
           "b.bookingStatus AS status, c.pickupLocation AS pickupLocation, " +
           "c.destination AS destination " +
           "FROM Booking b JOIN b.driver d JOIN b.cargoRequest c " +
           "WHERE b.driver.id IN :driverIds " +
           "AND b.bookingStatus NOT IN :excludedStatuses " +
           "ORDER BY b.createdAt DESC, b.id DESC")
    Slice<FleetActiveBookingProjection> findActiveDashboardBookings(
            @Param("driverIds") java.util.Collection<Long> driverIds,
            @Param("excludedStatuses") java.util.Collection<Booking.BookingStatus> excludedStatuses,
            Pageable pageable);

    interface FleetActiveBookingProjection {
        Long getBookingId();
        Long getDriverId();
        String getDriverName();
        Booking.BookingStatus getStatus();
        String getPickupLocation();
        String getDestination();
    }

    // ── Driver listing statistics (B1) ────────────────────────────────────────

    /**
     * Count of bookings per driver for a set of driver IDs, filtered by status.
     * <p>Returns a list of [driverId, count] pairs (Object[]).
     * Used by driver listing to compute totalDeliveries without N+1 queries.
     *
     * @param driverIds Set of driver user IDs to aggregate
     * @param status    Booking status to filter on (e.g. COMPLETED)
     * @return List of Object[]{Long driverId, Long count}
     */
    @Query("SELECT b.driver.id, COUNT(b) FROM Booking b " +
           "WHERE b.driver.id IN :driverIds AND b.bookingStatus = :status " +
           "GROUP BY b.driver.id")
    List<Object[]> countByDriverIdInAndStatus(
            @Param("driverIds") java.util.Collection<Long> driverIds,
            @Param("status") Booking.BookingStatus status);

    /**
     * Count of all bookings per driver for a set of driver IDs.
     * <p>Returns a list of [driverId, count] pairs (Object[]).
     * Used to compute successRate without N+1 queries.
     *
     * @param driverIds Set of driver user IDs to aggregate
     * @return List of Object[]{Long driverId, Long count}
     */
    @Query("SELECT b.driver.id, COUNT(b) FROM Booking b " +
           "WHERE b.driver.id IN :driverIds " +
           "GROUP BY b.driver.id")
    List<Object[]> countByDriverIdIn(
            @Param("driverIds") java.util.Collection<Long> driverIds);

    /**
     * Admin: a bounded page of bookings without any payment row, excluding cancelled bookings.
     * Fetches the singular response associations and counts with the same NOT EXISTS predicate.
     */
    @Query(value = "SELECT b FROM Booking b " +
                   "LEFT JOIN FETCH b.cargoRequest c " +
                   "LEFT JOIN FETCH c.shipper s " +
                   "LEFT JOIN FETCH b.driver d " +
                   "WHERE NOT EXISTS (SELECT 1 FROM Payment p WHERE p.booking = b) " +
                   "AND b.bookingStatus != :excludedStatus " +
                   "ORDER BY b.createdAt DESC, b.id DESC",
           countQuery = "SELECT COUNT(b) FROM Booking b " +
                        "WHERE NOT EXISTS (SELECT 1 FROM Payment p WHERE p.booking = b) " +
                        "AND b.bookingStatus != :excludedStatus")
    Page<Booking> findUnpaidBookings(
            @Param("excludedStatus") Booking.BookingStatus excludedStatus, Pageable pageable);

    /** Aggregate booking commissions by creation month for completed revenue analytics. */
    @Query("SELECT EXTRACT(MONTH FROM b.createdAt) AS monthNumber, " +
           "COALESCE(SUM(b.commissionFee), 0) AS commissions " +
           "FROM Booking b " +
           "WHERE b.bookingStatus IN :statuses " +
           "AND b.createdAt >= :yearStart AND b.createdAt < :nextYearStart " +
           "GROUP BY EXTRACT(MONTH FROM b.createdAt)")
    List<MonthlyBookingCommissionAggregate> aggregateMonthlyBookingCommissions(
            @Param("statuses") Collection<Booking.BookingStatus> statuses,
            @Param("yearStart") LocalDateTime yearStart,
            @Param("nextYearStart") LocalDateTime nextYearStart);

    interface MonthlyBookingCommissionAggregate {
        Integer getMonthNumber();
        BigDecimal getCommissions();
    }

    /**
     * Find booking by ID with all necessary associations for DTO mapping.
     */
    @Query("SELECT b FROM Booking b " +
           "LEFT JOIN FETCH b.cargoRequest c " +
           "LEFT JOIN FETCH c.shipper " +
           "LEFT JOIN FETCH b.driver " +
           "LEFT JOIN FETCH b.vehicle " +
           "LEFT JOIN FETCH b.bid " +
           "WHERE b.id = :id")
    Optional<Booking> findByIdWithAssociations(@Param("id") Long id);

    // ── Phase 6: carrier dispatch queries ─────────────────────────────────────

    /**
     * Phase 6 — Find a booking by its originating dispatch assignment (idempotency guard).
     */
    Optional<Booking> findByDispatchAssignmentId(Long dispatchAssignmentId);

    /**
     * Phase 6 — All bookings for a carrier company (dispatch portal).
     */
    @Query("SELECT b FROM Booking b WHERE b.carrierCompanyId = :carrierCompanyId " +
           "ORDER BY b.createdAt DESC")
    List<Booking> findByCarrierCompanyId(@Param("carrierCompanyId") Long carrierCompanyId);
}
