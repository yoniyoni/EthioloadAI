package com.ethioloadai.carrier.offer.service;

import com.ethioloadai.carrier.company.entity.CarrierCompany;
import com.ethioloadai.carrier.company.entity.VerificationStatus;
import com.ethioloadai.carrier.company.repository.CarrierCompanyRepository;
import com.ethioloadai.carrier.offer.dto.CarrierOfferResponse;
import com.ethioloadai.carrier.offer.dto.CounterOfferRequest;
import com.ethioloadai.carrier.offer.dto.NegotiationEntryResponse;
import com.ethioloadai.carrier.offer.dto.SubmitOfferRequest;
import com.ethioloadai.carrier.offer.entity.CarrierOffer;
import com.ethioloadai.carrier.offer.entity.CarrierOffer.ActorType;
import com.ethioloadai.carrier.offer.entity.CarrierOffer.OfferStatus;
import com.ethioloadai.carrier.offer.entity.CarrierOffer.PricingType;
import com.ethioloadai.carrier.offer.entity.OfferNegotiationEntry;
import com.ethioloadai.carrier.offer.entity.OfferNegotiationEntry.NegotiationAction;
import com.ethioloadai.carrier.offer.exception.CarrierOfferException;
import com.ethioloadai.carrier.offer.repository.CarrierOfferRepository;
import com.ethioloadai.carrier.offer.repository.OfferNegotiationEntryRepository;
import com.ethioloadai.carrier.team.entity.CompanyRole;
import com.ethioloadai.carrier.team.entity.MembershipStatus;
import com.ethioloadai.carrier.team.repository.CompanyMembershipRepository;
import com.ethioloadai.carrier.team.service.CompanyContext;
import com.ethioloadai.carrier.team.service.CompanyContextService;
import com.ethioloadai.freight.entity.Freight;
import com.ethioloadai.freight.repository.FreightRepository;
import com.ethioloadai.notification.service.NotificationStoreService;
import com.ethioloadai.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Phase 5 — Full carrier offer lifecycle implementation.
 *
 * <h2>Concurrency strategy</h2>
 * <p>All state-changing methods use {@code Isolation.READ_COMMITTED} plus
 * pessimistic row-level locking ({@code findByIdWithLock}) on the
 * {@code carrier_offers} row. The JPA {@code @Version} optimistic-lock column
 * provides a second safety net for the rare case where the pessimistic lock
 * is not held (e.g., bulk supersede path).
 *
 * <h2>Turn-order rule</h2>
 * <pre>
 *   1. Carrier submits initial offer  → nextActor = SHIPPER
 *   2. Shipper counters               → nextActor = CARRIER
 *   3. Carrier counters               → nextActor = SHIPPER
 *   … alternates …
 *   N. Either party accepts on their turn.
 * </pre>
 *
 * <h2>Fixed-price rule</h2>
 * <p>For FIXED-price freight: carrier may only ACCEPT (amount = freight budget)
 * or DECLINE. Counter-offers produce a 422.
 */
@Service
@RequiredArgsConstructor
public class CarrierOfferServiceImpl implements CarrierOfferService {

    private static final Logger log = LoggerFactory.getLogger(CarrierOfferServiceImpl.class);

    private final CarrierOfferRepository offerRepo;
    private final OfferNegotiationEntryRepository historyRepo;
    private final FreightRepository freightRepo;
    private final CarrierCompanyRepository companyRepo;
    private final CompanyMembershipRepository membershipRepo;
    private final CompanyContextService companyContextService;
    private final NotificationStoreService notificationService;

