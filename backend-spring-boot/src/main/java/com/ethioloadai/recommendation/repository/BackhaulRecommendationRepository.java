package com.ethioloadai.recommendation.repository;

import com.ethioloadai.recommendation.entity.BackhaulRecommendation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BackhaulRecommendationRepository extends JpaRepository<BackhaulRecommendation, Long> {

    /**
     * All non-dismissed recommendations for a driver's trip,
     * sorted by score descending (best matches first).
     */
    @Query("SELECT r FROM BackhaulRecommendation r " +
           "WHERE r.trip.id = :tripId AND r.driver.id = :driverId " +
           "AND r.status != 'DISMISSED' " +
           "ORDER BY r.score DESC")
    List<BackhaulRecommendation> findActiveForTrip(@Param("tripId") Long tripId,
                                                    @Param("driverId") Long driverId);

    /** Find a recommendation and verify it belongs to the given driver (IDOR). */
    Optional<BackhaulRecommendation> findByIdAndDriver_Id(Long id, Long driverId);

    /** Used by the background job to avoid re-inserting duplicates. */
    boolean existsByTrip_IdAndDriver_IdAndCargoRequest_Id(Long tripId, Long driverId, Long cargoRequestId);
}
