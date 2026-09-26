package com.ethioloadai.rating.repository;

import com.ethioloadai.rating.entity.Rating;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RatingRepository extends JpaRepository<Rating, Long> {

    /** All ratings for a specific booking. */
    List<Rating> findByBooking_Id(Long bookingId);

    /** All ratings received by a driver (for their profile/history). */
    List<Rating> findByDriver_Id(Long driverId);

    /** Check if the rater already submitted a rating for this booking. */
    boolean existsByBooking_IdAndRater_Id(Long bookingId, Long raterId);

    /** Average rating for a driver — used in bid ranking. */
    @Query("SELECT AVG(r.rating) FROM Rating r WHERE r.driver.id = :driverId")
    Optional<Double> findAverageRatingForDriver(@Param("driverId") Long driverId);

    /** Rating count for a driver. */
    long countByDriver_Id(Long driverId);

    // ── Driver listing bulk query (B1) ────────────────────────────────────────

    /**
     * Average ratings for multiple drivers in a single query.
     * <p>Returns a list of [driverId, avgRating] pairs (Object[]).
     * Used by driver listing to avoid N+1 rating queries.
     *
     * @param driverIds Set of driver user IDs to aggregate
     * @return List of Object[]{Long driverId, Double avgRating}
     */
    @Query("SELECT r.driver.id, AVG(r.rating) FROM Rating r " +
           "WHERE r.driver.id IN :driverIds " +
           "GROUP BY r.driver.id")
    List<Object[]> findAverageRatingsForDriverIds(
            @Param("driverIds") java.util.Collection<Long> driverIds);
}
