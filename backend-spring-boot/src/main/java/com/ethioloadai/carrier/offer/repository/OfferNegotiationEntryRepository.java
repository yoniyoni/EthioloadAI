package com.ethioloadai.carrier.offer.repository;

import com.ethioloadai.carrier.offer.entity.OfferNegotiationEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * Phase 5 — Data access for immutable {@link OfferNegotiationEntry} history rows.
 *
 * <p>No update or delete operations are intentionally exposed — the history
 * is append-only by design.
 */
public interface OfferNegotiationEntryRepository extends JpaRepository<OfferNegotiationEntry, Long> {

    /** Full chronological history for a given offer. */
    @Query("SELECT e FROM OfferNegotiationEntry e WHERE e.carrierOffer.id = :offerId ORDER BY e.createdAt ASC")
    List<OfferNegotiationEntry> findByCarrierOfferIdOrderByCreatedAtAsc(@Param("offerId") Long offerId);

    /** Most recent entry for an offer (used to determine whose turn it is). */
    @Query("SELECT e FROM OfferNegotiationEntry e WHERE e.carrierOffer.id = :offerId " +
           "ORDER BY e.createdAt DESC LIMIT 1")
    java.util.Optional<OfferNegotiationEntry> findLatestByCarrierOfferId(@Param("offerId") Long offerId);

    /** Count of entries for an offer — used for audit checks in tests. */
    long countByCarrierOffer_Id(Long offerId);
}