    // ════════════════════════════════════════════════════════════════════════════
    // Carrier actions
    // ════════════════════════════════════════════════════════════════════════════

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public CarrierOfferResponse submitOffer(Long freightId, Long companyId,
                                             User caller, SubmitOfferRequest request) {
        log.info("submitOffer: freightId={} companyId={} caller={}", freightId, companyId, caller.getId());

        // 1. Resolve + validate company context (prevents mass-assignment)
        CompanyContext ctx = companyContextService.resolveForCompany(companyId, caller);
        requireCommercialRole(ctx, caller);

        CarrierCompany company = ctx.company();
        requireApprovedCompany(company);

        // 2. Load and validate freight
        Freight freight = freightRepo.findByIdForUpdate(freightId)
                .orElseThrow(() -> CarrierOfferException.freightNotFound(freightId));
        requireFreightOpen(freight);

        // 3. Check for duplicate active offer
        if (offerRepo.existsActiveOfferForCompanyAndFreight(freightId, companyId)) {
            throw CarrierOfferException.alreadyActive(freightId, companyId);
        }

        // 4. Determine amount based on pricing type
        PricingType pricingType = freight.getPriceType() == Freight.PriceType.FIXED
                ? PricingType.FIXED : PricingType.NEGOTIABLE;

        BigDecimal offerAmount;
        if (pricingType == PricingType.FIXED) {
            // For fixed price, the amount IS the freight budget — ignore client amount
            offerAmount = freight.getBudget();
        } else {
            offerAmount = request.amount();
        }
        validateAmount(offerAmount);

        // 5. Create the offer
        CarrierOffer offer = CarrierOffer.builder()
                .freight(freight)
                .carrierCompany(company)
                .submittedBy(caller)
                .pricingType(pricingType)
                .currentAmount(offerAmount)
                .status(OfferStatus.PENDING)
                .nextActor(ActorType.SHIPPER)   // shipper responds first
                .build();
        offer = offerRepo.save(offer);

        // 6. Append immutable history entry
        appendHistory(offer, ActorType.CARRIER, caller.getId(),
                NegotiationAction.OFFER, offerAmount, request.note());

        // 7. Notify the shipper
        notifyShipper(freight, offer, "CARRIER_OFFER_SUBMITTED",
                Map.of("offerId", offer.getId(),
                       "companyName", company.getCompanyName(),
                       "amount", offerAmount));

        log.info("Carrier offer created: offerId={} freight={} company={} amount={}",
                offer.getId(), freightId, companyId, offerAmount);
        return CarrierOfferResponse.from(loadWithHistory(offer.getId()));
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public CarrierOfferResponse carrierCounter(Long offerId, Long companyId,
                                                User caller, CounterOfferRequest request) {
        log.info("carrierCounter: offerId={} companyId={} caller={}", offerId, companyId, caller.getId());

        CompanyContext ctx = companyContextService.resolveForCompany(companyId, caller);
        requireCommercialRole(ctx, caller);

        CarrierOffer offer = offerRepo.findByIdWithLock(offerId)
                .orElseThrow(() -> CarrierOfferException.offerNotFound(offerId));

        // IDOR: offer must belong to this company
        if (!offer.getCarrierCompany().getId().equals(companyId)) {
            throw CarrierOfferException.forbidden();
        }
        requireNegotiablePricing(offer);
        requireActiveOffer(offer, "counter");
        requireActorTurn(offer, ActorType.CARRIER);

        validateAmount(request.amount());

        offer.setCurrentAmount(request.amount());
        offer.setStatus(OfferStatus.NEGOTIATING);
        offer.setNextActor(ActorType.SHIPPER);
        offerRepo.save(offer);

        appendHistory(offer, ActorType.CARRIER, caller.getId(),
                NegotiationAction.COUNTER_OFFER, request.amount(), request.note());

        notifyShipper(offer.getFreight(), offer, "CARRIER_COUNTER_OFFER",
                Map.of("offerId", offer.getId(), "amount", request.amount()));

        return CarrierOfferResponse.from(loadWithHistory(offer.getId()));
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public CarrierOfferResponse carrierAccept(Long offerId, Long companyId, User caller) {
        log.info("carrierAccept: offerId={} companyId={} caller={}", offerId, companyId, caller.getId());

        CompanyContext ctx = companyContextService.resolveForCompany(companyId, caller);
        requireCommercialRole(ctx, caller);

        Long freightId = offerRepo.findFreightIdById(offerId)
                .orElseThrow(() -> CarrierOfferException.offerNotFound(offerId));
        Freight freight = freightRepo.findByIdForUpdate(freightId)
                .orElseThrow(() -> CarrierOfferException.freightNotFound(freightId));
        requireFreightOpen(freight);

        CarrierOffer offer = offerRepo.findByIdWithLock(offerId)
                .orElseThrow(() -> CarrierOfferException.offerNotFound(offerId));

        if (!offer.getFreight().getId().equals(freightId)
                || !offer.getCarrierCompany().getId().equals(companyId)) {
            throw CarrierOfferException.forbidden();
        }
        // Carrier can only accept when it's their turn (shipper counter was last)
        requireActiveOffer(offer, "accept");
        if (offer.getStatus() != OfferStatus.NEGOTIATING) {
            throw CarrierOfferException.illegalTransition(offer.getStatus(),
                    "carrierAccept — only valid after a shipper counter-offer");
        }
        requireActorTurn(offer, ActorType.CARRIER);

        return performAcceptance(offer, caller, ActorType.CARRIER);
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public CarrierOfferResponse withdraw(Long offerId, Long companyId, User caller) {
        log.info("withdraw: offerId={} companyId={} caller={}", offerId, companyId, caller.getId());

        CompanyContext ctx = companyContextService.resolveForCompany(companyId, caller);
        requireCommercialRole(ctx, caller);

        CarrierOffer offer = offerRepo.findByIdWithLock(offerId)
                .orElseThrow(() -> CarrierOfferException.offerNotFound(offerId));

        if (!offer.getCarrierCompany().getId().equals(companyId)) {
            throw CarrierOfferException.forbidden();
        }
        requireActiveOffer(offer, "withdraw");

        String companyNameForNotif = offer.getCarrierCompany().getCompanyName();
        offer.setStatus(OfferStatus.WITHDRAWN);
        offer.setWithdrawnAt(LocalDateTime.now());
        offer.setNextActor(null);
        offerRepo.save(offer);

        appendHistory(offer, ActorType.CARRIER, caller.getId(),
                NegotiationAction.WITHDRAW, null, null);

        notifyShipper(offer.getFreight(), offer, "CARRIER_OFFER_WITHDRAWN",
                Map.of("offerId", offer.getId(),
                       "companyName", companyNameForNotif));

        log.info("Offer withdrawn: offerId={}", offer.getId());
        return CarrierOfferResponse.from(loadWithHistory(offer.getId()));
    }

    // ════════════════════════════════════════════════════════════════════════════
    // Shipper actions
    // ════════════════════════════════════════════════════════════════════════════

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public CarrierOfferResponse shipperCounter(Long freightId, Long offerId,
                                                User shipper, CounterOfferRequest request) {
        log.info("shipperCounter: freightId={} offerId={} shipper={}", freightId, offerId, shipper.getId());

        Freight freight = loadFreightForShipper(freightId, shipper);
        CarrierOffer offer = offerRepo.findByIdWithLock(offerId)
                .orElseThrow(() -> CarrierOfferException.offerNotFound(offerId));

        // IDOR: offer must belong to this freight
        if (!offer.getFreight().getId().equals(freightId)) {
            throw CarrierOfferException.forbidden();
        }
        requireNegotiablePricing(offer);
        requireActiveOffer(offer, "counter");
        requireActorTurn(offer, ActorType.SHIPPER);

        validateAmount(request.amount());

        offer.setCurrentAmount(request.amount());
        offer.setStatus(OfferStatus.NEGOTIATING);
        offer.setNextActor(ActorType.CARRIER);
        offerRepo.save(offer);

        appendHistory(offer, ActorType.SHIPPER, shipper.getId(),
                NegotiationAction.COUNTER_OFFER, request.amount(), request.note());

        notifyCarrierOfChange(offer, "SHIPPER_COUNTER_OFFER",
                Map.of("offerId", offer.getId(), "amount", request.amount()));

        return CarrierOfferResponse.from(loadWithHistory(offer.getId()));
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public CarrierOfferResponse shipperAccept(Long freightId, Long offerId, User shipper) {
        log.info("shipperAccept: freightId={} offerId={} shipper={}", freightId, offerId, shipper.getId());

        Freight freight = freightRepo.findByIdForUpdate(freightId)
                .orElseThrow(() -> CarrierOfferException.freightNotFound(freightId));
        authorizeShipperAccess(freight, shipper);
        requireFreightOpen(freight);

        // Check freight not already awarded — use a count query to bypass JPA cache
        boolean alreadyAwarded = freight.getCarrierAwardOfferId() != null
                || freightRepo.findById(freightId)
                        .map(f -> f.getCarrierAwardOfferId() != null)
                        .orElse(false);
        if (alreadyAwarded) {
            throw CarrierOfferException.freightAlreadyAwarded(freightId);
        }

        CarrierOffer offer = offerRepo.findByIdWithLock(offerId)
                .orElseThrow(() -> CarrierOfferException.offerNotFound(offerId));

        if (!offer.getFreight().getId().equals(freightId)) {
            throw CarrierOfferException.forbidden();
        }
        requireActiveOffer(offer, "accept");
        requireActorTurn(offer, ActorType.SHIPPER);

        return performAcceptance(offer, shipper, ActorType.SHIPPER);
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public CarrierOfferResponse shipperReject(Long freightId, Long offerId, User shipper) {
        log.info("shipperReject: freightId={} offerId={} shipper={}", freightId, offerId, shipper.getId());

        loadFreightForShipper(freightId, shipper);

        CarrierOffer offer = offerRepo.findByIdWithLock(offerId)
                .orElseThrow(() -> CarrierOfferException.offerNotFound(offerId));

        if (!offer.getFreight().getId().equals(freightId)) {
            throw CarrierOfferException.forbidden();
        }
        requireActiveOffer(offer, "reject");

        offer.setStatus(OfferStatus.REJECTED);
        offer.setRejectedAt(LocalDateTime.now());
        offer.setNextActor(null);
        offerRepo.save(offer);

        appendHistory(offer, ActorType.SHIPPER, shipper.getId(),
                NegotiationAction.REJECT, null, null);

        notifyCarrierOfChange(offer, "SHIPPER_REJECTED_OFFER",
                Map.of("offerId", offer.getId()));

        log.info("Offer rejected: offerId={}", offer.getId());
        return CarrierOfferResponse.from(loadWithHistory(offer.getId()));
    }

    // ════════════════════════════════════════════════════════════════════════════
    // Query methods
    // ════════════════════════════════════════════════════════════════════════════

    @Override
    @Transactional(readOnly = true)
    public List<CarrierOfferResponse> getOffersForFreight(Long freightId, User caller) {
        Freight freight = freightRepo.findById(freightId)
                .orElseThrow(() -> CarrierOfferException.freightNotFound(freightId));

        boolean isShipperOwner = freight.getShipper().getId().equals(caller.getId());
        boolean isAdmin = caller.getRole() == User.Role.ADMIN;

        List<CarrierOffer> offers = offerRepo.findByFreightId(freightId);

        if (isShipperOwner || isAdmin) {
            // Freight owner and admin see full details including history
            return offers.stream()
                    .map(o -> {
                        List<OfferNegotiationEntry> hist = historyRepo
                                .findByCarrierOfferIdOrderByCreatedAtAsc(o.getId());
                        o.getNegotiationHistory().clear();
                        o.getNegotiationHistory().addAll(hist);
                        return CarrierOfferResponse.from(o);
                    })
                    .toList();
        }

        // Carrier member: show own offer with history, others as summary
        Long callerCompanyId = resolveCallerCompanyId(caller);
        return offers.stream()
                .map(o -> {
                    boolean isOwn = callerCompanyId != null
                            && o.getCarrierCompany().getId().equals(callerCompanyId);
                    if (isOwn) {
                        List<OfferNegotiationEntry> hist = historyRepo
                                .findByCarrierOfferIdOrderByCreatedAtAsc(o.getId());
                        o.getNegotiationHistory().clear();
                        o.getNegotiationHistory().addAll(hist);
                        return CarrierOfferResponse.from(o);
                    }
                    return CarrierOfferResponse.summary(o);
                })
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public CarrierOfferResponse getOffer(Long offerId, User caller) {
        CarrierOffer offer = offerRepo.findByIdEager(offerId)
                .orElseThrow(() -> CarrierOfferException.offerNotFound(offerId));
        authorizeOfferView(offer, caller);
        List<OfferNegotiationEntry> hist = historyRepo
                .findByCarrierOfferIdOrderByCreatedAtAsc(offerId);
        offer.getNegotiationHistory().clear();
        offer.getNegotiationHistory().addAll(hist);
        return CarrierOfferResponse.from(offer);
    }

    @Override
    @Transactional(readOnly = true)
    public CarrierOfferResponse getCompanyOfferForFreight(Long freightId, Long companyId, User caller) {
        companyContextService.resolveForCompany(companyId, caller); // validates membership
        // Find the offer directly by freight+company scope
        CarrierOffer offer = offerRepo.findByFreightId(freightId).stream()
                .filter(o -> o.getCarrierCompany().getId().equals(companyId))
                .findFirst()
                .orElseThrow(() -> CarrierOfferException.offerNotFound(-1L));
        // Reload eagerly for safe access
        CarrierOffer eager = offerRepo.findByIdEager(offer.getId())
                .orElseThrow(() -> CarrierOfferException.offerNotFound(offer.getId()));
        List<OfferNegotiationEntry> hist = historyRepo
                .findByCarrierOfferIdOrderByCreatedAtAsc(eager.getId());
        eager.getNegotiationHistory().clear();
        eager.getNegotiationHistory().addAll(hist);
        return CarrierOfferResponse.from(eager);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<CarrierOfferResponse> getCompanyOffers(Long companyId, User caller, Pageable pageable) {
        companyContextService.resolveForCompany(companyId, caller); // validates membership
        return offerRepo.findByCarrierCompanyId(companyId, pageable)
                .map(CarrierOfferResponse::summary);
    }

    @Override
    @Transactional(readOnly = true)
    public List<NegotiationEntryResponse> getNegotiationHistory(Long offerId, User caller) {
        CarrierOffer offer = offerRepo.findByIdEager(offerId)
                .orElseThrow(() -> CarrierOfferException.offerNotFound(offerId));
        authorizeOfferView(offer, caller);
        return historyRepo.findByCarrierOfferIdOrderByCreatedAtAsc(offerId)
                .stream().map(NegotiationEntryResponse::from).toList();
    }

    // ════════════════════════════════════════════════════════════════════════════
    // Internal — acceptance (atomic, locked)
    // ════════════════════════════════════════════════════════════════════════════

    /**
     * Atomic offer acceptance.
     * <ol>
     *   <li>Marks the offer ACCEPTED with agreed amount.</li>
     *   <li>Supersedes all competing active offers on the same freight.</li>
     *   <li>Records the award on the freight row (carrier_award_offer_id).</li>
     *   <li>Appends history entry.</li>
     *   <li>Fires notifications.</li>
     * </ol>
     */
    private CarrierOfferResponse performAcceptance(CarrierOffer offer, User actor, ActorType actorType) {
        BigDecimal agreedAmount = offer.getCurrentAmount();
        Long freightId = offer.getFreight().getId();
        // Capture company name BEFORE any cache-clearing operations
        String companyName = offer.getCarrierCompany().getCompanyName();
        Long submittedByUserId = offer.getSubmittedBy().getId();

        // Double-check freight has no award yet (re-read under the lock)
        Freight freight = freightRepo.findById(freightId)
                .orElseThrow(() -> CarrierOfferException.freightNotFound(freightId));

        if (freight.getCarrierAwardOfferId() != null) {
            throw CarrierOfferException.freightAlreadyAwarded(freight.getId());
        }

        try {
            // 1. Accept this offer
            offer.setStatus(OfferStatus.ACCEPTED);
            offer.setAcceptedAt(LocalDateTime.now());
            offer.setAgreedAmount(agreedAmount);
            offer.setNextActor(null);
            offerRepo.save(offer);

            // 2. Supersede all competing offers
            int superseded = offerRepo.supersedCompetingOffers(freightId, offer.getId());
            log.info("Superseded {} competing offers for freight {}", superseded, freightId);

            // 3. Record award on freight via bulk JPQL update (bypasses L1 cache,
            //    immediately visible to subsequent JDBC reads in the same transaction).
            //    The WHERE carrier_award_offer_id IS NULL acts as a concurrency guard:
            //    if two threads race, only one update succeeds.
            int updated = freightRepo.recordCarrierAward(freightId, offer.getId());
            if (updated == 0) {
                // Another transaction already set the award — concurrent acceptance
                throw CarrierOfferException.freightAlreadyAwarded(freightId);
            }

        } catch (ObjectOptimisticLockingFailureException e) {
            throw CarrierOfferException.freightAlreadyAwarded(freightId);
        }

        // 4. History entry
        appendHistory(offer, actorType, actor.getId(), NegotiationAction.ACCEPT, null, null);

        // 5. Notifications — use pre-captured values (avoid lazy proxy access after cache clear)
        Freight freshFreight = freightRepo.findById(freightId)
                .orElseThrow(() -> CarrierOfferException.freightNotFound(freightId));
        notifyShipper(freshFreight, offer, "CARRIER_OFFER_ACCEPTED",
                Map.of("offerId", offer.getId(),
                       "companyName", companyName,
                       "agreedAmount", agreedAmount));
        try {
            notificationService.recordNotification(submittedByUserId,
                    "OFFER_ACCEPTED_NOTIFICATION",
                    Map.of("offerId", offer.getId(), "agreedAmount", agreedAmount));
        } catch (Exception e) {
            log.warn("Failed to notify carrier submitter {} for offer {}: {}",
                    submittedByUserId, offer.getId(), e.getMessage());
        }

        log.info("Offer ACCEPTED: offerId={} freight={} company={} agreedAmount={}",
                offer.getId(), freightId, companyName, agreedAmount);

        return CarrierOfferResponse.from(loadWithHistory(offer.getId()));
    }

    // ════════════════════════════════════════════════════════════════════════════
    // Internal helpers
    // ════════════════════════════════════════════════════════════════════════════

    /** Append an immutable history entry (always runs within the caller's transaction). */
    private void appendHistory(CarrierOffer offer, ActorType actorType, Long actorUserId,
                                NegotiationAction action, BigDecimal amount, String note) {
        OfferNegotiationEntry entry = OfferNegotiationEntry.builder()
                .carrierOffer(offer)
                .actorType(actorType)
                .actorUserId(actorUserId)
                .action(action)
                .amount(amount)
                .note(note)
                .build();
        historyRepo.save(entry);
    }

    /** Load offer with eager associations + history for response building. */
    private CarrierOffer loadWithHistory(Long offerId) {
        CarrierOffer offer = offerRepo.findByIdEager(offerId)
                .orElseThrow(() -> CarrierOfferException.offerNotFound(offerId));
        List<OfferNegotiationEntry> hist = historyRepo
                .findByCarrierOfferIdOrderByCreatedAtAsc(offerId);
        offer.getNegotiationHistory().clear();
        offer.getNegotiationHistory().addAll(hist);
        return offer;
    }

    /**
     * Require the caller to have a commercial role (CARRIER_OWNER or DISPATCHER).
     * FLEET_MANAGER and DRIVER are denied.
     */
    private void requireCommercialRole(CompanyContext ctx, User caller) {
        if (caller.getRole() == User.Role.DRIVER || caller.getRole() == User.Role.FLEET_OWNER) {
            throw CarrierOfferException.driverCannotSubmitCompanyOffer();
        }
        CompanyRole role = ctx.role();
        if (role != CompanyRole.CARRIER_OWNER && role != CompanyRole.DISPATCHER) {
            throw CarrierOfferException.driverCannotSubmitCompanyOffer();
        }
    }

    private void requireApprovedCompany(CarrierCompany company) {
        if (company.getVerificationStatus() != VerificationStatus.APPROVED) {
            throw CarrierOfferException.companyNotApproved();
        }
    }

    private void requireFreightOpen(Freight freight) {
        if (freight.getStatus() != Freight.FreightStatus.POSTED) {
            throw CarrierOfferException.freightNotOpen(freight.getId());
        }
        // Check award via direct query to avoid stale L1-cache state
        boolean alreadyAwarded = freightRepo.existsById(freight.getId())
                && freightRepo.findById(freight.getId())
                        .map(f -> f.getCarrierAwardOfferId() != null)
                        .orElse(false);
        if (alreadyAwarded) {
            throw CarrierOfferException.freightAlreadyAwarded(freight.getId());
        }
    }

    private void requireActiveOffer(CarrierOffer offer, String action) {
        if (!offer.getStatus().isActive()) {
            throw CarrierOfferException.illegalTransition(offer.getStatus(), action);
        }
    }

    private void requireActorTurn(CarrierOffer offer, ActorType expected) {
        if (offer.getNextActor() != expected) {
            throw CarrierOfferException.notYourTurn(
                    offer.getNextActor() != null ? offer.getNextActor() : expected);
        }
    }

    private void requireNegotiablePricing(CarrierOffer offer) {
        if (offer.getPricingType() == PricingType.FIXED) {
            throw CarrierOfferException.fixedPriceCounterNotAllowed();
        }
    }

    private void validateAmount(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new com.ethioloadai.exception.ValidationException(
                    "Offer amount must be greater than 0", "amount", "must be > 0");
        }
        if (amount.scale() > 2) {
            throw new com.ethioloadai.exception.ValidationException(
                    "Offer amount must have at most 2 decimal places", "amount", "invalid scale");
        }
    }

    /**
     * Load freight and verify the caller is the shipper (or admin).
     */
    private Freight loadFreightForShipper(Long freightId, User caller) {
        Freight freight = freightRepo.findById(freightId)
                .orElseThrow(() -> CarrierOfferException.freightNotFound(freightId));
        authorizeShipperAccess(freight, caller);
        return freight;
    }

    private void authorizeShipperAccess(Freight freight, User caller) {
        boolean isShipperOwner = freight.getShipper().getId().equals(caller.getId());
        boolean isAdmin = caller.getRole() == User.Role.ADMIN;
        if (!isShipperOwner && !isAdmin) {
            throw CarrierOfferException.forbidden();
        }
    }

    /**
     * Authorize view access: caller must be the freight shipper, an active
     * member of the carrier company that owns the offer, or an ADMIN.
     */
    private void authorizeOfferView(CarrierOffer offer, User caller) {
        if (caller.getRole() == User.Role.ADMIN) return;
        boolean isShipper = offer.getFreight().getShipper().getId().equals(caller.getId());
        if (isShipper) return;
        boolean isCarrierMember = membershipRepo
                .findByCarrierCompanyIdAndUserId(offer.getCarrierCompany().getId(), caller.getId())
                .filter(m -> m.getStatus() == MembershipStatus.ACTIVE)
                .isPresent();
        if (!isCarrierMember) throw CarrierOfferException.forbidden();
    }

    /** Resolve the first active company membership for a caller (for view decisions). */
    private Long resolveCallerCompanyId(User caller) {
        var active = membershipRepo.findActiveByUserId(caller.getId());
        return active.isEmpty() ? null : active.get(0).getCarrierCompanyId();
    }

    private void notifyShipper(Freight freight, CarrierOffer offer, String type, Map<String, Object> data) {
        try {
            notificationService.recordNotification(freight.getShipper().getId(), type, data);
        } catch (Exception e) {
            log.warn("Failed to notify shipper {} for offer {}: {}", freight.getShipper().getId(),
                    offer.getId(), e.getMessage());
        }
    }

    private void notifyCarrierOfChange(CarrierOffer offer, String type, Map<String, Object> data) {
        try {
            notificationService.recordNotification(offer.getSubmittedBy().getId(), type, data);
        } catch (Exception e) {
            log.warn("Failed to notify carrier submitter {} for offer {}: {}",
                    offer.getSubmittedBy().getId(), offer.getId(), e.getMessage());
        }
    }
}
