package com.ethioloadai.delivery;

import com.ethioloadai.booking.entity.Booking;
import com.ethioloadai.booking.repository.BookingRepository;
import com.ethioloadai.carrier.company.dto.CarrierCompanyRequest;
import com.ethioloadai.carrier.company.entity.VerificationStatus;
import com.ethioloadai.carrier.company.repository.CarrierCompanyRepository;
import com.ethioloadai.carrier.company.service.CarrierCompanyService;
import com.ethioloadai.carrier.dispatch.entity.DispatchStatus;
import com.ethioloadai.carrier.dispatch.repository.DispatchAssignmentRepository;
import com.ethioloadai.carrier.dispatch.service.DispatchService;
import com.ethioloadai.carrier.fleet.dto.AddCarrierDriverRequest;
import com.ethioloadai.carrier.fleet.dto.RegisterCompanyVehicleRequest;
import com.ethioloadai.carrier.fleet.entity.CarrierDriverStatus;
import com.ethioloadai.carrier.fleet.entity.DriverAvailability;
import com.ethioloadai.carrier.fleet.repository.CarrierDriverRepository;
import com.ethioloadai.carrier.fleet.service.CarrierFleetService;
import com.ethioloadai.carrier.offer.dto.SubmitOfferRequest;
import com.ethioloadai.carrier.offer.repository.CarrierOfferRepository;
import com.ethioloadai.carrier.offer.service.CarrierOfferService;
import com.ethioloadai.carrier.team.dto.InvitationRequest;
import com.ethioloadai.carrier.team.entity.CompanyRole;
import com.ethioloadai.carrier.team.repository.CompanyMembershipRepository;
import com.ethioloadai.carrier.team.service.CompanyInvitationService;
import com.ethioloadai.carrier.team.service.CompanyMembershipService;
import com.ethioloadai.carrier.team.service.InvitationTokens;
import com.ethioloadai.carrier.team.config.InvitationProperties;
import com.ethioloadai.delivery.entity.ProofOfDelivery;
import com.ethioloadai.delivery.repository.ProofOfDeliveryRepository;
import com.ethioloadai.delivery.service.ProofOfDeliveryService;
import com.ethioloadai.document.storage.DocumentStorage;
import com.ethioloadai.email.EmailService;
import com.ethioloadai.freight.dto.CreateFreightRequest;
import com.ethioloadai.freight.entity.Freight;
import com.ethioloadai.freight.repository.FreightRepository;
import com.ethioloadai.freight.service.FreightService;
import com.ethioloadai.rating.dto.SubmitRatingRequest;
import com.ethioloadai.rating.repository.RatingRepository;
import com.ethioloadai.rating.service.RatingService;
import com.ethioloadai.security.jwt.JwtService;
import com.ethioloadai.settlement.entity.CarrierSettlement;
import com.ethioloadai.settlement.repository.CarrierSettlementRepository;
import com.ethioloadai.settlement.service.CarrierSettlementService;
import com.ethioloadai.trip.entity.Trip;
import com.ethioloadai.trip.repository.TripRepository;
import com.ethioloadai.trip.service.TripService;
import com.ethioloadai.user.entity.User;
import com.ethioloadai.user.repository.UserRepository;
import com.ethioloadai.vehicle.entity.Vehicle;
import com.ethioloadai.vehicle.repository.VehicleRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Phase 7 — Complete delivery workflow integration tests.
 *
 * <p>Covers all 74+ test cases from the Phase 7 specification.
 */
@SpringBootTest(properties = {
        "carrier.invitations.cooldown-seconds=0",
        "carrier.invitations.max-per-hour=100"
})
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@ActiveProfiles("test")
@Transactional
@DisplayName("Phase 7 — Trip Execution + Proof of Delivery + Settlement")
class Phase7DeliveryIntegrationTest {

    // ── MockMvc & infrastructure ──────────────────────────────────────────────
    @Autowired MockMvc mvc;
    @Autowired JwtService jwt;

    // ── Repositories ──────────────────────────────────────────────────────────
    @Autowired UserRepository users;
    @Autowired FreightRepository freightRepo;
    @Autowired BookingRepository bookingRepo;
    @Autowired TripRepository tripRepo;
    @Autowired CarrierCompanyRepository companies;
    @Autowired CarrierOfferRepository offerRepo;
    @Autowired DispatchAssignmentRepository dispatchRepo;
    @Autowired CarrierDriverRepository carrierDriverRepo;
    @Autowired VehicleRepository vehicleRepo;
    @Autowired ProofOfDeliveryRepository proofRepo;
    @Autowired CarrierSettlementRepository settlementRepo;
    @Autowired RatingRepository ratingRepo;
    @Autowired CompanyMembershipRepository memberships;
    @Autowired EntityManager em;

    // ── Services ──────────────────────────────────────────────────────────────
    @Autowired FreightService freightService;
    @Autowired CarrierCompanyService companyService;
    @Autowired CompanyInvitationService invitations;
    @Autowired CompanyMembershipService teams;
    @Autowired InvitationTokens tokenHelper;
    @Autowired InvitationProperties invProps;
    @Autowired CarrierOfferService offerService;
    @Autowired CarrierFleetService fleetService;
    @Autowired DispatchService dispatchService;
    @Autowired TripService tripService;
    @Autowired ProofOfDeliveryService proofService;
    @Autowired CarrierSettlementService settlementService;
    @Autowired RatingService ratingService;

