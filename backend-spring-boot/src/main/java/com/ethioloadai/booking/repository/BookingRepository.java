package com.ethioloadai.booking.repository;

import com.ethioloadai.booking.entity.Booking;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BookingRepository extends JpaRepository<Booking, Long> {

    /** Driver's own bookings. */
    List<Booking> findByDriver_Id(Long driverId);

    /** Bookings for a specific freight item. */
    List<Booking> findByCargoRequest_Id(Long freightId);

    /** Find a booking and verify it belongs to the given driver (IDOR protection). */
    Optional<Booking> findByIdAndDriver_Id(Long bookingId, Long driverId);

    /** Admin: all bookings with a specific status. */
    List<Booking> findByBookingStatus(Booking.BookingStatus status);

    /** Check if the freight already has an active booking (prevents double-booking). */
    boolean existsByCargoRequest_IdAndBookingStatusIn(
            Long freightId, java.util.Collection<Booking.BookingStatus> statuses);

    /** Shipper's bookings — bookings for freight they own. */
    @Query("SELECT b FROM Booking b WHERE b.cargoRequest.shipper.id = :shipperId")
    List<Booking> findByShipperId(@Param("shipperId") Long shipperId);

    /**
     * Fleet owner: active (non-completed/cancelled) bookings for a set of driver IDs.
     * Used by the fleet dashboard to summarise fleet activity.
     */
    @Query("SELECT b FROM Booking b " +
           "WHERE b.driver.id IN :driverIds " +
           "AND b.bookingStatus NOT IN :excludedStatuses")
    List<Booking> findActiveByDriverIdIn(
            @Param("driverIds") java.util.Collection<Long> driverIds,
            @Param("excludedStatuses") java.util.Collection<Booking.BookingStatus> excludedStatuses);

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
     * Admin: find bookings that have no payment record and are not cancelled.
     * Uses JOIN FETCH on cargoRequest, cargoRequest.shipper, and driver to prevent N+1 queries.
     * Orders newest first.
     */
    @Query("SELECT b FROM Booking b " +
           "LEFT JOIN FETCH b.cargoRequest c " +
           "LEFT JOIN FETCH c.shipper s " +
           "LEFT JOIN FETCH b.driver d " +
           "WHERE NOT EXISTS (SELECT 1 FROM Payment p WHERE p.booking = b) " +
           "AND b.bookingStatus != :excludedStatus " +
           "ORDER BY b.createdAt DESC")
    List<Booking> findUnpaidBookings(@Param("excludedStatus") Booking.BookingStatus excludedStatus);

    default List<Booking> findUnpaidBookings() {
        return findUnpaidBookings(Booking.BookingStatus.CANCELLED);
    }

    /** Bookings in a status set and date range (for monthly commissions analytics). */
    List<Booking> findByBookingStatusInAndCreatedAtBetween(
            java.util.Collection<Booking.BookingStatus> statuses,
            java.time.LocalDateTime start,
            java.time.LocalDateTime end);

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
