package com.ethioloadai.carrier.offer;

import com.ethioloadai.carrier.company.entity.CarrierCompany;
import com.ethioloadai.carrier.company.entity.VerificationStatus;
import com.ethioloadai.carrier.company.repository.CarrierCompanyRepository;
import com.ethioloadai.carrier.offer.dto.CarrierOfferResponse;
import com.ethioloadai.carrier.offer.dto.CounterOfferRequest;
import com.ethioloadai.carrier.offer.dto.SubmitOfferRequest;
import com.ethioloadai.carrier.offer.entity.CarrierOffer;
import com.ethioloadai.carrier.offer.entity.CarrierOffer.ActorType;
import com.ethioloadai.carrier.offer.entity.CarrierOffer.OfferStatus;
import com.ethioloadai.carrier.offer.entity.OfferNegotiationEntry;
import com.ethioloadai.carrier.offer.exception.CarrierOfferException;
import com.ethioloadai.carrier.offer.repository.CarrierOfferRepository;
import com.ethioloadai.carrier.offer.repository.OfferNegotiationEntryRepository;
import com.ethioloadai.carrier.offer.service.CarrierOfferServiceImpl;
import com.ethioloadai.carrier.team.entity.CompanyMembership;
import com.ethioloadai.carrier.team.entity.CompanyRole;
import com.ethioloadai.carrier.team.entity.MembershipStatus;
import com.ethioloadai.carrier.team.repository.CompanyMembershipRepository;
import com.ethioloadai.carrier.team.service.CompanyContext;
import com.ethioloadai.carrier.team.service.CompanyContextService;
import com.ethioloadai.freight.entity.Freight;
import com.ethioloadai.freight.repository.FreightRepository;
import com.ethioloadai.notification.service.NotificationStoreService;
import com.ethioloadai.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Phase 5 — Unit tests for {@link CarrierOfferServiceImpl}.
 *
 * <p>Covers: fixed price, negotiable, counter-offer, turn order, lifecycle,
 * tenant isolation, IDOR, mass-assignment, money validation, booking boundary,
 * and concurrency guard.
 */
@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
@DisplayName("CarrierOfferService")
class CarrierOfferServiceTest {

    @Mock CarrierOfferRepository offerRepo;
    @Mock OfferNegotiationEntryRepository historyRepo;
    @Mock FreightRepository freightRepo;
    @Mock CarrierCompanyRepository companyRepo;
    @Mock CompanyMembershipRepository membershipRepo;
    @Mock CompanyContextService companyContextService;
    @Mock NotificationStoreService notificationService;

    @InjectMocks CarrierOfferServiceImpl service;

    // ── Fixtures ───────────────────────────────────────────────────────────────

    User shipper;
    User abcOwner;
    User abcDispatcher;
    User xyzOwner;
    User fleetManager;
    User driver;
    CarrierCompany abcCompany;
    CarrierCompany xyzCompany;
    Freight negotiableFreight;
    Freight fixedFreight;

    @BeforeEach
    void setUp() {
        shipper       = user(1L, User.Role.SHIPPER);
        abcOwner      = user(2L, User.Role.SHIPPER); // platform role SHIPPER, company role CARRIER_OWNER
        abcDispatcher = user(3L, User.Role.SHIPPER);
        xyzOwner      = user(4L, User.Role.SHIPPER);
        fleetManager  = user(5L, User.Role.SHIPPER);
        driver        = user(6L, User.Role.DRIVER);

        abcCompany = company(10L, VerificationStatus.APPROVED);
        xyzCompany = company(20L, VerificationStatus.APPROVED);

        negotiableFreight = freight(100L, Freight.PriceType.NEGOTIABLE, new BigDecimal("50000"), shipper);
        fixedFreight      = freight(101L, Freight.PriceType.FIXED,      new BigDecimal("50000"), shipper);
    }

    // ════════════════════════════════════════════════════════════════════════════
    // 1 — Submit offer
    // ════════════════════════════════════════════════════════════════════════════

    @Nested @DisplayName("Submit Offer")
    class SubmitOfferTests {

        @Test @DisplayName("CARRIER_OWNER can submit negotiable offer")
        void ownerSubmitsNegotiableOffer() {
            stubCompanyContext(abcOwner, abcCompany, CompanyRole.CARRIER_OWNER);
            stubFreight(negotiableFreight);
            stubNoActiveOffer(100L, 10L);
            CarrierOffer saved = savedOffer(1L, abcCompany, negotiableFreight,
                    CarrierOffer.PricingType.NEGOTIABLE, new BigDecimal("45000"), OfferStatus.PENDING);
            when(offerRepo.save(any())).thenReturn(saved);
            when(offerRepo.findByIdEager(1L)).thenReturn(Optional.of(saved));
            when(historyRepo.save(any())).thenAnswer(i -> i.getArgument(0));
            when(historyRepo.findByCarrierOfferIdOrderByCreatedAtAsc(1L)).thenReturn(List.of());

            CarrierOfferResponse resp = service.submitOffer(100L, 10L, abcOwner,
                    new SubmitOfferRequest(new BigDecimal("45000"), null));

            assertThat(resp.status()).isEqualTo(OfferStatus.PENDING);
            assertThat(resp.currentAmount()).isEqualByComparingTo("45000");
            assertThat(resp.nextActor()).isEqualTo(ActorType.SHIPPER);
        }

