package com.ethioloadai.trip.repository;

import com.ethioloadai.trip.entity.TripStop;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TripStopRepository extends JpaRepository<TripStop, Long> {

    /** All stops for a trip, in stop_order. */
    List<TripStop> findByTrip_IdOrderByStopOrderAsc(Long tripId);

    /** Find a stop and verify it belongs to the given trip (IDOR protection). */
    Optional<TripStop> findByIdAndTrip_Id(Long stopId, Long tripId);

    /** Sum of agreed prices for a trip (for total_amount calculation). */
    @Query("SELECT COALESCE(SUM(s.agreedPrice), 0) FROM TripStop s WHERE s.trip.id = :tripId")
    java.math.BigDecimal sumAgreedPriceByTripId(@Param("tripId") Long tripId);

    /** Next available stop_order for a trip. */
    @Query("SELECT COALESCE(MAX(s.stopOrder), 0) + 1 FROM TripStop s WHERE s.trip.id = :tripId")
    Integer nextStopOrder(@Param("tripId") Long tripId);

    /** Find stop by trip and stop order. */
    Optional<TripStop> findByTrip_IdAndStopOrder(Long tripId, Integer stopOrder);

    /** Find stops with order less than specified. */
    List<TripStop> findByTrip_IdAndStopOrderLessThan(Long tripId, Integer stopOrder);

    /** Find stops with order greater than specified. */
    List<TripStop> findByTrip_IdAndStopOrderGreaterThan(Long tripId, Integer stopOrder);
}
