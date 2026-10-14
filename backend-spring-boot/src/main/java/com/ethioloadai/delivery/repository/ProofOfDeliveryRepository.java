package com.ethioloadai.delivery.repository;

import com.ethioloadai.delivery.entity.ProofOfDelivery;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProofOfDeliveryRepository extends JpaRepository<ProofOfDelivery, Long> {

    /** One proof per trip (unique DB constraint mirrors this). */
    Optional<ProofOfDelivery> findByTrip_Id(Long tripId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM ProofOfDelivery p WHERE p.trip.id = :tripId")
    Optional<ProofOfDelivery> findByTrip_IdForUpdate(@Param("tripId") Long tripId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM ProofOfDelivery p WHERE p.id = :proofId")
    Optional<ProofOfDelivery> findByIdForUpdate(@Param("proofId") Long proofId);

    Optional<ProofOfDelivery> findByBooking_Id(Long bookingId);

    /** Authorization check: is this proof for a freight owned by the given shipper? */
    @Query("SELECT p FROM ProofOfDelivery p " +
           "WHERE p.id = :proofId AND p.freight.shipper.id = :shipperId")
    Optional<ProofOfDelivery> findByIdAndShipperId(
            @Param("proofId") Long proofId,
            @Param("shipperId") Long shipperId);

    /** Authorization check: is this proof uploaded by the given driver? */
    @Query("SELECT p FROM ProofOfDelivery p " +
           "WHERE p.id = :proofId AND p.uploadedBy.id = :driverId")
    Optional<ProofOfDelivery> findByIdAndDriverId(
            @Param("proofId") Long proofId,
            @Param("driverId") Long driverId);

    /** All proofs for a carrier company — for carrier dashboard. */
    @Query("SELECT p FROM ProofOfDelivery p " +
           "WHERE p.booking.carrierCompanyId = :carrierCompanyId " +
           "ORDER BY p.createdAt DESC")
    List<ProofOfDelivery> findByCarrierCompanyId(@Param("carrierCompanyId") Long carrierCompanyId);

    /** All proofs for a shipper — for shipper history. */
    @Query("SELECT p FROM ProofOfDelivery p " +
           "WHERE p.freight.shipper.id = :shipperId " +
           "ORDER BY p.createdAt DESC")
    List<ProofOfDelivery> findByShipperId(@Param("shipperId") Long shipperId);

    boolean existsByTrip_Id(Long tripId);
}