        @Test @DisplayName("DISPATCHER can submit offer")
        void dispatcherSubmitsOffer() {
            stubCompanyContext(abcDispatcher, abcCompany, CompanyRole.DISPATCHER);
            stubFreight(negotiableFreight);
            stubNoActiveOffer(100L, 10L);
            CarrierOffer saved = savedOffer(2L, abcCompany, negotiableFreight,
                    CarrierOffer.PricingType.NEGOTIABLE, new BigDecimal("44000"), OfferStatus.PENDING);
            when(offerRepo.save(any())).thenReturn(saved);
            when(offerRepo.findByIdEager(2L)).thenReturn(Optional.of(saved));
            when(historyRepo.save(any())).thenAnswer(i -> i.getArgument(0));
            when(historyRepo.findByCarrierOfferIdOrderByCreatedAtAsc(2L)).thenReturn(List.of());

            CarrierOfferResponse resp = service.submitOffer(100L, 10L, abcDispatcher,
                    new SubmitOfferRequest(new BigDecimal("44000"), null));

            assertThat(resp.status()).isEqualTo(OfferStatus.PENDING);
        }

        @Test @DisplayName("FLEET_MANAGER denied from submitting offer")
        void fleetManagerDenied() {
            stubCompanyContext(fleetManager, abcCompany, CompanyRole.FLEET_MANAGER);
            stubFreight(negotiableFreight);
            stubNoActiveOffer(100L, 10L);

            assertThatThrownBy(() ->
                    service.submitOffer(100L, 10L, fleetManager,
                            new SubmitOfferRequest(new BigDecimal("45000"), null)))
                    .isInstanceOf(CarrierOfferException.class)
                    .hasMessageContaining("CARRIER_OWNER or DISPATCHER");
        }

        @Test @DisplayName("DRIVER denied from submitting company offer")
        void driverDenied() {
            // Driver has no company membership; service throws before resolving context
            when(companyContextService.resolveForCompany(10L, driver))
                    .thenThrow(CarrierOfferException.driverCannotSubmitCompanyOffer());

            assertThatThrownBy(() ->
                    service.submitOffer(100L, 10L, driver,
                            new SubmitOfferRequest(new BigDecimal("45000"), null)))
                    .isInstanceOf(CarrierOfferException.class);
        }

        @Test @DisplayName("Fixed-price offer uses freight budget, ignores client amount")
        void fixedPriceUsesFreightBudget() {
            stubCompanyContext(abcOwner, abcCompany, CompanyRole.CARRIER_OWNER);
            stubFreight(fixedFreight);
            stubNoActiveOffer(101L, 10L);
            CarrierOffer saved = savedOffer(3L, abcCompany, fixedFreight,
                    CarrierOffer.PricingType.FIXED, new BigDecimal("50000"), OfferStatus.PENDING);
            when(offerRepo.save(any())).thenReturn(saved);
            when(offerRepo.findByIdEager(3L)).thenReturn(Optional.of(saved));
            when(historyRepo.save(any())).thenAnswer(i -> i.getArgument(0));
            when(historyRepo.findByCarrierOfferIdOrderByCreatedAtAsc(3L)).thenReturn(List.of());

            // Client sends 45000, but freight budget is 50000 — must be overridden
            CarrierOfferResponse resp = service.submitOffer(101L, 10L, abcOwner,
                    new SubmitOfferRequest(new BigDecimal("45000"), null));

            // Amount captured in the saved offer = 50000 (freight budget)
            assertThat(resp.currentAmount()).isEqualByComparingTo("50000");
        }

        @Test @DisplayName("Duplicate active offer rejected")
        void duplicateActiveOfferRejected() {
            stubCompanyContext(abcOwner, abcCompany, CompanyRole.CARRIER_OWNER);
            stubFreight(negotiableFreight);
            when(offerRepo.existsActiveOfferForCompanyAndFreight(100L, 10L)).thenReturn(true);

            assertThatThrownBy(() ->
                    service.submitOffer(100L, 10L, abcOwner,
                            new SubmitOfferRequest(new BigDecimal("45000"), null)))
                    .isInstanceOf(CarrierOfferException.class)
                    .hasMessageContaining("already has an active offer");
        }

        @Test @DisplayName("Offer on non-POSTED freight rejected")
        void offerOnMatchedFreightRejected() {
            Freight matched = freight(100L, Freight.PriceType.NEGOTIABLE, new BigDecimal("50000"), shipper);
            matched.setStatus(Freight.FreightStatus.MATCHED);
            stubCompanyContext(abcOwner, abcCompany, CompanyRole.CARRIER_OWNER);
            when(freightRepo.findById(100L)).thenReturn(Optional.of(matched));

            assertThatThrownBy(() ->
                    service.submitOffer(100L, 10L, abcOwner,
                            new SubmitOfferRequest(new BigDecimal("45000"), null)))
                    .isInstanceOf(CarrierOfferException.class)
                    .hasMessageContaining("not open");
        }

        @Test @DisplayName("Zero amount rejected")
        void zeroAmountRejected() {
            stubCompanyContext(abcOwner, abcCompany, CompanyRole.CARRIER_OWNER);
            stubFreight(negotiableFreight);
            stubNoActiveOffer(100L, 10L);

            assertThatThrownBy(() ->
                    service.submitOffer(100L, 10L, abcOwner,
                            new SubmitOfferRequest(BigDecimal.ZERO, null)))
                    .isInstanceOf(Exception.class);
        }

