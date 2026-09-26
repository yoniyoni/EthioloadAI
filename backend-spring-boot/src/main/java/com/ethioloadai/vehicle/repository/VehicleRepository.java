package com.ethioloadai.vehicle.repository;

import com.ethioloadai.vehicle.entity.Vehicle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface VehicleRepository extends JpaRepository<Vehicle, Long> {

    Optional<Vehicle> findByPlateNumber(String plateNumber);

    boolean existsByPlateNumber(String plateNumber);

    boolean existsByPlateNumberAndIdNot(String plateNumber, Long id);

    List<Vehicle> findByUserId(Long userId);

    List<Vehicle> findByFleetOwnerId(Long fleetOwnerId);

    /** Batch lookup for vehicles belonging to any of the specified fleet owner IDs. */
    List<Vehicle> findByFleetOwnerIdIn(java.util.Collection<Long> fleetOwnerIds);

    // ── Phase 4 company-scoped queries ────────────────────────────────────────

    /** All vehicles belonging to a carrier company. */
    org.springframework.data.domain.Page<Vehicle> findByCarrierCompanyId(
            Long carrierCompanyId, org.springframework.data.domain.Pageable pageable);

    /** Scoped single-vehicle lookup — prevents IDOR. */
    Optional<Vehicle> findByIdAndCarrierCompanyId(Long id, Long carrierCompanyId);

    /** Check ownership without fetching the entity. */
    boolean existsByIdAndCarrierCompanyId(Long id, Long carrierCompanyId);

    List<Vehicle> findByCurrentCity(String currentCity);

    List<Vehicle> findByAvailabilityStatus(String availabilityStatus);

    /** Count vehicles by availability status. */
    long countByAvailabilityStatus(String availabilityStatus);

    @Query("SELECT v FROM Vehicle v WHERE " +
           "(:latitude IS NULL OR :longitude IS NULL OR " +
           "(6371 * acos(cos(radians(:latitude)) * cos(radians(v.latitude)) * " +
           "cos(radians(v.longitude) - radians(:longitude)) + " +
           "sin(radians(:latitude)) * sin(radians(v.latitude)))) < :radius) " +
           "AND v.availabilityStatus = 'available'")
    List<Vehicle> findNearbyAvailableVehicles(
            @Param("latitude") BigDecimal latitude,
            @Param("longitude") BigDecimal longitude,
            @Param("radius") Double radius
    );

    @Query("SELECT v FROM Vehicle v WHERE " +
           "v.currentCity = :city AND v.availabilityStatus = 'available'")
    List<Vehicle> findAvailableVehiclesInCity(@Param("city") String city);
}
