package com.ethioloadai.carrier.offer.service;

import com.ethioloadai.carrier.offer.dto.CarrierOfferResponse;
import com.ethioloadai.carrier.offer.dto.CounterOfferRequest;
import com.ethioloadai.carrier.offer.dto.NegotiationEntryResponse;
import com.ethioloadai.carrier.offer.dto.SubmitOfferRequest;
import com.ethioloadai.user.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

/**
 * Phase 5 — Commercial offer lifecycle for carrier companies.
 *
 * <h2>Role policy</h2>
 * <ul>
 *   <li>CARRIER_OWNER, DISPATCHER — may submit, counter, withdraw, and view own company's offers.</li>
 *   <li>FLEET_MANAGER — read-only (view); cannot submit or mutate.</li>
 *   <li>DRIVER — denied all commercial operations.</li>
 *   <li>SHIPPER — may view offers on own freight, counter, accept, reject.</li>
 *   <li>ADMIN — platform-wide read via admin endpoints.</li>
 * </ul>
 *
 * <h2>Concurrency guarantee</h2>
 * <p>All mutation methods use pessimistic row-level locking on the
 * {@code carrier_offers} row so that concurrent acceptance attempts result
 * in exactly one winner and a clean exception for the loser.
 */
public interface CarrierOfferService {

    // ── Carrier actions ────────────────────────────────────────────────────────

    /**
     * Carrier submits an initial offer on a freight shipment.
     * The company is resolved from the caller's active membership.
     *
     * @param freightId target freight
     * @param companyId the carrier company (from URL path — verified against caller membership)
     * @param caller    authenticated user (CARRIER_OWNER or DISPATCHER)
     * @param request   offer details; amount ignored for FIXED-price freight
     * @return the created offer
     */
    CarrierOfferResponse submitOffer(Long freightId, Long companyId, User caller, SubmitOfferRequest request);

    /**
     * Carrier submits a counter-offer in response to a shipper counter.
     * Only valid when {@code nextActor == CARRIER}.
     */
    CarrierOfferResponse carrierCounter(Long offerId, Long companyId, User caller, CounterOfferRequest request);

    /**
     * Carrier accepts the shipper's most recent counter-offer.
     * Only valid when {@code nextActor == CARRIER} and status is NEGOTIATING.
     */
    CarrierOfferResponse carrierAccept(Long offerId, Long companyId, User caller);

    /**
     * Carrier withdraws its own active offer.
     * Not allowed once the offer is ACCEPTED.
     */
    CarrierOfferResponse withdraw(Long offerId, Long companyId, User caller);

    // ── Shipper actions ────────────────────────────────────────────────────────

    /**
     * Shipper counters a carrier's offer.
     * Only valid when {@code nextActor == SHIPPER}.
     */
    CarrierOfferResponse shipperCounter(Long freightId, Long offerId, User shipper, CounterOfferRequest request);

    /**
     * Shipper accepts the carrier's current offer (initial or counter).
     * Only valid when {@code nextActor == SHIPPER}.
     * Atomically supersedes all other active offers on the freight.
     */
    CarrierOfferResponse shipperAccept(Long freightId, Long offerId, User shipper);

    /**
     * Shipper rejects a carrier's offer.
     */
    CarrierOfferResponse shipperReject(Long freightId, Long offerId, User shipper);

    // ── Query methods ──────────────────────────────────────────────────────────

    /**
     * List all offers on a freight. Shipper sees full history for their freight.
     * Other carriers see only their own offer details.
     */
    List<CarrierOfferResponse> getOffersForFreight(Long freightId, User caller);

    /**
     * Get a single offer by ID with full history.
     * Caller must be: the submitting company member, the freight owner, or ADMIN.
     */
    CarrierOfferResponse getOffer(Long offerId, User caller);

    /**
     * Get a carrier company's own offer on a specific freight.
     */
    CarrierOfferResponse getCompanyOfferForFreight(Long freightId, Long companyId, User caller);

    /**
     * Paginated list of all offers submitted by a company (company portal view).
     */
    Page<CarrierOfferResponse> getCompanyOffers(Long companyId, User caller, Pageable pageable);

    /**
     * Full negotiation history for an offer (authorized caller only).
     */
    List<NegotiationEntryResponse> getNegotiationHistory(Long offerId, User caller);
}