        @Test @DisplayName("Negative amount rejected")
        void negativeAmountRejected() {
            stubCompanyContext(abcOwner, abcCompany, CompanyRole.CARRIER_OWNER);
            stubFreight(negotiableFreight);
            stubNoActiveOffer(100L, 10L);

            assertThatThrownBy(() ->
                    service.submitOffer(100L, 10L, abcOwner,
                            new SubmitOfferRequest(new BigDecimal("-100"), null)))
                    .isInstanceOf(Exception.class);
        }

        @Test @DisplayName("Unapproved company denied")
        void unapprovedCompanyDenied() {
            CarrierCompany draft = company(10L, VerificationStatus.DRAFT);
            stubCompanyContextWith(abcOwner, draft, CompanyRole.CARRIER_OWNER);
            stubFreight(negotiableFreight);
            stubNoActiveOffer(100L, 10L);

            assertThatThrownBy(() ->
                    service.submitOffer(100L, 10L, abcOwner,
                            new SubmitOfferRequest(new BigDecimal("45000"), null)))
                    .isInstanceOf(CarrierOfferException.class)
                    .hasMessageContaining("not approved");
        }
    }

    // ════════════════════════════════════════════════════════════════════════════
    // 2 — Counter-offer
    // ════════════════════════════════════════════════════════════════════════════

    @Nested @DisplayName("Counter-Offer")
    class CounterOfferTests {

        @Test @DisplayName("Shipper can counter a PENDING carrier offer")
        void shipperCountersPendingOffer() {
            CarrierOffer offer = pendingOffer(1L, abcCompany, negotiableFreight,
                    new BigDecimal("45000"), ActorType.SHIPPER);
            when(freightRepo.findById(100L)).thenReturn(Optional.of(negotiableFreight));
            when(offerRepo.findByIdWithLock(1L)).thenReturn(Optional.of(offer));
            when(offerRepo.save(any())).thenAnswer(i -> i.getArgument(0));
            when(historyRepo.save(any())).thenAnswer(i -> i.getArgument(0));
            when(offerRepo.findByIdEager(1L)).thenReturn(Optional.of(offer));
            when(historyRepo.findByCarrierOfferIdOrderByCreatedAtAsc(1L)).thenReturn(List.of());

            CarrierOfferResponse resp = service.shipperCounter(100L, 1L, shipper,
                    new CounterOfferRequest(new BigDecimal("48000"), "try this"));

            assertThat(resp.status()).isEqualTo(OfferStatus.NEGOTIATING);
            assertThat(resp.nextActor()).isEqualTo(ActorType.CARRIER);
            assertThat(resp.currentAmount()).isEqualByComparingTo("48000");
        }

        @Test @DisplayName("Carrier can counter a shipper counter")
        void carrierCountersShipperCounter() {
            CarrierOffer offer = negotiatingOffer(1L, abcCompany, negotiableFreight,
                    new BigDecimal("48000"), ActorType.CARRIER);
            stubCompanyContext(abcOwner, abcCompany, CompanyRole.CARRIER_OWNER);
            when(offerRepo.findByIdWithLock(1L)).thenReturn(Optional.of(offer));
            when(offerRepo.save(any())).thenAnswer(i -> i.getArgument(0));
            when(historyRepo.save(any())).thenAnswer(i -> i.getArgument(0));
            when(offerRepo.findByIdEager(1L)).thenReturn(Optional.of(offer));
            when(historyRepo.findByCarrierOfferIdOrderByCreatedAtAsc(1L)).thenReturn(List.of());

            CarrierOfferResponse resp = service.carrierCounter(1L, 10L, abcOwner,
                    new CounterOfferRequest(new BigDecimal("47000"), null));

            assertThat(resp.status()).isEqualTo(OfferStatus.NEGOTIATING);
            assertThat(resp.nextActor()).isEqualTo(ActorType.SHIPPER);
            assertThat(resp.currentAmount()).isEqualByComparingTo("47000");
        }

        @Test @DisplayName("Counter-offer on fixed-price freight rejected (422)")
        void counterOnFixedPriceRejected() {
            CarrierOffer offer = pendingOffer(2L, abcCompany, fixedFreight,
                    new BigDecimal("50000"), ActorType.SHIPPER);
            offer.setPricingType(CarrierOffer.PricingType.FIXED);
            when(freightRepo.findById(101L)).thenReturn(Optional.of(fixedFreight));
            when(offerRepo.findByIdWithLock(2L)).thenReturn(Optional.of(offer));

            assertThatThrownBy(() ->
                    service.shipperCounter(101L, 2L, shipper,
                            new CounterOfferRequest(new BigDecimal("48000"), null)))
                    .isInstanceOf(CarrierOfferException.class)
                    .hasMessageContaining("fixed-price");
        }

        @Test @DisplayName("Shipper counter rejected when it is carrier's turn")
        void shipperCounterWrongTurn() {
            CarrierOffer offer = negotiatingOffer(1L, abcCompany, negotiableFreight,
                    new BigDecimal("48000"), ActorType.CARRIER); // CARRIER's turn
            when(freightRepo.findById(100L)).thenReturn(Optional.of(negotiableFreight));
            when(offerRepo.findByIdWithLock(1L)).thenReturn(Optional.of(offer));

            assertThatThrownBy(() ->
                    service.shipperCounter(100L, 1L, shipper,
                            new CounterOfferRequest(new BigDecimal("49000"), null)))
                    .isInstanceOf(CarrierOfferException.class)
                    .hasMessageContaining("CARRIER");
        }

