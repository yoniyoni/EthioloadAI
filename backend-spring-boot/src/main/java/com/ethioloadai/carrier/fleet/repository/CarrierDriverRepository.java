package com.ethioloadai.carrier.fleet.repository;

import com.ethioloadai.carrier.fleet.entity.CarrierDriver;
import com.ethioloadai.carrier.fleet.entity.CarrierDriverStatus;
import com.ethioloadai.carrier.fleet.entity.DriverAvailability;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CarrierDriverRepository extends JpaRepository<CarrierDriver, Long> {

    /** Find a specific company-driver row (any status). */
    Optional<CarrierDriver> findByCarrierCompanyIdAndDriverUserId(
            Long carrierCompanyId, Long driverUserId);

    /** Find a specific row scoped to a company — IDOR protection. */
    Optional<CarrierDriver> findByIdAndCarrierCompanyId(Long id, Long carrierCompanyId);

    /** All drivers of a company with a given status, paged. */
    Page<CarrierDriver> findByCarrierCompanyIdAndStatus(
            Long carrierCompanyId, CarrierDriverStatus status, Pageable pageable);

    /** All active drivers (no pagination — used for assignment validation). */
    List<CarrierDriver> findByCarrierCompanyIdAndStatus(
            Long carrierCompanyId, CarrierDriverStatus status);

    /** Active drivers with a specific availability. */
    List<CarrierDriver> findByCarrierCompanyIdAndStatusAndAvailability(
            Long carrierCompanyId, CarrierDriverStatus status, DriverAvailability availability);

    /** Check company owns this driver row. */
    boolean existsByIdAndCarrierCompanyId(Long id, Long carrierCompanyId);

    /** Check a user is already an active driver in this company. */
    boolean existsByCarrierCompanyIdAndDriverUserIdAndStatus(
            Long carrierCompanyId, Long driverUserId, CarrierDriverStatus status);

    /** Count active drivers in a company. */
    long countByCarrierCompanyIdAndStatus(Long carrierCompanyId, CarrierDriverStatus status);

    /** Find all companies a user is an active driver in (multi-company edge case). */
    List<CarrierDriver> findByDriverUserIdAndStatus(Long driverUserId, CarrierDriverStatus status);

    /** Active driver rows that are AVAILABLE for the company — Phase 5 dispatch prep. */
    @Query("SELECT cd FROM CarrierDriver cd WHERE cd.carrierCompanyId = :companyId " +
           "AND cd.status = 'ACTIVE' AND cd.availability = 'AVAILABLE'")
    List<CarrierDriver> findAvailableDrivers(@Param("companyId") Long companyId);
}
