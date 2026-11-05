package com.ethioloadai.carrier.dispatch.repository;

import com.ethioloadai.booking.entity.Booking;
import com.ethioloadai.carrier.dispatch.entity.DispatchAssignment;
import com.ethioloadai.carrier.dispatch.entity.DispatchStatus;
import com.ethioloadai.freight.entity.Freight;
import com.ethioloadai.trip.entity.Trip;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface DispatchAssignmentRepository extends JpaRepository<DispatchAssignment, Long> {

    // ── Company-scoped lookups (tenant isolation) ─────────────────────────────

    /** Find by ID, scoped to a specific carrier company. */
    Optional<DispatchAssignment> findByIdAndCarrierCompanyId(Long id, Long carrierCompanyId);

    /** All assignments for a carrier company, newest first. */
    Page<DispatchAssignment> findByCarrierCompanyIdOrderByAssignedAtDesc(
            Long carrierCompanyId, Pageable pageable);

    @Query("SELECT d FROM DispatchAssignment d WHERE d.carrierCompanyId = :companyId " +
            "AND NOT EXISTS (SELECT invalidTrip.id FROM Trip invalidTrip " +
            "WHERE invalidTrip.booking.cargoRequest.id = d.freightId " +
            "AND invalidTrip.tripStatus = :completedTripStatus AND invalidTrip.endTime IS NULL) " +
            "AND NOT EXISTS (SELECT invalidBooking.id FROM Booking invalidBooking " +
            "WHERE invalidBooking.cargoRequest.id = d.freightId " +
            "AND invalidBooking.bookingStatus IN :terminalBookingStatuses " +
            "AND invalidBooking.updatedAt IS NULL) AND (" +
            "(EXISTS (SELECT f.id FROM Freight f WHERE f.id = d.freightId " +
            "AND f.status NOT IN :terminalFreightStatuses) " +
            "AND NOT EXISTS (SELECT t.id FROM Trip t WHERE t.booking.cargoRequest.id = d.freightId " +
            "AND t.tripStatus = :completedTripStatus) " +
            "AND NOT EXISTS (SELECT b.id FROM Booking b WHERE b.cargoRequest.id = d.freightId " +
            "AND b.bookingStatus IN :terminalBookingStatuses)) " +
            "OR EXISTS (SELECT f.id FROM Freight f WHERE f.id = d.freightId " +
            "AND f.status = :cancelledStatus AND f.updatedAt BETWEEN :cutoff AND :now) " +
            "OR (NOT EXISTS (SELECT f.id FROM Freight f WHERE f.id = d.freightId " +
            "AND f.status = :cancelledStatus) AND EXISTS (SELECT t.id FROM Trip t " +
            "WHERE t.booking.cargoRequest.id = d.freightId AND t.tripStatus = :completedTripStatus " +
            "AND t.endTime BETWEEN :cutoff AND :now)) " +
            "OR (NOT EXISTS (SELECT f.id FROM Freight f WHERE f.id = d.freightId " +
            "AND f.status = :cancelledStatus) AND NOT EXISTS (SELECT t.id FROM Trip t " +
            "WHERE t.booking.cargoRequest.id = d.freightId AND t.tripStatus = :completedTripStatus) " +
            "AND EXISTS (SELECT b.id FROM Booking b WHERE b.cargoRequest.id = d.freightId " +
            "AND b.bookingStatus IN :terminalBookingStatuses AND b.updatedAt BETWEEN :cutoff AND :now)) " +
            "OR (NOT EXISTS (SELECT f.id FROM Freight f WHERE f.id = d.freightId " +
            "AND f.status = :cancelledStatus) AND NOT EXISTS (SELECT t.id FROM Trip t " +
            "WHERE t.booking.cargoRequest.id = d.freightId AND t.tripStatus = :completedTripStatus) " +
            "AND NOT EXISTS (SELECT b.id FROM Booking b WHERE b.cargoRequest.id = d.freightId " +
            "AND b.bookingStatus IN :terminalBookingStatuses) " +
            "AND EXISTS (SELECT f.id FROM Freight f WHERE f.id = d.freightId " +
            "AND f.status = :completedStatus AND f.updatedAt BETWEEN :cutoff AND :now))) " +
            "ORDER BY d.assignedAt DESC")
    Page<DispatchAssignment> findVisibleByCarrierCompanyId(
            @Param("companyId") Long carrierCompanyId,
            @Param("terminalFreightStatuses") List<Freight.FreightStatus> terminalFreightStatuses,
            @Param("cancelledStatus") Freight.FreightStatus cancelledStatus,
            @Param("completedStatus") Freight.FreightStatus completedStatus,
            @Param("completedTripStatus") Trip.TripStatus completedTripStatus,
            @Param("terminalBookingStatuses") List<Booking.BookingStatus> terminalBookingStatuses,
            @Param("cutoff") LocalDateTime cutoff,
            @Param("now") LocalDateTime now,
            Pageable pageable);

    /** All assignments for a specific freight within a company. */
    List<DispatchAssignment> findByFreightIdAndCarrierCompanyIdOrderByAssignedAtDesc(
            Long freightId, Long carrierCompanyId);

    // ── Active dispatch detection ─────────────────────────────────────────────

    /**
     * Find the currently active dispatch for a freight (ASSIGNED or DRIVER_ACCEPTED).
     * Used to enforce the "one active dispatch per freight" invariant.
     */
    @Query("SELECT d FROM DispatchAssignment d WHERE d.freightId = :freightId " +
           "AND d.status IN ('ASSIGNED', 'DRIVER_ACCEPTED')")
    Optional<DispatchAssignment> findActiveByFreightId(@Param("freightId") Long freightId);

    boolean existsByFreightIdAndStatusIn(Long freightId, List<DispatchStatus> statuses);

    /**
     * Pessimistic-write lock variant for concurrent dispatch creation guard.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT d FROM DispatchAssignment d WHERE d.freightId = :freightId " +
           "AND d.status IN ('ASSIGNED', 'DRIVER_ACCEPTED')")
    Optional<DispatchAssignment> findActiveByFreightIdForUpdate(@Param("freightId") Long freightId);

    // ── Driver-facing queries ─────────────────────────────────────────────────

    /** All assignments targeting a specific driver (their inbox). */
    List<DispatchAssignment> findByDriverUserIdOrderByAssignedAtDesc(Long driverUserId);

    @Query("SELECT d FROM DispatchAssignment d WHERE d.driverUserId = :driverUserId " +
            "AND NOT EXISTS (SELECT invalidTrip.id FROM Trip invalidTrip " +
            "WHERE invalidTrip.booking.cargoRequest.id = d.freightId " +
            "AND invalidTrip.tripStatus = :completedTripStatus AND invalidTrip.endTime IS NULL) " +
            "AND NOT EXISTS (SELECT invalidBooking.id FROM Booking invalidBooking " +
            "WHERE invalidBooking.cargoRequest.id = d.freightId " +
            "AND invalidBooking.bookingStatus IN :terminalBookingStatuses " +
            "AND invalidBooking.updatedAt IS NULL) AND (" +
            "(d.status IN :activeAssignmentStatuses AND EXISTS (SELECT f.id FROM Freight f " +
            "WHERE f.id = d.freightId AND f.status NOT IN :terminalFreightStatuses) " +
            "AND NOT EXISTS (SELECT t.id FROM Trip t WHERE t.booking.cargoRequest.id = d.freightId " +
            "AND t.tripStatus = :completedTripStatus) " +
            "AND NOT EXISTS (SELECT b.id FROM Booking b WHERE b.cargoRequest.id = d.freightId " +
            "AND b.bookingStatus IN :terminalBookingStatuses)) " +
            "OR (d.status = :acceptedStatus AND EXISTS (SELECT f.id FROM Freight f " +
            "WHERE f.id = d.freightId AND f.status = :cancelledStatus " +
            "AND f.updatedAt BETWEEN :cutoff AND :now)) " +
            "OR (d.status = :acceptedStatus AND NOT EXISTS (SELECT f.id FROM Freight f " +
            "WHERE f.id = d.freightId AND f.status = :cancelledStatus) " +
            "AND EXISTS (SELECT t.id FROM Trip t WHERE t.booking.cargoRequest.id = d.freightId " +
            "AND t.tripStatus = :completedTripStatus AND t.endTime BETWEEN :cutoff AND :now)) " +
            "OR (d.status = :acceptedStatus AND NOT EXISTS (SELECT f.id FROM Freight f " +
            "WHERE f.id = d.freightId AND f.status = :cancelledStatus) " +
            "AND NOT EXISTS (SELECT t.id FROM Trip t WHERE t.booking.cargoRequest.id = d.freightId " +
            "AND t.tripStatus = :completedTripStatus) " +
            "AND EXISTS (SELECT b.id FROM Booking b WHERE b.cargoRequest.id = d.freightId " +
            "AND b.bookingStatus IN :terminalBookingStatuses AND b.updatedAt BETWEEN :cutoff AND :now)) " +
            "OR (d.status = :acceptedStatus AND NOT EXISTS (SELECT f.id FROM Freight f " +
            "WHERE f.id = d.freightId AND f.status = :cancelledStatus) " +
            "AND NOT EXISTS (SELECT t.id FROM Trip t WHERE t.booking.cargoRequest.id = d.freightId " +
            "AND t.tripStatus = :completedTripStatus) " +
            "AND NOT EXISTS (SELECT b.id FROM Booking b WHERE b.cargoRequest.id = d.freightId " +
            "AND b.bookingStatus IN :terminalBookingStatuses) " +
            "AND EXISTS (SELECT f.id FROM Freight f WHERE f.id = d.freightId " +
            "AND f.status = :completedStatus AND f.updatedAt BETWEEN :cutoff AND :now))) " +
            "ORDER BY d.assignedAt DESC")
    List<DispatchAssignment> findVisibleByDriverUserId(
            @Param("driverUserId") Long driverUserId,
            @Param("activeAssignmentStatuses") List<DispatchStatus> activeAssignmentStatuses,
            @Param("acceptedStatus") DispatchStatus acceptedStatus,
            @Param("terminalFreightStatuses") List<Freight.FreightStatus> terminalFreightStatuses,
            @Param("cancelledStatus") Freight.FreightStatus cancelledStatus,
            @Param("completedStatus") Freight.FreightStatus completedStatus,
            @Param("completedTripStatus") Trip.TripStatus completedTripStatus,
            @Param("terminalBookingStatuses") List<Booking.BookingStatus> terminalBookingStatuses,
            @Param("cutoff") LocalDateTime cutoff,
            @Param("now") LocalDateTime now);

    /** A specific assignment for a specific driver (IDOR protection). */
    Optional<DispatchAssignment> findByIdAndDriverUserId(Long id, Long driverUserId);

    @Query("SELECT d FROM DispatchAssignment d WHERE d.id = :dispatchId " +
            "AND d.driverUserId = :driverUserId " +
            "AND NOT EXISTS (SELECT invalidTrip.id FROM Trip invalidTrip " +
            "WHERE invalidTrip.booking.cargoRequest.id = d.freightId " +
            "AND invalidTrip.tripStatus = :completedTripStatus AND invalidTrip.endTime IS NULL) " +
            "AND NOT EXISTS (SELECT invalidBooking.id FROM Booking invalidBooking " +
            "WHERE invalidBooking.cargoRequest.id = d.freightId " +
            "AND invalidBooking.bookingStatus IN :terminalBookingStatuses " +
            "AND invalidBooking.updatedAt IS NULL) AND (" +
            "(d.status IN :activeAssignmentStatuses AND EXISTS (SELECT f.id FROM Freight f " +
            "WHERE f.id = d.freightId AND f.status NOT IN :terminalFreightStatuses) " +
            "AND NOT EXISTS (SELECT t.id FROM Trip t WHERE t.booking.cargoRequest.id = d.freightId " +
            "AND t.tripStatus = :completedTripStatus) " +
            "AND NOT EXISTS (SELECT b.id FROM Booking b WHERE b.cargoRequest.id = d.freightId " +
            "AND b.bookingStatus IN :terminalBookingStatuses)) " +
            "OR (d.status = :acceptedStatus AND EXISTS (SELECT f.id FROM Freight f " +
            "WHERE f.id = d.freightId AND f.status = :cancelledStatus " +
            "AND f.updatedAt BETWEEN :cutoff AND :now)) " +
            "OR (d.status = :acceptedStatus AND NOT EXISTS (SELECT f.id FROM Freight f " +
            "WHERE f.id = d.freightId AND f.status = :cancelledStatus) " +
            "AND EXISTS (SELECT t.id FROM Trip t WHERE t.booking.cargoRequest.id = d.freightId " +
            "AND t.tripStatus = :completedTripStatus AND t.endTime BETWEEN :cutoff AND :now)) " +
            "OR (d.status = :acceptedStatus AND NOT EXISTS (SELECT f.id FROM Freight f " +
            "WHERE f.id = d.freightId AND f.status = :cancelledStatus) " +
            "AND NOT EXISTS (SELECT t.id FROM Trip t WHERE t.booking.cargoRequest.id = d.freightId " +
            "AND t.tripStatus = :completedTripStatus) " +
            "AND EXISTS (SELECT b.id FROM Booking b WHERE b.cargoRequest.id = d.freightId " +
            "AND b.bookingStatus IN :terminalBookingStatuses AND b.updatedAt BETWEEN :cutoff AND :now)) " +
            "OR (d.status = :acceptedStatus AND NOT EXISTS (SELECT f.id FROM Freight f " +
            "WHERE f.id = d.freightId AND f.status = :cancelledStatus) " +
            "AND NOT EXISTS (SELECT t.id FROM Trip t WHERE t.booking.cargoRequest.id = d.freightId " +
            "AND t.tripStatus = :completedTripStatus) " +
            "AND NOT EXISTS (SELECT b.id FROM Booking b WHERE b.cargoRequest.id = d.freightId " +
            "AND b.bookingStatus IN :terminalBookingStatuses) " +
            "AND EXISTS (SELECT f.id FROM Freight f WHERE f.id = d.freightId " +
            "AND f.status = :completedStatus AND f.updatedAt BETWEEN :cutoff AND :now)))")
    Optional<DispatchAssignment> findVisibleByIdAndDriverUserId(
            @Param("dispatchId") Long dispatchId,
            @Param("driverUserId") Long driverUserId,
            @Param("activeAssignmentStatuses") List<DispatchStatus> activeAssignmentStatuses,
            @Param("acceptedStatus") DispatchStatus acceptedStatus,
            @Param("terminalFreightStatuses") List<Freight.FreightStatus> terminalFreightStatuses,
            @Param("cancelledStatus") Freight.FreightStatus cancelledStatus,
            @Param("completedStatus") Freight.FreightStatus completedStatus,
            @Param("completedTripStatus") Trip.TripStatus completedTripStatus,
            @Param("terminalBookingStatuses") List<Booking.BookingStatus> terminalBookingStatuses,
            @Param("cutoff") LocalDateTime cutoff,
            @Param("now") LocalDateTime now);

    /** A freight assignment scoped to its target driver. */
    Optional<DispatchAssignment> findByFreightIdAndDriverUserId(Long freightId, Long driverUserId);

    @Query("SELECT d.freightId FROM DispatchAssignment d WHERE d.id = :dispatchId")
    Optional<Long> findFreightIdById(@Param("dispatchId") Long dispatchId);

    /**
     * Pending assignment for a driver — used to prevent double-dispatch
     * while a driver still has an unanswered assignment.
     */
    @Query("SELECT d FROM DispatchAssignment d WHERE d.driverUserId = :driverUserId " +
           "AND d.status = 'ASSIGNED'")
    Optional<DispatchAssignment> findPendingByDriverUserId(@Param("driverUserId") Long driverUserId);

    /**
     * Pending assignment for a vehicle — used to prevent double-dispatch.
     */
    @Query("SELECT d FROM DispatchAssignment d WHERE d.vehicleId = :vehicleId " +
           "AND d.status = 'ASSIGNED'")
    Optional<DispatchAssignment> findPendingByVehicleId(@Param("vehicleId") Long vehicleId);

    // ── Idempotency guard ─────────────────────────────────────────────────────

    /**
     * Find existing DRIVER_ACCEPTED dispatch for a freight (for idempotent acceptance).
     */
    @Query("SELECT d FROM DispatchAssignment d WHERE d.freightId = :freightId " +
           "AND d.status = 'DRIVER_ACCEPTED'")
    Optional<DispatchAssignment> findAcceptedByFreightId(@Param("freightId") Long freightId);

    // ── Pessimistic-write lock for state transitions ──────────────────────────

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT d FROM DispatchAssignment d WHERE d.id = :id")
    Optional<DispatchAssignment> findByIdForUpdate(@Param("id") Long id);

    // ── Statistics / reporting ────────────────────────────────────────────────

    /** Count assignments by status for a company. */
    long countByCarrierCompanyIdAndStatus(Long carrierCompanyId, DispatchStatus status);
}