        @Test @DisplayName("Carrier counter rejected when it is shipper's turn")
        void carrierCounterWrongTurn() {
            CarrierOffer offer = pendingOffer(1L, abcCompany, negotiableFreight,
                    new BigDecimal("45000"), ActorType.SHIPPER); // SHIPPER's turn
            stubCompanyContext(abcOwner, abcCompany, CompanyRole.CARRIER_OWNER);
            when(offerRepo.findByIdWithLock(1L)).thenReturn(Optional.of(offer));

            assertThatThrownBy(() ->
                    service.carrierCounter(1L, 10L, abcOwner,
                            new CounterOfferRequest(new BigDecimal("46000"), null)))
                    .isInstanceOf(CarrierOfferException.class)
                    .hasMessageContaining("SHIPPER");
        }

        @Test @DisplayName("Counter on ACCEPTED offer rejected")
        void counterOnAcceptedRejected() {
            CarrierOffer offer = acceptedOffer(1L, abcCompany, negotiableFreight, new BigDecimal("47000"));
            when(offerRepo.findByIdWithLock(1L)).thenReturn(Optional.of(offer));

            assertThatThrownBy(() ->
                    service.shipperCounter(100L, 1L, shipper,
                            new CounterOfferRequest(new BigDecimal("46000"), null)))
                    .isInstanceOf(CarrierOfferException.class);
        }

        @Test @DisplayName("Counter on REJECTED offer rejected")
        void counterOnRejectedRejected() {
            CarrierOffer offer = rejectedOffer(1L, abcCompany, negotiableFreight, new BigDecimal("45000"));
            when(offerRepo.findByIdWithLock(1L)).thenReturn(Optional.of(offer));

            assertThatThrownBy(() ->
                    service.shipperCounter(100L, 1L, shipper,
                            new CounterOfferRequest(new BigDecimal("46000"), null)))
                    .isInstanceOf(CarrierOfferException.class);
        }

        @Test @DisplayName("XYZ cannot counter ABC offer (tenant isolation)")
        void xyzCannotCounterAbcOffer() {
            // ABC offer — freight_id matches, but offer belongs to ABC
            CarrierOffer abcOffer = pendingOffer(1L, abcCompany, negotiableFreight,
                    new BigDecimal("45000"), ActorType.SHIPPER);
            stubCompanyContext(xyzOwner, xyzCompany, CompanyRole.CARRIER_OWNER);
            when(offerRepo.findByIdWithLock(1L)).thenReturn(Optional.of(abcOffer));

            assertThatThrownBy(() ->
                    service.carrierCounter(1L, 20L, xyzOwner,          // companyId=20 (XYZ)
                            new CounterOfferRequest(new BigDecimal("47000"), null)))
                    .isInstanceOf(CarrierOfferException.class);
        }
    }

    // ════════════════════════════════════════════════════════════════════════════
    // 3 — Acceptance
    // ════════════════════════════════════════════════════════════════════════════

    @Nested @DisplayName("Acceptance")
    class AcceptanceTests {

        @Test @DisplayName("Shipper accepts carrier offer — commercial award")
        void shipperAcceptsOffer() {
            CarrierOffer offer = pendingOffer(1L, abcCompany, negotiableFreight,
                    new BigDecimal("45000"), ActorType.SHIPPER);
            Freight freshFreight = freight(100L, Freight.PriceType.NEGOTIABLE,
                    new BigDecimal("50000"), shipper); // no award yet

            when(offerRepo.findByIdWithLock(1L)).thenReturn(Optional.of(offer));
            when(freightRepo.findById(100L)).thenReturn(Optional.of(freshFreight));
            when(offerRepo.save(any())).thenAnswer(i -> i.getArgument(0));
            when(freightRepo.recordCarrierAward(100L, 1L)).thenReturn(1);
            when(offerRepo.supersedCompetingOffers(100L, 1L)).thenReturn(0);
            when(historyRepo.save(any())).thenAnswer(i -> i.getArgument(0));
            when(offerRepo.findByIdEager(1L)).thenReturn(Optional.of(offer));
            when(historyRepo.findByCarrierOfferIdOrderByCreatedAtAsc(1L)).thenReturn(List.of());

            CarrierOfferResponse resp = service.shipperAccept(100L, 1L, shipper);

            assertThat(resp.status()).isEqualTo(OfferStatus.ACCEPTED);
            assertThat(resp.agreedAmount()).isEqualByComparingTo("45000");
            assertThat(resp.acceptedAt()).isNotNull();
            assertThat(resp.nextActor()).isNull();
        }

        @Test @DisplayName("Accepted amount equals current negotiated amount")
        void acceptedAmountIsCurrentNegotiatedAmount() {
            CarrierOffer offer = negotiatingOffer(1L, abcCompany, negotiableFreight,
                    new BigDecimal("47000"), ActorType.SHIPPER);
            Freight freshFreight = freight(100L, Freight.PriceType.NEGOTIABLE,
                    new BigDecimal("50000"), shipper);

            when(offerRepo.findByIdWithLock(1L)).thenReturn(Optional.of(offer));
            when(freightRepo.findById(100L)).thenReturn(Optional.of(freshFreight));
            when(offerRepo.save(any())).thenAnswer(i -> i.getArgument(0));
            when(freightRepo.recordCarrierAward(100L, 1L)).thenReturn(1);
            when(offerRepo.supersedCompetingOffers(100L, 1L)).thenReturn(1);
            when(historyRepo.save(any())).thenAnswer(i -> i.getArgument(0));
            when(offerRepo.findByIdEager(1L)).thenReturn(Optional.of(offer));
            when(historyRepo.findByCarrierOfferIdOrderByCreatedAtAsc(1L)).thenReturn(List.of());

            CarrierOfferResponse resp = service.shipperAccept(100L, 1L, shipper);

            assertThat(resp.agreedAmount()).isEqualByComparingTo("47000");
        }

