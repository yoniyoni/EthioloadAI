package com.ethioloadai.recommendation.repository;

import com.ethioloadai.recommendation.entity.AIRecommendation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AIRecommendationRepository extends JpaRepository<AIRecommendation, Long> {

    /**
     * Top AI recommendations for a freight item, sorted by score descending.
     * Used to surface the best vehicle matches on the freight detail page.
     */
    @Query("SELECT r FROM AIRecommendation r " +
           "WHERE r.cargoRequest.id = :freightId " +
           "ORDER BY r.recommendationScore DESC")
    List<AIRecommendation> findTopByCargoRequestId(@Param("freightId") Long freightId);

    /** Delete old recommendations before inserting fresh ones from the AI engine. */
    void deleteByCargoRequest_Id(Long freightId);
}
