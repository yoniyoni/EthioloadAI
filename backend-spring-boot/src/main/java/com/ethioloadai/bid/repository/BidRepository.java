package com.ethioloadai.bid.repository;

import com.ethioloadai.bid.entity.Bid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BidRepository extends JpaRepository<Bid, Long> {

    /** All bids for a freight listing, with driver and vehicle eager-loaded. */
    @Query("SELECT b FROM Bid b JOIN FETCH b.driver JOIN FETCH b.vehicle WHERE b.cargoRequest.id = :freightId")
    List<Bid> findByCargoRequestIdWithDetails(@Param("freightId") Long freightId);

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

    /** Average rating for a driver — used in bid ranking tiebreaker. */
    @Query("SELECT AVG(r.rating) FROM Rating r WHERE r.driver.id = :driverId")
    Optional<Double> findAverageRatingByDriverId(@Param("driverId") Long driverId);
}