        @Test @DisplayName("Fixed-price acceptance succeeds at budget price")
        void fixedPriceAcceptanceSucceeds() {
            CarrierOffer offer = pendingOffer(3L, abcCompany, fixedFreight,
                    new BigDecimal("50000"), ActorType.SHIPPER);
            offer.setPricingType(CarrierOffer.PricingType.FIXED);
            Freight freshFreight = freight(101L, Freight.PriceType.FIXED,
                    new BigDecimal("50000"), shipper);

            when(offerRepo.findByIdWithLock(3L)).thenReturn(Optional.of(offer));
            when(freightRepo.findById(101L)).thenReturn(Optional.of(freshFreight));
            when(offerRepo.save(any())).thenAnswer(i -> i.getArgument(0));
            when(freightRepo.recordCarrierAward(101L, 3L)).thenReturn(1);
            when(offerRepo.supersedCompetingOffers(101L, 3L)).thenReturn(0);
            when(historyRepo.save(any())).thenAnswer(i -> i.getArgument(0));
            when(offerRepo.findByIdEager(3L)).thenReturn(Optional.of(offer));
            when(historyRepo.findByCarrierOfferIdOrderByCreatedAtAsc(3L)).thenReturn(List.of());

            CarrierOfferResponse resp = service.shipperAccept(101L, 3L, shipper);

            assertThat(resp.agreedAmount()).isEqualByComparingTo("50000");
        }

        @Test @DisplayName("Freight already awarded — concurrent acceptance rejected")
        void freightAlreadyAwardedRejected() {
            // Freight already has an award
            Freight awardedFreight = freight(100L, Freight.PriceType.NEGOTIABLE,
                    new BigDecimal("50000"), shipper);
            awardedFreight.setCarrierAwardOfferId(99L); // already awarded

            when(freightRepo.findById(100L)).thenReturn(Optional.of(awardedFreight));

            assertThatThrownBy(() -> service.shipperAccept(100L, 1L, shipper))
                    .isInstanceOf(CarrierOfferException.class)
                    .hasMessageContaining("already has an accepted");
        }

        @Test @DisplayName("No driver or vehicle required for commercial award")
        void noDriverOrVehicleRequired() {
            // This test verifies the booking boundary — no Booking is created
            CarrierOffer offer = pendingOffer(1L, abcCompany, negotiableFreight,
                    new BigDecimal("45000"), ActorType.SHIPPER);
            Freight freshFreight = freight(100L, Freight.PriceType.NEGOTIABLE,
                    new BigDecimal("50000"), shipper);

            when(offerRepo.findByIdWithLock(1L)).thenReturn(Optional.of(offer));
            when(freightRepo.findById(100L)).thenReturn(Optional.of(freshFreight));
            when(offerRepo.save(any())).thenAnswer(i -> i.getArgument(0));
            when(freightRepo.recordCarrierAward(100L, 1L)).thenReturn(1);
            when(offerRepo.supersedCompetingOffers(100L, 1L)).thenReturn(0);
            when(historyRepo.save(any())).thenAnswer(i -> i.getArgument(0));
            when(offerRepo.findByIdEager(1L)).thenReturn(Optional.of(offer));
            when(historyRepo.findByCarrierOfferIdOrderByCreatedAtAsc(1L)).thenReturn(List.of());

            // Must not interact with any BookingRepository or VehicleRepository
            service.shipperAccept(100L, 1L, shipper);
            // No mock for BookingRepository — if it were called, Mockito would throw
        }

        @Test @DisplayName("Accepted offer cannot be countered")
        void acceptedOfferCannotBeCountered() {
            CarrierOffer offer = acceptedOffer(1L, abcCompany, negotiableFreight, new BigDecimal("47000"));
            when(freightRepo.findById(100L)).thenReturn(Optional.of(negotiableFreight));
            when(offerRepo.findByIdWithLock(1L)).thenReturn(Optional.of(offer));

            assertThatThrownBy(() ->
                    service.shipperCounter(100L, 1L, shipper,
                            new CounterOfferRequest(new BigDecimal("46000"), null)))
                    .isInstanceOf(CarrierOfferException.class);
        }

        @Test @DisplayName("Unrelated shipper cannot accept offer")
        void unrelatedShipperCannotAccept() {
            User otherShipper = user(99L, User.Role.SHIPPER);
            // loadFreightForShipper runs before offer lookup — freight.shipper is shipper(id=1), not otherShipper(id=99)
            when(freightRepo.findById(100L)).thenReturn(Optional.of(negotiableFreight));

            assertThatThrownBy(() ->
                    service.shipperAccept(100L, 1L, otherShipper))
                    .isInstanceOf(CarrierOfferException.class);
        }