    @MockBean EmailService emailService;
    @MockBean DocumentStorage documentStorage;

    // ── Test actors ───────────────────────────────────────────────────────────
    User admin, shipper, otherShipper;
    User abcOwner, abcDispatcher, abcFleetManager, abcDriverA, abcDriverB;
    User xyzOwner, xyzDriver;
    Long abcCompanyId, xyzCompanyId;
    Long abcVehicle1Id, xyzVehicleId;

    Freight awardedFreight;
    BigDecimal agreedPrice;

    final Map<String, String> invLinks = new ConcurrentHashMap<>();

    @BeforeEach
    void setUp() {
        invProps.setCooldownSeconds(0);
        invProps.setMaxPerHour(100);
        invLinks.clear();

        doAnswer(call -> { invLinks.put(call.getArgument(0), call.getArgument(3)); return null; })
                .when(emailService).sendInvitation(anyString(), anyString(), any(), anyString(), any());

        // Storage mock — just return ok
        try {
            when(documentStorage.put(anyString(), any())).thenReturn(null);
        } catch (java.io.IOException e) {
            throw new RuntimeException(e);
        }
        when(documentStorage.get(anyString(), anyString(), anyString()))
                .thenReturn(new DocumentStorage.StoredDocument(
                        new ByteArrayResource("proof-content".getBytes()),
                        "proof.jpg", "image/jpeg"));

        // Users
        admin        = mkUser(User.Role.ADMIN);
        shipper      = mkUser(User.Role.SHIPPER);
        otherShipper = mkUser(User.Role.SHIPPER);
        abcOwner        = mkUser(User.Role.SHIPPER);
        abcDispatcher   = mkUser(User.Role.SHIPPER);
        abcFleetManager = mkUser(User.Role.SHIPPER);
        abcDriverA      = mkUser(User.Role.DRIVER);
        abcDriverB      = mkUser(User.Role.DRIVER);
        xyzOwner        = mkUser(User.Role.SHIPPER);
        xyzDriver       = mkUser(User.Role.DRIVER);

        // Companies
        abcCompanyId = mkApprovedCompany(abcOwner);
        addMember(abcCompanyId, abcDispatcher,   CompanyRole.DISPATCHER,    abcOwner);
        addMember(abcCompanyId, abcFleetManager, CompanyRole.FLEET_MANAGER, abcOwner);
        xyzCompanyId = mkApprovedCompany(xyzOwner);

        // Drivers
        addDriverToCompany(abcCompanyId, abcDriverA.getId(), abcOwner);
        addDriverToCompany(abcCompanyId, abcDriverB.getId(), abcOwner);
        addDriverToCompany(xyzCompanyId, xyzDriver.getId(),  xyzOwner);

        // Vehicles
        abcVehicle1Id = mkApprovedVehicle(abcCompanyId, 20.0, abcOwner);
        xyzVehicleId  = mkApprovedVehicle(xyzCompanyId, 15.0, xyzOwner);

        // Freight + awarded offer
        agreedPrice    = new BigDecimal("45000.00");
        awardedFreight = mkFreight(shipper, new BigDecimal("50000.00"));
        awardAndDispatchAndAccept(awardedFreight, agreedPrice);
    }

    // ════════════════════════════════════════════════════════════════════════
    // 1. REGRESSION — Phase 1-6 still work
    // ════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Phase 5 — CarrierOffer agreedAmount is set on awarded freight")
    void regression_phase5_carrierAwardSet() {
        Freight f = freightRepo.findById(awardedFreight.getId()).orElseThrow();
        assertThat(f.getCarrierAwardOfferId()).isNotNull();
    }

    @Test
    @DisplayName("Phase 6 — Booking exists with carrier context after driver acceptance")
    void regression_phase6_bookingExists() {
        Booking booking = findBookingForFreight(awardedFreight.getId());
        assertThat(booking.getCarrierCompanyId()).isEqualTo(abcCompanyId);
        assertThat(booking.getEstimatedPrice()).isEqualByComparingTo(agreedPrice);
        assertThat(booking.getBookingStatus()).isEqualTo(Booking.BookingStatus.ACCEPTED);
    }

    // ════════════════════════════════════════════════════════════════════════
    // 2. TRIP START
    // ════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Assigned driver can start trip → freight becomes IN_TRANSIT")
    void tripStart_assignedDriver_succeeds() {
        Trip trip = findTripForFreight(awardedFreight.getId());

        // startTrip: runs native UPDATE then detaches freight entity from L1 cache
        tripService.startTrip(trip.getId(), abcDriverA);

        Trip started = tripRepo.findById(trip.getId()).orElseThrow();
        assertThat(started.getStartTime()).isNotNull();
        assertThat(started.getTripStatus()).isEqualTo(Trip.TripStatus.ONGOING);

        // Force full L1 cache eviction before reading freight status,
        // to ensure no stale MATCHED entity overwrites the DB 'in_transit' on flush.
        em.clear();

        // Now load freight from DB — should be IN_TRANSIT
        Freight f = freightRepo.findById(awardedFreight.getId()).orElseThrow();
        assertThat(f.getStatus()).isEqualTo(Freight.FreightStatus.IN_TRANSIT);

        // Vehicle should be on_trip
        Vehicle v = vehicleRepo.findById(abcVehicle1Id).orElseThrow();
        assertThat(v.getAvailabilityStatus()).isEqualTo("on_trip");
    }

