package com.ethioloadai.carrier.fleet.repository;

import com.ethioloadai.carrier.fleet.entity.AssignmentStatus;
import com.ethioloadai.carrier.fleet.entity.VehicleDriverAssignment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;

public interface VehicleDriverAssignmentRepository extends JpaRepository<VehicleDriverAssignment, Long> {

    /** Company-scoped lookup — IDOR protection. */
    Optional<VehicleDriverAssignment> findByIdAndCarrierCompanyId(Long id, Long carrierCompanyId);

    Optional<VehicleDriverAssignment> findByCarrierCompanyIdAndVehicleIdAndStatus(
            Long carrierCompanyId, Long vehicleId, AssignmentStatus status);

    /** Current ACTIVE assignment for a driver. Used during assignment creation / conflict check. */
    Optional<VehicleDriverAssignment> findByCarrierDriverIdAndStatus(
            Long carrierDriverId, AssignmentStatus status);

    /** Current ACTIVE assignment for a vehicle. */
    Optional<VehicleDriverAssignment> findByVehicleIdAndStatus(Long vehicleId, AssignmentStatus status);

    /**
     * Pessimistic-write lock version of the active-driver lookup.
     * Called inside the assign transaction to prevent two concurrent assignments
     * for the same driver (serialises at DB level).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM VehicleDriverAssignment a " +
           "WHERE a.carrierDriverId = :driverId AND a.status = 'ACTIVE'")
    Optional<VehicleDriverAssignment> lockByCarrierDriverIdActive(
            @Param("driverId") Long driverId);

    /** Full assignment history for a company, newest first. */
    Page<VehicleDriverAssignment> findByCarrierCompanyIdOrderByAssignedAtDesc(
            Long carrierCompanyId, Pageable pageable);

    /** Assignment history for a specific driver. */
    List<VehicleDriverAssignment> findByCarrierDriverIdOrderByAssignedAtDesc(
            Long carrierDriverId);

    /** Assignment history for a specific vehicle. */
    List<VehicleDriverAssignment> findByVehicleIdOrderByAssignedAtDesc(Long vehicleId);

    /** All ACTIVE assignments in a company (for fleet dashboard). */
    List<VehicleDriverAssignment> findByCarrierCompanyIdAndStatus(
            Long carrierCompanyId, AssignmentStatus status);
}