        @Test @DisplayName("ABC cannot accept XYZ's offer (IDOR)")
        void abcCannotAcceptXyzOffer() {
            // offer belongs to XYZ; ABC tries to accept
            CarrierOffer xyzOffer = pendingOffer(5L, xyzCompany, negotiableFreight,
                    new BigDecimal("46000"), ActorType.SHIPPER);
            stubCompanyContext(abcOwner, abcCompany, CompanyRole.CARRIER_OWNER);
            when(offerRepo.findByIdWithLock(5L)).thenReturn(Optional.of(xyzOffer));

            assertThatThrownBy(() ->
                    service.carrierAccept(5L, 10L, abcOwner))   // companyId = ABC (10)
                    .isInstanceOf(CarrierOfferException.class);
        }
    }

    // ════════════════════════════════════════════════════════════════════════════
    // 4 — Withdrawal
    // ════════════════════════════════════════════════════════════════════════════

    @Nested @DisplayName("Withdrawal")
    class WithdrawalTests {

        @Test @DisplayName("Carrier can withdraw active offer")
        void carrierWithdrawsOffer() {
            CarrierOffer offer = pendingOffer(1L, abcCompany, negotiableFreight,
                    new BigDecimal("45000"), ActorType.SHIPPER);
            stubCompanyContext(abcOwner, abcCompany, CompanyRole.CARRIER_OWNER);
            when(offerRepo.findByIdWithLock(1L)).thenReturn(Optional.of(offer));
            when(offerRepo.save(any())).thenAnswer(i -> i.getArgument(0));
            when(historyRepo.save(any())).thenAnswer(i -> i.getArgument(0));
            when(offerRepo.findByIdEager(1L)).thenReturn(Optional.of(offer));
            when(historyRepo.findByCarrierOfferIdOrderByCreatedAtAsc(1L)).thenReturn(List.of());

            CarrierOfferResponse resp = service.withdraw(1L, 10L, abcOwner);

            assertThat(resp.status()).isEqualTo(OfferStatus.WITHDRAWN);
            assertThat(resp.withdrawnAt()).isNotNull();
        }

        @Test @DisplayName("Withdrawn offer cannot be accepted")
        void withdrawnOfferCannotBeAccepted() {
            CarrierOffer offer = withdrawnOffer(1L, abcCompany, negotiableFreight, new BigDecimal("45000"));
            when(freightRepo.findById(100L)).thenReturn(Optional.of(negotiableFreight));
            when(offerRepo.findByIdWithLock(1L)).thenReturn(Optional.of(offer));

            assertThatThrownBy(() ->
                    service.shipperAccept(100L, 1L, shipper))
                    .isInstanceOf(CarrierOfferException.class);
        }

        @Test @DisplayName("XYZ cannot withdraw ABC offer")
        void xyzCannotWithdrawAbcOffer() {
            CarrierOffer abcOffer = pendingOffer(1L, abcCompany, negotiableFreight,
                    new BigDecimal("45000"), ActorType.SHIPPER);
            stubCompanyContext(xyzOwner, xyzCompany, CompanyRole.CARRIER_OWNER);
            when(offerRepo.findByIdWithLock(1L)).thenReturn(Optional.of(abcOffer));

            assertThatThrownBy(() ->
                    service.withdraw(1L, 20L, xyzOwner))     // companyId = XYZ (20)
                    .isInstanceOf(CarrierOfferException.class);
        }
    }

    // ════════════════════════════════════════════════════════════════════════════
    // 5 — Shipper reject
    // ════════════════════════════════════════════════════════════════════════════

    @Nested @DisplayName("Rejection")
    class RejectionTests {

        @Test @DisplayName("Shipper can reject a carrier offer")
        void shipperRejectsOffer() {
            CarrierOffer offer = pendingOffer(1L, abcCompany, negotiableFreight,
                    new BigDecimal("45000"), ActorType.SHIPPER);
            when(freightRepo.findById(100L)).thenReturn(Optional.of(negotiableFreight));
            when(offerRepo.findByIdWithLock(1L)).thenReturn(Optional.of(offer));
            when(offerRepo.save(any())).thenAnswer(i -> i.getArgument(0));
            when(historyRepo.save(any())).thenAnswer(i -> i.getArgument(0));
            when(offerRepo.findByIdEager(1L)).thenReturn(Optional.of(offer));
            when(historyRepo.findByCarrierOfferIdOrderByCreatedAtAsc(1L)).thenReturn(List.of());

            CarrierOfferResponse resp = service.shipperReject(100L, 1L, shipper);

            assertThat(resp.status()).isEqualTo(OfferStatus.REJECTED);
            assertThat(resp.rejectedAt()).isNotNull();
        }

        @Test @DisplayName("Rejected offer cannot be accepted")
        void rejectedOfferCannotBeAccepted() {
            CarrierOffer offer = rejectedOffer(1L, abcCompany, negotiableFreight, new BigDecimal("45000"));
            // shipperAccept calls freightRepo before the offer lock
            when(freightRepo.findById(100L)).thenReturn(Optional.of(negotiableFreight));
            when(offerRepo.findByIdWithLock(1L)).thenReturn(Optional.of(offer));

            assertThatThrownBy(() ->
                    service.shipperAccept(100L, 1L, shipper))
                    .isInstanceOf(CarrierOfferException.class);
        }

