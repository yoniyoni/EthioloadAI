package com.ethioloadai.dispute.repository;

import com.ethioloadai.dispute.entity.Dispute;
import com.ethioloadai.dispute.entity.DisputeStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface DisputeRepository extends JpaRepository<Dispute, Long> {

    /** Find all disputes for a freight, newest first. */
    List<Dispute> findByFreight_IdOrderByCreatedAtDesc(Long freightId);

    /** Check if an active dispute exists for a freight. */
    boolean existsByFreight_IdAndStatusIn(Long freightId, Collection<DisputeStatus> statuses);

    /** Find all disputes ordered by creation date descending (for admin). */
    List<Dispute> findAllByOrderByCreatedAtDesc();

    /** Count disputes by status. */
    long countByStatus(DisputeStatus status);
}
