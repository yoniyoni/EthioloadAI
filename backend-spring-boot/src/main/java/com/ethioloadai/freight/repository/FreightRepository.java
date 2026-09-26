package com.ethioloadai.freight.repository;

import com.ethioloadai.freight.entity.Freight;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface FreightRepository extends JpaRepository<Freight, Long>, JpaSpecificationExecutor<Freight> {

    // Freight.shipper is a @ManyToOne association — must traverse via shipper.id using underscore.
    // findByShipperId would fail because there is no scalar 'shipperId' field on Freight.
    List<Freight> findByShipper_Id(Long shipperId);

    List<Freight> findByShipper_IdOrderByCreatedAtDescIdDesc(Long shipperId);

    List<Freight> findByStatus(Freight.FreightStatus status);

    /**
     * Phase 7 — Direct native SQL update for freight status.
     * Uses lowercase string values to match the PostgreSQL CHECK constraint.
     * flushAutomatically=true ensures pending changes are flushed before the update.
     * clearAutomatically=true evicts all entities from L1 cache so callers see the update.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "UPDATE freight SET status = :newStatus WHERE id = :freightId AND status = :expectedStatus",
           nativeQuery = true)
    int updateStatusNative(@Param("freightId") Long freightId,
                           @Param("expectedStatus") String expectedStatus,
                           @Param("newStatus") String newStatus);

    long countByStatusIn(java.util.Collection<Freight.FreightStatus> statuses);

    @Query("SELECT f.pickupLocation AS pickup, f.destination AS delivery, COUNT(f) AS count " +
           "FROM Freight f " +
           "WHERE f.pickupLocation IS NOT NULL AND f.destination IS NOT NULL " +
           "GROUP BY f.pickupLocation, f.destination " +
           "ORDER BY COUNT(f) DESC")
    List<com.ethioloadai.analytics.dto.RouteCountProjection> findTopRoutes(Pageable pageable);

    @Query("SELECT f.materialType AS cargoType, COUNT(f) AS count " +
           "FROM Freight f " +
           "WHERE f.materialType IS NOT NULL " +
           "GROUP BY f.materialType " +
           "ORDER BY COUNT(f) DESC")
    List<com.ethioloadai.analytics.dto.CargoTypeCountProjection> findCargoTypeDistribution();

    List<Freight> findByStatusAndMaterialType(Freight.FreightStatus status, String materialType);

    // matchedDriver is also a @ManyToOne association — use underscore traversal.
    List<Freight> findByMatchedDriver_Id(Long driverId);

    Optional<Freight> findByIdAndShipper_Id(Long id, Long shipperId);

    boolean existsByShipper_IdAndId(Long shipperId, Long id);

    // Paginated variant for the search endpoint — avoids loading the full table.
    Page<Freight> findAll(org.springframework.data.jpa.domain.Specification<Freight> spec, Pageable pageable);

    // ── V6 query methods ───────────────────────────────────────────────────

    /** Find intracity freight by city (for city-based browsing) */
    List<Freight> findByServiceTypeAndCityIgnoreCaseAndStatus(
            Freight.ServiceType serviceType, String city, Freight.FreightStatus status);

    /** Find freight by service type and status */
    List<Freight> findByServiceTypeAndStatus(Freight.ServiceType serviceType, Freight.FreightStatus status);

    /** Find negotiable freight with open bids (no deadline or future deadline) */
    @Query("SELECT f FROM Freight f WHERE f.priceType = :priceType AND f.status = :status " +
           "AND (f.bidDeadline IS NULL OR f.bidDeadline > :now)")
    List<Freight> findNegotiableFreightWithOpenBids(@Param("priceType") Freight.PriceType priceType,
                                                   @Param("status") Freight.FreightStatus status,
                                                   @Param("now") LocalDateTime now);

    /** Find fixed-price freight ready for direct booking */
    List<Freight> findByPriceTypeAndStatusAndBudgetIsNotNull(
            Freight.PriceType priceType, Freight.FreightStatus status);

    /** Count freight by city (for city-based statistics) */
    @Query("SELECT COUNT(f) FROM Freight f WHERE f.serviceType = :serviceType AND " +
           "LOWER(f.city) = LOWER(:city) AND f.status = :status")
    Long countByServiceTypeAndCityAndStatus(@Param("serviceType") Freight.ServiceType serviceType,
                                          @Param("city") String city,
                                          @Param("status") Freight.FreightStatus status);

    // ── Phase 5: carrier award ─────────────────────────────────────────────

    /**
     * Atomically record the winning CarrierOffer on the freight row.
     * Uses a bulk JPQL update so the change bypasses the JPA first-level
     * cache and is immediately visible to subsequent JDBC/JPA reads within
     * the same transaction.
     *
     * @param freightId       the freight that has been commercially awarded
     * @param carrierOfferId  the winning CarrierOffer id
     * @return number of rows updated (always 1 on success)
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Freight f SET f.carrierAwardOfferId = :carrierOfferId " +
           "WHERE f.id = :freightId AND f.carrierAwardOfferId IS NULL")
    int recordCarrierAward(@Param("freightId") Long freightId,
                           @Param("carrierOfferId") Long carrierOfferId);
}