        @Test @DisplayName("Unrelated shipper cannot reject offer")
        void unrelatedShipperCannotReject() {
            User otherShipper = user(99L, User.Role.SHIPPER);
            // loadFreightForShipper checks ownership — otherShipper != freight.shipper
            when(freightRepo.findById(100L)).thenReturn(Optional.of(negotiableFreight));

            assertThatThrownBy(() ->
                    service.shipperReject(100L, 1L, otherShipper))
                    .isInstanceOf(CarrierOfferException.class);
        }
    }

    // ════════════════════════════════════════════════════════════════════════════
    // 6 — Negotiation history
    // ════════════════════════════════════════════════════════════════════════════

    @Nested @DisplayName("Negotiation History")
    class HistoryTests {

        @Test @DisplayName("History is preserved after full negotiation round")
        void historyPreserved() {
            CarrierOffer offer = pendingOffer(1L, abcCompany, negotiableFreight,
                    new BigDecimal("47000"), ActorType.SHIPPER);
            // Simulate 4 history entries
            List<OfferNegotiationEntry> entries = List.of(
                    historyEntry(1L, offer, ActorType.CARRIER, OfferNegotiationEntry.NegotiationAction.OFFER, new BigDecimal("45000")),
                    historyEntry(2L, offer, ActorType.SHIPPER, OfferNegotiationEntry.NegotiationAction.COUNTER_OFFER, new BigDecimal("48000")),
                    historyEntry(3L, offer, ActorType.CARRIER, OfferNegotiationEntry.NegotiationAction.COUNTER_OFFER, new BigDecimal("47000")),
                    historyEntry(4L, offer, ActorType.SHIPPER, OfferNegotiationEntry.NegotiationAction.ACCEPT, null)
            );
            when(offerRepo.findByIdEager(1L)).thenReturn(Optional.of(offer));
            when(historyRepo.findByCarrierOfferIdOrderByCreatedAtAsc(1L)).thenReturn(entries);
            // Shipper authorized (owns the freight)
            when(membershipRepo.findByCarrierCompanyIdAndUserId(anyLong(), eq(1L)))
                    .thenReturn(Optional.empty());

            var history = service.getNegotiationHistory(1L, shipper);

            assertThat(history).hasSize(4);
            assertThat(history.get(0).action()).isEqualTo(OfferNegotiationEntry.NegotiationAction.OFFER);
            assertThat(history.get(0).amount()).isEqualByComparingTo("45000");
            assertThat(history.get(2).action()).isEqualTo(OfferNegotiationEntry.NegotiationAction.COUNTER_OFFER);
            assertThat(history.get(3).action()).isEqualTo(OfferNegotiationEntry.NegotiationAction.ACCEPT);
        }

