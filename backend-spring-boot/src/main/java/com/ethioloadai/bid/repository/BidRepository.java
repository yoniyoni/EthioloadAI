package com.ethioloadai.bid.repository;

import com.ethioloadai.bid.entity.Bid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;

@Repository
public interface BidRepository extends JpaRepository<Bid, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM Bid b WHERE b.id = :bidId")
    Optional<Bid> findByIdForUpdate(@Param("bidId") Long bidId);

    @Query("SELECT b.cargoRequest.id FROM Bid b WHERE b.id = :bidId")
    Optional<Long> findFreightIdById(@Param("bidId") Long bidId);

    boolean existsByCargoRequest_IdAndStatusIn(
            Long freightId, Collection<Bid.BidStatus> statuses);

    /** All bids for a freight listing, with driver and vehicle eager-loaded. */
    @Query("SELECT b FROM Bid b JOIN FETCH b.driver JOIN FETCH b.vehicle WHERE b.cargoRequest.id = :freightId")
    List<Bid> findByCargoRequestIdWithDetails(@Param("freightId") Long freightId);

    /** Rank intracity bids in the database before applying the page window. */
    @Query(value = "SELECT b.id FROM bids b " +
            "WHERE b.cargo_request_id = :freightId " +
            "ORDER BY b.amount ASC, b.id ASC", nativeQuery = true)
    Slice<Long> findIntracityBidIdsByFreightId(@Param("freightId") Long freightId, Pageable pageable);

    /** Rank intercity bids using set-based driver averages before applying the page window. */
    @Query(value = "SELECT b.id FROM bids b " +
            "LEFT JOIN (" +
            "  SELECT r.driver_id, AVG(r.rating) AS average_rating " +
            "  FROM ratings r " +
            "  WHERE r.driver_id IN (" +
            "    SELECT DISTINCT ranked_bid.driver_id FROM bids ranked_bid " +
            "    WHERE ranked_bid.cargo_request_id = :freightId" +
            "  ) " +
            "  GROUP BY r.driver_id" +
            ") driver_rating ON driver_rating.driver_id = b.driver_id " +
            "WHERE b.cargo_request_id = :freightId " +
            "ORDER BY b.amount ASC, COALESCE(driver_rating.average_rating, 0) DESC, b.id ASC",
            nativeQuery = true)
    Slice<Long> findIntercityBidIdsByFreightId(@Param("freightId") Long freightId, Pageable pageable);

    /** Fetch only page-selected bids and their to-one response associations. */
    @Query("SELECT b FROM Bid b JOIN FETCH b.driver JOIN FETCH b.vehicle " +
            "JOIN FETCH b.cargoRequest c JOIN FETCH c.shipper WHERE b.id IN :bidIds")
    List<Bid> findAllWithDetailsByIdIn(@Param("bidIds") Collection<Long> bidIds);

    @Query("SELECT COUNT(b) FROM Bid b WHERE b.cargoRequest.id = :freightId " +
            "AND b.status IN :statuses")
    long countByFreightIdAndStatuses(
            @Param("freightId") Long freightId,
            @Param("statuses") Collection<Bid.BidStatus> statuses);

    /** All bids placed by a driver (for the driver's bid history view). */
    List<Bid> findByDriver_Id(Long driverId);

    /** All bids placed by a driver with pagination (for the driver's bid history view). */
    @EntityGraph(attributePaths = {"driver", "vehicle", "cargoRequest", "cargoRequest.shipper"})
    Page<Bid> findByDriver_Id(Long driverId, Pageable pageable);

    /** Active (pending or countered) bids for a freight — used in bid ranking. */
    @Query("SELECT b FROM Bid b WHERE b.cargoRequest.id = :freightId AND b.status IN ('PENDING', 'COUNTERED')")
    List<Bid> findActiveBidsByCargoRequestId(@Param("freightId") Long freightId);

    /** Check if a specific vehicle has already bid on a freight. */
    boolean existsByCargoRequest_IdAndVehicle_Id(Long freightId, Long vehicleId);

    /** Find a bid owned by a specific driver (IDOR protection). */
    Optional<Bid> findByIdAndDriver_Id(Long bidId, Long driverId);

    /**
     * Bulk-reject all competing bids when one bid is accepted.
     * Called inside a transaction when a bid is accepted.
     */
    @Modifying
        @Query("UPDATE Bid b SET b.status = 'rejected' " +
           "WHERE b.cargoRequest.id = :freightId " +
           "AND b.id != :acceptedBidId " +
            "AND b.status IN ('pending', 'countered')")
    int rejectCompetingBids(@Param("freightId") Long freightId,
                            @Param("acceptedBidId") Long acceptedBidId);

    /** Average ratings for multiple drivers — avoids per-comparison lookups when ranking bids. */
    @Query("SELECT r.driver.id AS driverId, AVG(r.rating) AS averageRating " +
           "FROM Rating r WHERE r.driver.id IN :driverIds GROUP BY r.driver.id")
    List<DriverRatingAverage> findAverageRatingsByDriverIds(@Param("driverIds") Collection<Long> driverIds);

    interface DriverRatingAverage {
        Long getDriverId();
        Double getAverageRating();
    }
}
