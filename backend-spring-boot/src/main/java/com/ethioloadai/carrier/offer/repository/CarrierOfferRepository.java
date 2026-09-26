package com.ethioloadai.carrier.offer.repository;

import com.ethioloadai.carrier.offer.entity.CarrierOffer;
import com.ethioloadai.carrier.offer.entity.CarrierOffer.OfferStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Phase 5 — Data access for {@link CarrierOffer}.
 *
 * <p>Key query invariants:
 * <ul>
 *   <li>All freight-scoped carrier queries include the freight FK to prevent
 *       cross-freight data leaks.</li>
 *   <li>The pessimistic-write lock variant is used in the acceptance transaction
 *       to prevent concurrent double-acceptance.</li>
 * </ul>
 */
public interface CarrierOfferRepository extends JpaRepository<CarrierOffer, Long> {

    // ── Freight-scoped queries ─────────────────────────────────────────────────

    /** All offers for a given freight (shipper view, admin view). */
    @Query("SELECT o FROM CarrierOffer o " +
           "LEFT JOIN FETCH o.carrierCompany " +
           "LEFT JOIN FETCH o.submittedBy " +
           "WHERE o.freight.id = :freightId " +
           "ORDER BY o.createdAt DESC")
    List<CarrierOffer> findByFreightId(@Param("freightId") Long freightId);

    /** Paginated offers for a freight. */
    @Query(value = "SELECT o FROM CarrierOffer o WHERE o.freight.id = :freightId ORDER BY o.createdAt DESC",
           countQuery = "SELECT COUNT(o) FROM CarrierOffer o WHERE o.freight.id = :freightId")
    Page<CarrierOffer> findByFreightIdPaged(@Param("freightId") Long freightId, Pageable pageable);

    /** Active (non-terminal) offers for a freight. Used to supersede on acceptance. */
    @Query("SELECT o FROM CarrierOffer o WHERE o.freight.id = :freightId " +
           "AND o.status IN ('PENDING','NEGOTIATING')")
    List<CarrierOffer> findActiveByFreightId(@Param("freightId") Long freightId);

    /** Find the one accepted offer for a freight, if any. */
    @Query("SELECT o FROM CarrierOffer o WHERE o.freight.id = :freightId AND o.status = 'ACCEPTED'")
    Optional<CarrierOffer> findAcceptedByFreightId(@Param("freightId") Long freightId);

    // ── Company-scoped queries ─────────────────────────────────────────────────

    /** All offers submitted by a company (company portal view). */
    @Query(value = "SELECT o FROM CarrierOffer o " +
                   "LEFT JOIN FETCH o.freight " +
                   "WHERE o.carrierCompany.id = :companyId ORDER BY o.createdAt DESC",
           countQuery = "SELECT COUNT(o) FROM CarrierOffer o WHERE o.carrierCompany.id = :companyId")
    Page<CarrierOffer> findByCarrierCompanyId(@Param("companyId") Long companyId, Pageable pageable);

    // ── Single offer lookup with company validation (IDOR guard) ─────────────

    /** Find offer by ID only if it belongs to the given company. */
    @Query("SELECT o FROM CarrierOffer o WHERE o.id = :offerId AND o.carrierCompany.id = :companyId")
    Optional<CarrierOffer> findByIdAndCarrierCompanyId(
            @Param("offerId") Long offerId,
            @Param("companyId") Long companyId);

    /** Find offer by ID only if it belongs to the given freight. */
    @Query("SELECT o FROM CarrierOffer o WHERE o.id = :offerId AND o.freight.id = :freightId")
    Optional<CarrierOffer> findByIdAndFreightId(
            @Param("offerId") Long offerId,
            @Param("freightId") Long freightId);

    // ── Existence checks ───────────────────────────────────────────────────────

    /** True when the company has an active (non-terminal) offer on this freight. */
    @Query("SELECT COUNT(o) > 0 FROM CarrierOffer o " +
           "WHERE o.freight.id = :freightId AND o.carrierCompany.id = :companyId " +
           "AND o.status IN ('PENDING','NEGOTIATING')")
    boolean existsActiveOfferForCompanyAndFreight(
            @Param("freightId") Long freightId,
            @Param("companyId") Long companyId);

    // ── Pessimistic-lock variant for acceptance transaction ────────────────────

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM CarrierOffer o WHERE o.id = :id")
    Optional<CarrierOffer> findByIdWithLock(@Param("id") Long id);

    // ── Eager-fetch variant for response building (avoids LazyInitializationException) ──

    @Query("SELECT o FROM CarrierOffer o " +
           "LEFT JOIN FETCH o.carrierCompany " +
           "LEFT JOIN FETCH o.submittedBy " +
           "LEFT JOIN FETCH o.freight f " +
           "LEFT JOIN FETCH f.shipper " +
           "WHERE o.id = :id")
    Optional<CarrierOffer> findByIdEager(@Param("id") Long id);

    // ── Phase 6: dispatch portal queries ─────────────────────────────────────

    /**
     * Phase 6 — All ACCEPTED offers for a given carrier company.
     * Used to build the "awarded shipments" list for the dispatch portal.
     */
    @Query("SELECT o FROM CarrierOffer o " +
           "LEFT JOIN FETCH o.freight f " +
           "LEFT JOIN FETCH f.shipper " +
           "WHERE o.carrierCompany.id = :companyId AND o.status = 'ACCEPTED' " +
           "ORDER BY o.acceptedAt DESC")
    List<CarrierOffer> findAcceptedByCompanyId(@Param("companyId") Long companyId);

    // ── Bulk supersede (used when one offer is accepted) ──────────────────────

    /** Supersede all competing active offers except the winner. */
    @Modifying
    @Query("UPDATE CarrierOffer o SET o.status = 'SUPERSEDED', o.nextActor = NULL " +
           "WHERE o.freight.id = :freightId " +
           "AND o.id <> :winnerOfferId " +
           "AND o.status IN ('PENDING','NEGOTIATING')")
    int supersedCompetingOffers(
            @Param("freightId") Long freightId,
            @Param("winnerOfferId") Long winnerOfferId);
}