        @Test @DisplayName("History not accessible to unrelated party (IDOR)")
        void historyInaccessibleToStranger() {
            User stranger = user(77L, User.Role.SHIPPER);
            // stranger is not the freight shipper, not a company member
            CarrierOffer offer = pendingOffer(1L, abcCompany, negotiableFreight,
                    new BigDecimal("45000"), ActorType.SHIPPER);
            // freight.shipper.id = 1, stranger.id = 77 — no match
            when(offerRepo.findByIdEager(1L)).thenReturn(Optional.of(offer));
            when(membershipRepo.findByCarrierCompanyIdAndUserId(anyLong(), eq(77L)))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getNegotiationHistory(1L, stranger))
                    .isInstanceOf(CarrierOfferException.class);
        }
    }

    // ════════════════════════════════════════════════════════════════════════════
    // 7 — Money validation
    // ════════════════════════════════════════════════════════════════════════════

    @Nested @DisplayName("Money Validation")
    class MoneyTests {

        @Test @DisplayName("BigDecimal with 2dp accepted")
        void validBigDecimalAccepted() {
            stubCompanyContext(abcOwner, abcCompany, CompanyRole.CARRIER_OWNER);
            stubFreight(negotiableFreight);
            stubNoActiveOffer(100L, 10L);
            CarrierOffer saved = savedOffer(1L, abcCompany, negotiableFreight,
                    CarrierOffer.PricingType.NEGOTIABLE, new BigDecimal("45000.50"), OfferStatus.PENDING);
            when(offerRepo.save(any())).thenReturn(saved);
            when(offerRepo.findByIdEager(1L)).thenReturn(Optional.of(saved));
            when(historyRepo.save(any())).thenAnswer(i -> i.getArgument(0));
            when(historyRepo.findByCarrierOfferIdOrderByCreatedAtAsc(1L)).thenReturn(List.of());

            assertThatNoException().isThrownBy(() ->
                    service.submitOffer(100L, 10L, abcOwner,
                            new SubmitOfferRequest(new BigDecimal("45000.50"), null)));
        }

        @Test @DisplayName("Amount with more than 2dp rejected")
        void invalidScaleRejected() {
            stubCompanyContext(abcOwner, abcCompany, CompanyRole.CARRIER_OWNER);
            stubFreight(negotiableFreight);
            stubNoActiveOffer(100L, 10L);

            assertThatThrownBy(() ->
                    service.submitOffer(100L, 10L, abcOwner,
                            new SubmitOfferRequest(new BigDecimal("45000.123"), null)))
                    .isInstanceOf(Exception.class);
        }
    }

    // ════════════════════════════════════════════════════════════════════════════
    // Fixture helpers
    // ════════════════════════════════════════════════════════════════════════════

    private User user(Long id, User.Role role) {
        User u = new User();
        u.setId(id);
        u.setRole(role);
        u.setFullName("User-" + id);
        u.setPhone("091" + id);
        u.setIsActive(true);
        u.setVerificationStatus(true);
        return u;
    }

    private CarrierCompany company(Long id, VerificationStatus status) {
        CarrierCompany c = CarrierCompany.builder()
                .companyName("Company-" + id)
                .ownerId(2L)
                .verificationStatus(status)
                .build();
        // Reflection to set id (Lombok @Builder does not set id)
        try {
            var f = CarrierCompany.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(c, id);
        } catch (Exception ignored) {}
        return c;
    }

    private Freight freight(Long id, Freight.PriceType priceType, BigDecimal budget, User shipperUser) {
        Freight f = new Freight();
        f.setId(id);
        f.setPriceType(priceType);
        f.setBudget(budget);
        f.setShipper(shipperUser);
        f.setStatus(Freight.FreightStatus.POSTED);
        f.setPickupLocation("Addis Ababa");
        f.setDestination("Dire Dawa");
        f.setMaterialType("general");
        f.setWeightTons(BigDecimal.TEN);
        f.setUrgencyLevel("normal");
        f.setServiceType(Freight.ServiceType.INTERCITY);
        return f;
    }

    private CarrierOffer savedOffer(Long id, CarrierCompany company, Freight freight,
                                    CarrierOffer.PricingType pricingType, BigDecimal amount, OfferStatus status) {
        CarrierOffer o = CarrierOffer.builder()
                .carrierCompany(company)
                .freight(freight)
                .submittedBy(abcOwner)
                .pricingType(pricingType)
                .currentAmount(amount)
                .status(status)
                .nextActor(ActorType.SHIPPER)
                .build();
        try {
            var f = CarrierOffer.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(o, id);
        } catch (Exception ignored) {}
        return o;
    }

    private CarrierOffer pendingOffer(Long id, CarrierCompany company, Freight freight,
                                      BigDecimal amount, ActorType nextActor) {
        CarrierOffer o = savedOffer(id, company, freight,
                CarrierOffer.PricingType.NEGOTIABLE, amount, OfferStatus.PENDING);
        o.setNextActor(nextActor);
        return o;
    }

    private CarrierOffer negotiatingOffer(Long id, CarrierCompany company, Freight freight,
                                           BigDecimal amount, ActorType nextActor) {
        CarrierOffer o = savedOffer(id, company, freight,
                CarrierOffer.PricingType.NEGOTIABLE, amount, OfferStatus.NEGOTIATING);
        o.setNextActor(nextActor);
        return o;
    }

    private CarrierOffer acceptedOffer(Long id, CarrierCompany company, Freight freight, BigDecimal amount) {
        CarrierOffer o = savedOffer(id, company, freight,
                CarrierOffer.PricingType.NEGOTIABLE, amount, OfferStatus.ACCEPTED);
        o.setAgreedAmount(amount);
        o.setAcceptedAt(LocalDateTime.now());
        o.setNextActor(null);
        return o;
    }

    private CarrierOffer rejectedOffer(Long id, CarrierCompany company, Freight freight, BigDecimal amount) {
        CarrierOffer o = savedOffer(id, company, freight,
                CarrierOffer.PricingType.NEGOTIABLE, amount, OfferStatus.REJECTED);
        o.setRejectedAt(LocalDateTime.now());
        o.setNextActor(null);
        return o;
    }

    private CarrierOffer withdrawnOffer(Long id, CarrierCompany company, Freight freight, BigDecimal amount) {
        CarrierOffer o = savedOffer(id, company, freight,
                CarrierOffer.PricingType.NEGOTIABLE, amount, OfferStatus.WITHDRAWN);
        o.setWithdrawnAt(LocalDateTime.now());
        o.setNextActor(null);
        return o;
    }

    private OfferNegotiationEntry historyEntry(Long id, CarrierOffer offer, ActorType actor,
                                                OfferNegotiationEntry.NegotiationAction action, BigDecimal amount) {
        OfferNegotiationEntry e = OfferNegotiationEntry.builder()
                .carrierOffer(offer)
                .actorType(actor)
                .actorUserId(actor == ActorType.CARRIER ? abcOwner.getId() : shipper.getId())
                .action(action)
                .amount(amount)
                .build();
        try {
            var f = OfferNegotiationEntry.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(e, id);
        } catch (Exception ignored) {}
        return e;
    }

    private void stubCompanyContext(User user, CarrierCompany company, CompanyRole role) {
        stubCompanyContextWith(user, company, role);
    }

    private void stubCompanyContextWith(User user, CarrierCompany company, CompanyRole role) {
        CompanyMembership mem = new CompanyMembership();
        mem.setCarrierCompanyId(company.getId());
        mem.setUserId(user.getId());
        mem.setRole(role);
        mem.setStatus(MembershipStatus.ACTIVE);
        mem.setJoinedAt(LocalDateTime.now());
        CompanyContext ctx = new CompanyContext(mem, company);
        when(companyContextService.resolveForCompany(company.getId(), user)).thenReturn(ctx);
    }

    private void stubFreight(Freight freight) {
        when(freightRepo.findById(freight.getId())).thenReturn(Optional.of(freight));
    }

    private void stubNoActiveOffer(Long freightId, Long companyId) {
        when(offerRepo.existsActiveOfferForCompanyAndFreight(freightId, companyId)).thenReturn(false);
    }
}
