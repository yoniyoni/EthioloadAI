package com.ethioloadai.carrier.dispatch.repository;

import com.ethioloadai.carrier.dispatch.entity.DispatchAssignment;
import com.ethioloadai.carrier.dispatch.entity.DispatchStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

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

    /** A specific assignment for a specific driver (IDOR protection). */
    Optional<DispatchAssignment> findByIdAndDriverUserId(Long id, Long driverUserId);

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