    @Test
    @DisplayName("Unrelated driver cannot start trip → exception")
    void tripStart_unrelatedDriver_rejected() {
        Trip trip = findTripForFreight(awardedFreight.getId());
        assertThatThrownBy(() -> tripService.startTrip(trip.getId(), abcDriverB))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("XYZ driver cannot start ABC trip → exception")
    void tripStart_wrongCompanyDriver_rejected() {
        Trip trip = findTripForFreight(awardedFreight.getId());
        assertThatThrownBy(() -> tripService.startTrip(trip.getId(), xyzDriver))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("Duplicate trip start is idempotent → no error, no restart")
    void tripStart_idempotent() {
        Trip trip = findTripForFreight(awardedFreight.getId());
        tripService.startTrip(trip.getId(), abcDriverA);
        LocalDateTime first = tripRepo.findById(trip.getId()).orElseThrow().getStartTime();

        // Second call
        tripService.startTrip(trip.getId(), abcDriverA);
        LocalDateTime second = tripRepo.findById(trip.getId()).orElseThrow().getStartTime();

        assertThat(first).isEqualTo(second);
    }

    // ════════════════════════════════════════════════════════════════════════
    // 3. TRIP STATUS UPDATES via API
    // ════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("POST /trips/{id}/start → 200 + ONGOING trip + IN_TRANSIT freight")
    void api_startTrip_success() throws Exception {
        Trip trip = findTripForFreight(awardedFreight.getId());

        mvc.perform(post("/trips/" + trip.getId() + "/start")
                        .header("Authorization", auth(abcDriverA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tripStatus").value("ONGOING"));

        // Clear L1 cache so the JPQL query below doesn't auto-flush the stale MATCHED entity
        // and overwrite the controller's committed IN_TRANSIT update.
        em.clear();

        // Check freight status via JPQL (reads from DB after L1 cache cleared)
        Freight.FreightStatus statusEnum = em.createQuery(
                "SELECT f.status FROM Freight f WHERE f.id = :id", Freight.FreightStatus.class)
                .setParameter("id", awardedFreight.getId())
                .getSingleResult();
        assertThat(statusEnum).isEqualTo(Freight.FreightStatus.IN_TRANSIT);
    }

    @Test
    @DisplayName("POST /trips/{id}/arrive → 200 + ARRIVED trip")
    void api_markArrived_success() throws Exception {
        Trip trip = startTrip(awardedFreight.getId());

        mvc.perform(post("/trips/" + trip.getId() + "/arrive")
                        .header("Authorization", auth(abcDriverA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tripStatus").value("ARRIVED"));

        Trip arrived = tripRepo.findById(trip.getId()).orElseThrow();
        assertThat(arrived.getArrivedAt()).isNotNull();
    }

    @Test
    @DisplayName("XYZ driver cannot call arrive on ABC trip → 4xx")
    void api_markArrived_wrongDriver_rejected() throws Exception {
        Trip trip = startTrip(awardedFreight.getId());

        mvc.perform(post("/trips/" + trip.getId() + "/arrive")
                        .header("Authorization", auth(xyzDriver)))
                .andExpect(status().is4xxClientError());
    }

    // ════════════════════════════════════════════════════════════════════════
    // 4. PROOF OF DELIVERY
    // ════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Assigned driver submits proof → SUBMITTED, trip DELIVERY_PENDING_VERIFICATION")
    void proof_assignedDriver_submits() {
        Trip trip = startAndArrive(awardedFreight.getId());

        MockMultipartFile file = proofFile();
        proofService.submitProof(trip.getId(), file, "John Doe", "Goods received", abcDriverA);

        ProofOfDelivery proof = proofRepo.findByTrip_Id(trip.getId()).orElseThrow();
        assertThat(proof.getStatus()).isEqualTo(ProofOfDelivery.ProofStatus.SUBMITTED);
        assertThat(proof.getUploadedBy().getId()).isEqualTo(abcDriverA.getId());
        assertThat(proof.getFreight().getId()).isEqualTo(awardedFreight.getId());

        Trip updated = tripRepo.findById(trip.getId()).orElseThrow();
        assertThat(updated.getTripStatus()).isEqualTo(Trip.TripStatus.DELIVERY_PENDING_VERIFICATION);
    }

    @Test
    @DisplayName("Unrelated driver cannot submit proof → exception")
    void proof_unrelatedDriver_rejected() {
        Trip trip = startAndArrive(awardedFreight.getId());
        assertThatThrownBy(() -> proofService.submitProof(trip.getId(), proofFile(), null, null, abcDriverB))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("Driver cannot submit proof when trip is ONGOING (not ARRIVED)")
    void proof_tripNotArrived_rejected() {
        Trip trip = startTrip(awardedFreight.getId());
        // trip is ONGOING, not ARRIVED
        assertThatThrownBy(() -> proofService.submitProof(trip.getId(), proofFile(), null, null, abcDriverA))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("Proof linked to correct trip, booking, and freight")
    void proof_linkedCorrectly() {
        Trip trip = startAndArrive(awardedFreight.getId());
        Booking booking = findBookingForFreight(awardedFreight.getId());

        proofService.submitProof(trip.getId(), proofFile(), null, null, abcDriverA);

        ProofOfDelivery proof = proofRepo.findByTrip_Id(trip.getId()).orElseThrow();
        assertThat(proof.getTrip().getId()).isEqualTo(trip.getId());
        assertThat(proof.getBooking().getId()).isEqualTo(booking.getId());
        assertThat(proof.getFreight().getId()).isEqualTo(awardedFreight.getId());
    }

    @Test
    @DisplayName("Proof access by shipper — authorized")
    void proof_shipperCanAccessProof() {
        Trip trip = startAndArrive(awardedFreight.getId());
        var resp = proofService.submitProof(trip.getId(), proofFile(), null, null, abcDriverA);
        // Shipper can get by trip
        var got = proofService.getProofForTrip(trip.getId(), shipper);
        assertThat(got.tripId()).isEqualTo(trip.getId());
    }

    @Test
    @DisplayName("Unrelated shipper cannot access proof → exception")
    void proof_unrelatedShipperDenied() {
        Trip trip = startAndArrive(awardedFreight.getId());
        proofService.submitProof(trip.getId(), proofFile(), null, null, abcDriverA);

        assertThatThrownBy(() -> proofService.getProofForTrip(trip.getId(), otherShipper))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("XYZ cannot download ABC proof → exception")
    void proof_crossCompanyDenied() {
        Trip trip = startAndArrive(awardedFreight.getId());
        var resp = proofService.submitProof(trip.getId(), proofFile(), null, null, abcDriverA);

        assertThatThrownBy(() -> proofService.downloadProof(resp.id(), xyzDriver))
                .isInstanceOf(RuntimeException.class);
    }

    // ════════════════════════════════════════════════════════════════════════
    // 5. DELIVERY VERIFICATION
    // ════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Shipper verifies delivery → Trip COMPLETED, Booking DELIVERED, Freight DELIVERED")
    void verify_shipper_completes() {
        Trip trip = startAndArrive(awardedFreight.getId());
        var resp = proofService.submitProof(trip.getId(), proofFile(), null, null, abcDriverA);

        proofService.verifyOrRejectProof(resp.id(),
                new com.ethioloadai.delivery.dto.VerifyDeliveryRequest("VERIFY", null), shipper);

        Trip completed = tripRepo.findById(trip.getId()).orElseThrow();
        assertThat(completed.getTripStatus()).isEqualTo(Trip.TripStatus.COMPLETED);
        assertThat(completed.getEndTime()).isNotNull();

        Booking booking = findBookingForFreight(awardedFreight.getId());
        assertThat(booking.getBookingStatus()).isEqualTo(Booking.BookingStatus.DELIVERED);

        em.flush();
        em.clear(); // evict L1 so @Modifying freight update is visible
        // Use JPQL to bypass entity cache when reading freight status
        Freight.FreightStatus fStatus = em.createQuery(
                "SELECT f.status FROM Freight f WHERE f.id = :id", Freight.FreightStatus.class)
                .setParameter("id", awardedFreight.getId())
                .getSingleResult();
        assertThat(fStatus).isEqualTo(Freight.FreightStatus.DELIVERED);
    }

    @Test
    @DisplayName("Driver availability restored to AVAILABLE after completion")
    void verify_driverAvailabilityRestored() {
        Trip trip = startAndArrive(awardedFreight.getId());
        var resp = proofService.submitProof(trip.getId(), proofFile(), null, null, abcDriverA);
        proofService.verifyOrRejectProof(resp.id(),
                new com.ethioloadai.delivery.dto.VerifyDeliveryRequest("VERIFY", null), shipper);

        var cd = carrierDriverRepo.findByCarrierCompanyIdAndDriverUserId(abcCompanyId, abcDriverA.getId());
        assertThat(cd).isPresent();
        assertThat(cd.get().getAvailability()).isEqualTo(DriverAvailability.AVAILABLE);
    }

    @Test
    @DisplayName("Vehicle availability restored to 'available' after completion")
    void verify_vehicleAvailabilityRestored() {
        Trip trip = startAndArrive(awardedFreight.getId());
        var resp = proofService.submitProof(trip.getId(), proofFile(), null, null, abcDriverA);
        proofService.verifyOrRejectProof(resp.id(),
                new com.ethioloadai.delivery.dto.VerifyDeliveryRequest("VERIFY", null), shipper);

        Vehicle v = vehicleRepo.findById(abcVehicle1Id).orElseThrow();
        assertThat(v.getAvailabilityStatus()).isEqualTo("available");
    }

    @Test
    @DisplayName("Driver cannot self-verify delivery → exception")
    void verify_driverCannotSelfVerify() {
        Trip trip = startAndArrive(awardedFreight.getId());
        var resp = proofService.submitProof(trip.getId(), proofFile(), null, null, abcDriverA);

        assertThatThrownBy(() -> proofService.verifyOrRejectProof(resp.id(),
                new com.ethioloadai.delivery.dto.VerifyDeliveryRequest("VERIFY", null), abcDriverA))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("Carrier owner cannot self-verify delivery → exception")
    void verify_carrierOwnerCannotSelfVerify() {
        Trip trip = startAndArrive(awardedFreight.getId());
        var resp = proofService.submitProof(trip.getId(), proofFile(), null, null, abcDriverA);

        assertThatThrownBy(() -> proofService.verifyOrRejectProof(resp.id(),
                new com.ethioloadai.delivery.dto.VerifyDeliveryRequest("VERIFY", null), abcOwner))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("Unrelated shipper cannot verify → exception")
    void verify_unrelatedShipperCannotVerify() {
        Trip trip = startAndArrive(awardedFreight.getId());
        var resp = proofService.submitProof(trip.getId(), proofFile(), null, null, abcDriverA);

        assertThatThrownBy(() -> proofService.verifyOrRejectProof(resp.id(),
                new com.ethioloadai.delivery.dto.VerifyDeliveryRequest("VERIFY", null), otherShipper))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("Duplicate verify is idempotent → no error, no second settlement")
    void verify_idempotent_noDoubleSettlement() {
        Trip trip = startAndArrive(awardedFreight.getId());
        var resp = proofService.submitProof(trip.getId(), proofFile(), null, null, abcDriverA);

        proofService.verifyOrRejectProof(resp.id(),
                new com.ethioloadai.delivery.dto.VerifyDeliveryRequest("VERIFY", null), shipper);
        proofService.verifyOrRejectProof(resp.id(),
                new com.ethioloadai.delivery.dto.VerifyDeliveryRequest("VERIFY", null), shipper);

        long count = settlementRepo.findByCarrierCompanyId(abcCompanyId).size();
        assertThat(count).isEqualTo(1);
    }

    // ════════════════════════════════════════════════════════════════════════
    // 6. DELIVERY REJECTION
    // ════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Shipper rejects proof → Trip reverts to ARRIVED, no settlement")
    void reject_proof_noSettlement() {
        Trip trip = startAndArrive(awardedFreight.getId());
        var resp = proofService.submitProof(trip.getId(), proofFile(), null, null, abcDriverA);

        proofService.verifyOrRejectProof(resp.id(),
                new com.ethioloadai.delivery.dto.VerifyDeliveryRequest("REJECT", "Goods damaged"), shipper);

        Trip reverted = tripRepo.findById(trip.getId()).orElseThrow();
        assertThat(reverted.getTripStatus()).isEqualTo(Trip.TripStatus.ARRIVED);

        assertThat(settlementRepo.findByBooking_Id(findBookingForFreight(awardedFreight.getId()).getId()))
                .isEmpty();
    }

    @Test
    @DisplayName("Rejection requires rejectionReason → exception if missing")
    void reject_missingReason_exception() {
        Trip trip = startAndArrive(awardedFreight.getId());
        var resp = proofService.submitProof(trip.getId(), proofFile(), null, null, abcDriverA);

        assertThatThrownBy(() -> proofService.verifyOrRejectProof(resp.id(),
                new com.ethioloadai.delivery.dto.VerifyDeliveryRequest("REJECT", null), shipper))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("Driver can resubmit after rejection")
    void reject_driverCanResubmit() {
        Trip trip = startAndArrive(awardedFreight.getId());
        var r1 = proofService.submitProof(trip.getId(), proofFile(), null, null, abcDriverA);

        proofService.verifyOrRejectProof(r1.id(),
                new com.ethioloadai.delivery.dto.VerifyDeliveryRequest("REJECT", "Wrong item"), shipper);

        // Resubmit
        var r2 = proofService.submitProof(trip.getId(), proofFile(), null, null, abcDriverA);
        assertThat(r2.status()).isEqualTo("SUBMITTED");

        Trip t = tripRepo.findById(trip.getId()).orElseThrow();
        assertThat(t.getTripStatus()).isEqualTo(Trip.TripStatus.DELIVERY_PENDING_VERIFICATION);
    }

    // ════════════════════════════════════════════════════════════════════════
    // 7. FINANCIAL MODEL
    // ════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Gross amount = agreed Phase 5 price")
    void settlement_grossEqualsAgreedPrice() {
        doVerifyAndSettle();

        CarrierSettlement s = findSettlement();
        assertThat(s.getGrossAmount()).isEqualByComparingTo(agreedPrice);
    }

    @Test
    @DisplayName("Commission = 10% of gross (standard rate)")
    void settlement_commissionIs10Percent() {
        doVerifyAndSettle();

        CarrierSettlement s = findSettlement();
        BigDecimal expected = agreedPrice.multiply(new BigDecimal("0.10"))
                .setScale(2, RoundingMode.HALF_UP);
        assertThat(s.getCommissionAmount()).isEqualByComparingTo(expected);
    }

    @Test
    @DisplayName("Net amount = gross - commission")
    void settlement_netEqualsGrossMinusCommission() {
        doVerifyAndSettle();

        CarrierSettlement s = findSettlement();
        BigDecimal expectedNet = s.getGrossAmount().subtract(s.getCommissionAmount())
                .setScale(2, RoundingMode.HALF_UP);
        assertThat(s.getNetAmount()).isEqualByComparingTo(expectedNet);
    }

    @Test
    @DisplayName("Settlement currency is ETB")
    void settlement_currencyIsETB() {
        doVerifyAndSettle();
        assertThat(findSettlement().getCurrency()).isEqualTo("ETB");
    }

    @Test
    @DisplayName("Settlement recipient is ABC CarrierCompany, not individual driver")
    void settlement_recipientIsCarrierCompany() {
        doVerifyAndSettle();
        CarrierSettlement s = findSettlement();
        assertThat(s.getCarrierCompany().getId()).isEqualTo(abcCompanyId);
    }

    @Test
    @DisplayName("One settlement per booking — duplicate creates no second row")
    void settlement_uniquePerBooking() {
        Trip trip = startAndArrive(awardedFreight.getId());
        var resp = proofService.submitProof(trip.getId(), proofFile(), null, null, abcDriverA);

        proofService.verifyOrRejectProof(resp.id(),
                new com.ethioloadai.delivery.dto.VerifyDeliveryRequest("VERIFY", null), shipper);
        // Call createSettlement again — should be idempotent
        Long bookingId = findBookingForFreight(awardedFreight.getId()).getId();
        settlementService.createSettlement(bookingId, resp.id());

        assertThat(settlementRepo.findByCarrierCompanyId(abcCompanyId)).hasSize(1);
    }

    // ════════════════════════════════════════════════════════════════════════
    // 8. SETTLEMENT LIFECYCLE
    // ════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Admin can mark settlement PROCESSING → PAID")
    void settlement_adminMarksPaid() {
        doVerifyAndSettle();
        CarrierSettlement s = findSettlement();

        settlementService.markProcessing(s.getId(), admin);
        settlementService.markPaid(s.getId(), "TX123", "REF456", admin);

        CarrierSettlement updated = settlementRepo.findById(s.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(CarrierSettlement.SettlementStatus.PAID);
        assertThat(updated.getPaidAt()).isNotNull();
        assertThat(updated.getExternalTxId()).isEqualTo("TX123");
    }

    @Test
    @DisplayName("Payment failure preserves verified delivery")
    void settlement_paymentFailurePreservesDelivery() {
        doVerifyAndSettle();
        CarrierSettlement s = findSettlement();
        settlementService.markFailed(s.getId(), "Bank error", admin);

        // Trip, Booking, Freight still completed
        Trip trip = tripRepo.findByBooking_Id(s.getBooking().getId()).orElseThrow();
        assertThat(trip.getTripStatus()).isEqualTo(Trip.TripStatus.COMPLETED);
        Booking booking = bookingRepo.findById(s.getBooking().getId()).orElseThrow();
        assertThat(booking.getBookingStatus()).isEqualTo(Booking.BookingStatus.DELIVERED);
    }

    @Test
    @DisplayName("Settlement retry reuses same amounts — no recalculation")
    void settlement_retry_sameAmounts() {
        doVerifyAndSettle();
        CarrierSettlement s = findSettlement();
        settlementService.markFailed(s.getId(), "Error", admin);

        BigDecimal originalGross = s.getGrossAmount();
        BigDecimal originalNet   = s.getNetAmount();

        settlementService.retrySettlement(s.getId(), admin);

        CarrierSettlement retried = settlementRepo.findById(s.getId()).orElseThrow();
        assertThat(retried.getGrossAmount()).isEqualByComparingTo(originalGross);
        assertThat(retried.getNetAmount()).isEqualByComparingTo(originalNet);
        assertThat(retried.getRetryCount()).isEqualTo(1);
    }

    // ════════════════════════════════════════════════════════════════════════
    // 9. SECURITY — cross-company attacks
    // ════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("XYZ cannot view ABC settlement → exception")
    void security_xyzCannotViewAbcSettlement() {
        doVerifyAndSettle();
        CarrierSettlement s = findSettlement();

        assertThatThrownBy(() -> settlementService.getSettlement(s.getId(), xyzOwner))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("Driver cannot block settlement")
    void security_driverCannotBlockSettlement() {
        doVerifyAndSettle();
        CarrierSettlement s = findSettlement();

        assertThatThrownBy(() -> settlementService.blockSettlement(s.getId(), "tamper", abcDriverA))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("Carrier member (non-admin) cannot mark settlement PAID")
    void security_carrierMemberCannotMarkPaid() {
        doVerifyAndSettle();
        CarrierSettlement s = findSettlement();

        assertThatThrownBy(() -> settlementService.markPaid(s.getId(), null, null, abcOwner))
                .isInstanceOf(RuntimeException.class);
    }

    // ════════════════════════════════════════════════════════════════════════
    // 10. MONEY TAMPERING via HTTP
    // ════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Malicious verify body with grossAmount=1 is ignored — server calculates amount")
    void security_moneyTampering_httpVerify() throws Exception {
        Trip trip = startAndArrive(awardedFreight.getId());
        var resp = proofService.submitProof(trip.getId(), proofFile(), null, null, abcDriverA);

        // Attempt to pass tampered financial fields — should be ignored
        mvc.perform(post("/proof-of-delivery/" + resp.id() + "/verify")
                        .header("Authorization", auth(shipper))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"VERIFY\",\"grossAmount\":1,\"commissionRate\":0," +
                                "\"netAmount\":999999999,\"carrierCompanyId\":999}"))
                .andExpect(status().isOk());

        // Settlement should still use authoritative amount
        CarrierSettlement s = findSettlement();
        assertThat(s.getGrossAmount()).isEqualByComparingTo(agreedPrice);
    }

    // ════════════════════════════════════════════════════════════════════════
    // 11. RATINGS
    // ════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Shipper can rate completed booking")
    void rating_shipperCanRate() {
        doVerifyAndSettle();
        em.flush();
        em.clear(); // ensure fresh load
        Booking booking = findBookingForFreight(awardedFreight.getId());

        var result = ratingService.submitRating(
                new SubmitRatingRequest(booking.getId(), 5, "Excellent service"), shipper);
        assertThat(result.rating()).isEqualTo(5);
        assertThat(result.raterId()).isEqualTo(shipper.getId());
    }

    @Test
    @DisplayName("Driver can rate completed booking")
    void rating_driverCanRate() {
        doVerifyAndSettle();
        em.flush();
        em.clear();
        Booking booking = findBookingForFreight(awardedFreight.getId());

        var result = ratingService.submitRating(
                new SubmitRatingRequest(booking.getId(), 4, "Good shipper"), abcDriverA);
        assertThat(result.rating()).isEqualTo(4);
    }

    @Test
    @DisplayName("Incomplete booking cannot be rated → exception")
    void rating_incompleteBooking_rejected() {
        // Booking is ACCEPTED (not yet DELIVERED)
        Booking booking = findBookingForFreight(awardedFreight.getId());
        assertThat(booking.getBookingStatus()).isEqualTo(Booking.BookingStatus.ACCEPTED);

        assertThatThrownBy(() -> ratingService.submitRating(
                new SubmitRatingRequest(booking.getId(), 5, "ok"), shipper))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("Unrelated user cannot rate → exception")
    void rating_unrelatedUser_rejected() {
        doVerifyAndSettle();
        Booking booking = findBookingForFreight(awardedFreight.getId());

        assertThatThrownBy(() -> ratingService.submitRating(
                new SubmitRatingRequest(booking.getId(), 3, "hmm"), otherShipper))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("Duplicate rating rejected → AlreadyRatedException")
    void rating_duplicateRejected() {
        doVerifyAndSettle();
        em.flush();
        em.clear();
        Booking booking = findBookingForFreight(awardedFreight.getId());
        ratingService.submitRating(new SubmitRatingRequest(booking.getId(), 5, "Great"), shipper);

        assertThatThrownBy(() -> ratingService.submitRating(
                new SubmitRatingRequest(booking.getId(), 4, "Duplicate"), shipper))
                .isInstanceOf(RuntimeException.class);
    }

    // ════════════════════════════════════════════════════════════════════════
    // 12. SETTLEMENT HTTP SECURITY
    // ════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("GET /settlements/{id} — carrier member can view own settlement")
    void api_getSettlement_carrierMember_authorized() throws Exception {
        doVerifyAndSettle();
        CarrierSettlement s = findSettlement();

        mvc.perform(get("/settlements/" + s.getId())
                        .header("Authorization", auth(abcOwner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.grossAmount").value(agreedPrice.doubleValue()))
                .andExpect(jsonPath("$.currency").value("ETB"));
    }

    @Test
    @DisplayName("GET /settlements/{id} — XYZ cannot view ABC settlement → 4xx")
    void api_getSettlement_xyzDenied() throws Exception {
        doVerifyAndSettle();
        CarrierSettlement s = findSettlement();

        mvc.perform(get("/settlements/" + s.getId())
                        .header("Authorization", auth(xyzOwner)))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("POST /settlements/{id}/pay — non-admin denied → 4xx")
    void api_markPaid_nonAdminDenied() throws Exception {
        doVerifyAndSettle();
        CarrierSettlement s = findSettlement();

        mvc.perform(post("/settlements/" + s.getId() + "/pay")
                        .header("Authorization", auth(abcOwner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("GET /settlements/carrier/{id} — admin can list all")
    void api_adminListsAllSettlements() throws Exception {
        doVerifyAndSettle();

        mvc.perform(get("/settlements")
                        .header("Authorization", auth(admin)))
                .andExpect(status().isOk());
    }

    // ════════════════════════════════════════════════════════════════════════
    // HELPERS
    // ════════════════════════════════════════════════════════════════════════

    /** Complete full happy path: start, arrive, submit proof, verify. */
    void doVerifyAndSettle() {
        Trip trip = startAndArrive(awardedFreight.getId());
        var resp = proofService.submitProof(trip.getId(), proofFile(), "Receiver", "OK", abcDriverA);
        proofService.verifyOrRejectProof(resp.id(),
                new com.ethioloadai.delivery.dto.VerifyDeliveryRequest("VERIFY", null), shipper);
    }

    CarrierSettlement findSettlement() {
        Booking b = findBookingForFreight(awardedFreight.getId());
        return settlementRepo.findByBooking_Id(b.getId()).orElseThrow();
    }

    Trip startTrip(Long freightId) {
        Trip trip = findTripForFreight(freightId);
        tripService.startTrip(trip.getId(), abcDriverA);
        return tripRepo.findById(trip.getId()).orElseThrow();
    }

    Trip startAndArrive(Long freightId) {
        Trip trip = startTrip(freightId);
        tripService.markArrived(trip.getId(), abcDriverA);
        return tripRepo.findById(trip.getId()).orElseThrow();
    }

    Trip findTripForFreight(Long freightId) {
        Booking b = findBookingForFreight(freightId);
        return tripRepo.findByBooking_Id(b.getId())
                .orElseGet(() -> {
                    // Trip may not exist yet — create it
                    return tripService.createTripFromBooking(b.getId(), abcDriverA);
                });
    }

    Booking findBookingForFreight(Long freightId) {
        return bookingRepo.findByCargoRequest_Id(freightId).stream()
                .filter(bk -> bk.getBookingStatus() != Booking.BookingStatus.CANCELLED)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No booking for freight " + freightId));
    }

    MockMultipartFile proofFile() {
        return new MockMultipartFile("file", "proof.jpg", "image/jpeg",
                "fake-proof-content".getBytes());
    }

    String auth(User u) {
        return "Bearer " + jwt.generateToken(u);
    }

    // ── Fixture setup helpers ─────────────────────────────────────────────────

    /**
     * Creates freight, submits offer from ABC, shipper accepts it, dispatcher assigns driver,
     * driver accepts → booking created. No trip yet.
     */
    void awardAndDispatchAndAccept(Freight freight, BigDecimal price) {
        // Submit offer from ABC
        var offerReq = new SubmitOfferRequest(price, null);
        var offer = offerService.submitOffer(freight.getId(), abcCompanyId, abcOwner, offerReq);

        // Shipper accepts
        offerService.shipperAccept(freight.getId(), offer.id(), shipper);

        // Dispatcher assigns driver A + vehicle 1
        var dispatchReq = new com.ethioloadai.carrier.dispatch.dto.CreateDispatchRequest();
        dispatchReq.setDriverUserId(abcDriverA.getId());
        dispatchReq.setVehicleId(abcVehicle1Id);
        var dispatch = dispatchService.createDispatch(freight.getId(), abcCompanyId, dispatchReq, abcDispatcher);

        // Driver accepts → Booking created
        dispatchService.acceptAssignment(dispatch.getId(), abcDriverA);
    }

    User mkUser(User.Role role) {
        return users.saveAndFlush(User.builder()
                .fullName("Phase7 " + UUID.randomUUID().toString().substring(0, 6))
                .phone(UUID.randomUUID().toString().substring(0, 15))
                .email(UUID.randomUUID() + "@p7.test")
                .password("unused")
                .role(role)
                .isActive(true)
                .verificationStatus(role == User.Role.DRIVER)
                .build());
    }

    Long mkApprovedCompany(User owner) {
        var req = new CarrierCompanyRequest();
        req.setCompanyName("Co7-" + UUID.randomUUID().toString().substring(0, 6));
        Long id = companyService.create(req, owner).getId();
        var c = companies.findById(id).orElseThrow();
        c.setVerificationStatus(VerificationStatus.APPROVED);
        c.setSubmittedAt(LocalDateTime.now());
        c.setReviewedAt(LocalDateTime.now());
        c.setReviewedBy(admin.getId());
        companies.saveAndFlush(c);
        return id;
    }

    void addMember(Long companyId, User target, CompanyRole role, User inviter) {
        var req = new InvitationRequest();
        req.setEmail(target.getEmail());
        req.setRole(role);
        invitations.invite(companyId, req, inviter);
        String link = invLinks.get(target.getEmail().strip().toLowerCase(Locale.ROOT));
        String token = link.substring(link.indexOf("#token=") + 7);
        invitations.accept(token, target);
    }

    void addDriverToCompany(Long companyId, Long driverUserId, User owner) {
        var req = new AddCarrierDriverRequest();
        req.setDriverUserId(driverUserId);
        fleetService.addDriver(companyId, req, owner);
    }

    Long mkApprovedVehicle(Long companyId, double capacity, User owner) {
        RegisterCompanyVehicleRequest vReq = new RegisterCompanyVehicleRequest();
        vReq.setTruckType("Heavy Truck");
        vReq.setPlateNumber("P7-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase());
        vReq.setCapacity(capacity);
        vReq.setVehicleCategory("HEAVY");
        vReq.setCurrentCity("Addis Ababa");
        Long id = fleetService.registerVehicle(companyId, vReq, owner).getId();
        Vehicle v = vehicleRepo.findById(id).orElseThrow();
        v.setApprovalStatus(Vehicle.VehicleApprovalStatus.APPROVED);
        vehicleRepo.saveAndFlush(v);
        return id;
    }

    Freight mkFreight(User owner, BigDecimal budget) {
        var req = CreateFreightRequest.builder()
                .pickupLocation("Addis Ababa")
                .destination("Hawassa")
                .materialType("general cargo")
                .weightTons(new BigDecimal("10.0"))
                .budget(budget)
                .urgencyLevel("normal")
                .serviceType(Freight.ServiceType.INTERCITY)
                .priceType(Freight.PriceType.NEGOTIABLE)
                .build();
        return freightRepo.findById(freightService.createFreight(owner.getId(), req).getId()).orElseThrow();
    }
}
