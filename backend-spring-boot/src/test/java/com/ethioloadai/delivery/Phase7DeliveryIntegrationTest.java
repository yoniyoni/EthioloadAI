package com.ethioloadai.delivery;

import com.ethioloadai.booking.entity.Booking;
import com.ethioloadai.booking.repository.BookingRepository;
import com.ethioloadai.bid.service.CommissionService;
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
import com.ethioloadai.settlement.entity.SettlementOutboxRecord;
import com.ethioloadai.settlement.repository.SettlementOutboxRepository;
import com.ethioloadai.settlement.service.CarrierSettlementService;
import com.ethioloadai.settlement.service.SettlementOutboxScheduler;
import com.ethioloadai.settlement.service.SettlementOutboxWorker;
import com.ethioloadai.trip.entity.Trip;
import com.ethioloadai.trip.entity.TripStop;
import com.ethioloadai.trip.exception.TripStopValidationException;
import com.ethioloadai.trip.exception.TripValidationException;
import com.ethioloadai.trip.repository.TripRepository;
import com.ethioloadai.trip.repository.TripStopRepository;
import com.ethioloadai.trip.service.TripService;
import com.ethioloadai.trip.service.TripStopService;
import com.ethioloadai.trip.service.TripStatusTransitionService;
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
import org.springframework.scheduling.config.ScheduledTaskHolder;
import org.springframework.scheduling.support.ScheduledMethodRunnable;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.boot.test.mock.mockito.SpyBean;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

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
        "carrier.invitations.max-per-hour=100",
        "settlement.outbox.poll-interval-ms=3600000",
        "trip.scheduling.enabled=false",
        "spring.datasource.url=jdbc:h2:mem:phase7-delivery-test;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;MODE=PostgreSQL;NON_KEYWORDS=VALUE"
})
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@ActiveProfiles("test")
@Transactional
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
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
    @Autowired TripStopRepository tripStopRepo;
    @Autowired CarrierCompanyRepository companies;
    @Autowired CarrierOfferRepository offerRepo;
    @Autowired DispatchAssignmentRepository dispatchRepo;
    @Autowired CarrierDriverRepository carrierDriverRepo;
    @Autowired VehicleRepository vehicleRepo;
    @Autowired ProofOfDeliveryRepository proofRepo;
    @Autowired CarrierSettlementRepository settlementRepo;
    @Autowired SettlementOutboxRepository settlementOutboxRepo;
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
    @Autowired TripStopService tripStopService;
    @Autowired TripStatusTransitionService tripStatusTransitionService;
    @Autowired ProofOfDeliveryService proofService;
    @Autowired CarrierSettlementService settlementService;
    @Autowired SettlementOutboxScheduler settlementOutboxScheduler;
    @Autowired SettlementOutboxWorker settlementOutboxWorker;
    @Autowired ScheduledTaskHolder scheduledTaskHolder;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired RatingService ratingService;

    @MockBean EmailService emailService;
    @MockBean DocumentStorage documentStorage;
    @SpyBean CommissionService commissionService;

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

    @Test
    @DisplayName("Shipper confirms an arrived trip without requiring proof")
    void shipperConfirmsArrivedDelivery() throws Exception {
        Trip trip = startAndArrive(awardedFreight.getId());
        assertThat(freightRepo.findById(awardedFreight.getId()).orElseThrow()
                .getShipper().getId()).isEqualTo(shipper.getId());

        mvc.perform(post("/trips/" + trip.getId() + "/confirm-delivery")
                        .header("Authorization", auth(shipper)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tripStatus").value("COMPLETED"))
                .andExpect(jsonPath("$.shipperDeliveryConfirmedBy").value(shipper.getId()))
                .andExpect(jsonPath("$.shipperDeliveryConfirmedAt").isNotEmpty());

        em.flush();
        em.clear();
        Trip completed = tripRepo.findById(trip.getId()).orElseThrow();
        assertThat(completed.getTripStatus()).isEqualTo(Trip.TripStatus.COMPLETED);
        assertThat(completed.getShipperDeliveryConfirmedBy().getId()).isEqualTo(shipper.getId());
        assertThat(completed.getShipperDeliveryConfirmedAt()).isNotNull();
        assertThat(findBookingForFreight(awardedFreight.getId()).getBookingStatus())
                .isEqualTo(Booking.BookingStatus.DELIVERED);
        assertThat(em.createQuery("SELECT f.status FROM Freight f WHERE f.id = :id",
                        Freight.FreightStatus.class)
                .setParameter("id", awardedFreight.getId())
                .getSingleResult()).isEqualTo(Freight.FreightStatus.DELIVERED);
        assertThat(em.createQuery("SELECT COUNT(p) FROM Payment p WHERE p.booking.id = :bookingId",
                        Long.class)
                .setParameter("bookingId", findBookingForFreight(awardedFreight.getId()).getId())
                .getSingleResult()).isZero();
        assertThat(settlementRepo.findByBooking_Id(findBookingForFreight(awardedFreight.getId()).getId()))
                .isEmpty();
        assertThat(settlementOutboxRepo.findAll())
                .noneMatch(item -> item.getBookingId().equals(
                        findBookingForFreight(awardedFreight.getId()).getId()));
        assertThat(proofRepo.findByTrip_Id(trip.getId())).isEmpty();
        assertThat(carrierDriverRepo.findByCarrierCompanyIdAndDriverUserId(
                abcCompanyId, abcDriverA.getId()).orElseThrow().getAvailability())
                .isEqualTo(DriverAvailability.AVAILABLE);
        assertThat(vehicleRepo.findById(abcVehicle1Id).orElseThrow().getAvailabilityStatus())
                .isEqualTo("available");

        LocalDateTime confirmationTime = completed.getShipperDeliveryConfirmedAt();
        mvc.perform(post("/trips/" + trip.getId() + "/confirm-delivery")
                        .header("Authorization", auth(shipper)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shipperDeliveryConfirmedAt").value(
                        confirmationTime.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"))));
        assertThat(tripRepo.findById(trip.getId()).orElseThrow().getShipperDeliveryConfirmedAt())
                .isEqualTo(confirmationTime);
    }

    @Test
    @DisplayName("Only the freight's shipper may confirm an arrived delivery")
    void deliveryConfirmationRejectsWrongShipper() throws Exception {
        Trip trip = startAndArrive(awardedFreight.getId());

        mvc.perform(post("/trips/" + trip.getId() + "/confirm-delivery")
                        .header("Authorization", auth(otherShipper)))
                .andExpect(status().isForbidden());

        assertThat(tripRepo.findById(trip.getId()).orElseThrow().getTripStatus())
                .isEqualTo(Trip.TripStatus.ARRIVED);
        assertThat(tripRepo.findById(trip.getId()).orElseThrow().getShipperDeliveryConfirmedAt())
                .isNull();
    }

    @Test
    @DisplayName("Drivers and admins cannot use shipper delivery confirmation")
    void deliveryConfirmationRejectsDriverAndAdmin() throws Exception {
        Trip trip = startAndArrive(awardedFreight.getId());

        mvc.perform(post("/trips/" + trip.getId() + "/confirm-delivery")
                        .header("Authorization", auth(abcDriverA)))
                .andExpect(status().isForbidden());
        mvc.perform(post("/trips/" + trip.getId() + "/confirm-delivery")
                        .header("Authorization", auth(admin)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Delivery confirmation requires arrival and eligible booking and freight states")
    void deliveryConfirmationRejectsInvalidStates() throws Exception {
        Trip trip = startTrip(awardedFreight.getId());
        mvc.perform(post("/trips/" + trip.getId() + "/confirm-delivery")
                        .header("Authorization", auth(shipper)))
                .andExpect(status().isBadRequest());

        trip = startAndArrive(awardedFreight.getId());
        Booking booking = findBookingForFreight(awardedFreight.getId());
        booking.setBookingStatus(Booking.BookingStatus.PENDING);
        bookingRepo.saveAndFlush(booking);
        mvc.perform(post("/trips/" + trip.getId() + "/confirm-delivery")
                        .header("Authorization", auth(shipper)))
                .andExpect(status().isBadRequest());
        assertThat(tripRepo.findById(trip.getId()).orElseThrow().getTripStatus())
                .isEqualTo(Trip.TripStatus.ARRIVED);

        booking.setBookingStatus(Booking.BookingStatus.ACCEPTED);
        bookingRepo.saveAndFlush(booking);
        Freight freight = freightRepo.findById(awardedFreight.getId()).orElseThrow();
        freight.setStatus(Freight.FreightStatus.MATCHED);
        freightRepo.saveAndFlush(freight);
        mvc.perform(post("/trips/" + trip.getId() + "/confirm-delivery")
                        .header("Authorization", auth(shipper)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Multi-stop delivery remains active until final destination arrival and shipper confirmation")
    void multiStopDeliveryWaitsForShipperConfirmation() throws Exception {
        Trip trip = startTrip(awardedFreight.getId());
        trip.setTripType(Trip.TripType.MULTI_STOP);
        trip.setTotalStops(3);
        trip.setCompletedStops(0);
        tripRepo.saveAndFlush(trip);
        TripStop firstStop = TripStop.builder()
                .trip(trip)
                .stopOrder(1)
                .locationName("Intermediate stop 1")
                .stopStatus(TripStop.StopStatus.PENDING)
                .build();
        TripStop finalRouteStop = TripStop.builder()
                .trip(trip)
                .stopOrder(2)
                .locationName("Intermediate stop 2")
                .stopStatus(TripStop.StopStatus.PENDING)
                .build();
        tripStopRepo.saveAllAndFlush(List.of(firstStop, finalRouteStop));
        em.clear();

        assertThatThrownBy(() -> tripStopService.updateStopStatus(
                finalRouteStop.getId(), TripStop.StopStatus.ARRIVED, abcDriverA))
                .isInstanceOf(TripStopValidationException.class);

        tripStopService.updateStopStatus(firstStop.getId(), TripStop.StopStatus.ARRIVED, abcDriverA);
        TripStop completedFirstStop = tripStopService.updateStopStatus(
                firstStop.getId(), TripStop.StopStatus.COMPLETED, abcDriverA);
        LocalDateTime firstCompletedAt = completedFirstStop.getCompletedAt();

        assertThat(tripRepo.findById(trip.getId()).orElseThrow().getTripStatus())
                .isEqualTo(Trip.TripStatus.ONGOING);
        assertThat(tripRepo.findById(trip.getId()).orElseThrow().getCompletedStops()).isEqualTo(1);
        assertThat(findBookingForFreight(awardedFreight.getId()).getBookingStatus())
                .isEqualTo(Booking.BookingStatus.ACCEPTED);

        assertThatThrownBy(() -> tripService.markArrived(trip.getId(), abcDriverA))
                .isInstanceOf(TripValidationException.class);
        tripStopService.updateStopStatus(finalRouteStop.getId(), TripStop.StopStatus.ARRIVED, abcDriverA);
        TripStop completedFinalRouteStop = tripStopService.updateStopStatus(
                finalRouteStop.getId(), TripStop.StopStatus.COMPLETED, abcDriverA);
        LocalDateTime finalStopCompletedAt = completedFinalRouteStop.getCompletedAt();

        assertThat(tripStopService.updateStopStatus(
                finalRouteStop.getId(), TripStop.StopStatus.COMPLETED, abcDriverA).getCompletedAt())
                .isEqualTo(finalStopCompletedAt);
        assertThat(tripStopService.updateStopStatus(
                firstStop.getId(), TripStop.StopStatus.COMPLETED, abcDriverA).getCompletedAt())
                .isEqualTo(firstCompletedAt);

        assertThat(tripStatusTransitionService.processAutomaticTransitions()).isEmpty();
        em.flush();
        em.clear();
        assertThat(tripRepo.findById(trip.getId()).orElseThrow().getTripStatus())
                .isEqualTo(Trip.TripStatus.ONGOING);
        assertThat(findBookingForFreight(awardedFreight.getId()).getBookingStatus())
                .isEqualTo(Booking.BookingStatus.ACCEPTED);
        assertThat(em.createQuery("SELECT f.status FROM Freight f WHERE f.id = :id",
                        Freight.FreightStatus.class)
                .setParameter("id", awardedFreight.getId())
                .getSingleResult()).isEqualTo(Freight.FreightStatus.IN_TRANSIT);
        assertThat(carrierDriverRepo.findByCarrierCompanyIdAndDriverUserId(
                abcCompanyId, abcDriverA.getId()).orElseThrow().getAvailability())
                .isEqualTo(DriverAvailability.ON_TRIP);
        assertThat(vehicleRepo.findById(abcVehicle1Id).orElseThrow().getAvailabilityStatus())
                .isEqualTo("on_trip");

        tripService.markArrived(trip.getId(), abcDriverA);
        assertThat(tripStatusTransitionService.processAutomaticTransitions()).isEmpty();
        mvc.perform(post("/trips/" + trip.getId() + "/complete")
                        .header("Authorization", auth(abcDriverA)))
                .andExpect(status().is4xxClientError());
        mvc.perform(post("/trips/" + trip.getId() + "/confirm-delivery")
                        .header("Authorization", auth(otherShipper)))
                .andExpect(status().isForbidden());
        mvc.perform(post("/trips/" + trip.getId() + "/confirm-delivery")
                        .header("Authorization", auth(abcDriverA)))
                .andExpect(status().isForbidden());
        mvc.perform(post("/trips/" + trip.getId() + "/confirm-delivery")
                        .header("Authorization", auth(shipper)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tripStatus").value("COMPLETED"))
                .andExpect(jsonPath("$.shipperDeliveryConfirmedBy").value(shipper.getId()));

        em.flush();
        em.clear();
        Trip completed = tripRepo.findById(trip.getId()).orElseThrow();
        Booking booking = findBookingForFreight(awardedFreight.getId());
        assertThat(completed.getTripStatus()).isEqualTo(Trip.TripStatus.COMPLETED);
        assertThat(completed.getShipperDeliveryConfirmedBy().getId()).isEqualTo(shipper.getId());
        assertThat(completed.getShipperDeliveryConfirmedAt()).isNotNull();
        assertThat(booking.getBookingStatus()).isEqualTo(Booking.BookingStatus.DELIVERED);
        assertThat(em.createQuery("SELECT f.status FROM Freight f WHERE f.id = :id",
                        Freight.FreightStatus.class)
                .setParameter("id", awardedFreight.getId())
                .getSingleResult()).isEqualTo(Freight.FreightStatus.DELIVERED);
        assertThat(em.createQuery("SELECT COUNT(p) FROM Payment p WHERE p.booking.id = :bookingId",
                        Long.class)
                .setParameter("bookingId", booking.getId())
                .getSingleResult()).isZero();
        assertThat(settlementRepo.findByBooking_Id(booking.getId())).isEmpty();
        assertThat(carrierDriverRepo.findByCarrierCompanyIdAndDriverUserId(
                abcCompanyId, abcDriverA.getId()).orElseThrow().getAvailability())
                .isEqualTo(DriverAvailability.AVAILABLE);
        assertThat(vehicleRepo.findById(abcVehicle1Id).orElseThrow().getAvailabilityStatus())
                .isEqualTo("available");

        LocalDateTime confirmationTime = completed.getShipperDeliveryConfirmedAt();
        mvc.perform(post("/trips/" + trip.getId() + "/confirm-delivery")
                        .header("Authorization", auth(shipper)))
                .andExpect(status().isOk());
        assertThat(tripRepo.findById(trip.getId()).orElseThrow().getShipperDeliveryConfirmedAt())
                .isEqualTo(confirmationTime);
    }

    @Test
    @DisplayName("Multi-stop arrival and confirmation reject missing or gapped ordered stops")
    void multiStopArrivalRejectsIncompleteStopDefinition() {
        Trip trip = startTrip(awardedFreight.getId());
        trip.setTripType(Trip.TripType.MULTI_STOP);
        trip.setTotalStops(3);
        trip.setCompletedStops(1);
        tripRepo.saveAndFlush(trip);
        tripStopRepo.saveAndFlush(TripStop.builder()
                .trip(trip)
                .stopOrder(2)
                .locationName("Gapped stop")
                .stopStatus(TripStop.StopStatus.COMPLETED)
                .build());
        em.clear();

        assertThatThrownBy(() -> tripService.markArrived(trip.getId(), abcDriverA))
                .isInstanceOf(TripValidationException.class);

        assertThat(tripRepo.findById(trip.getId()).orElseThrow().getTripStatus())
                .isEqualTo(Trip.TripStatus.ONGOING);
        assertThat(tripRepo.findById(trip.getId()).orElseThrow().getArrivedAt()).isNull();

        trip.setTripStatus(Trip.TripStatus.ARRIVED);
        trip.setArrivedAt(LocalDateTime.now());
        tripRepo.saveAndFlush(trip);
        assertThatThrownBy(() -> tripService.confirmDeliveryByShipper(trip.getId(), shipper))
                .isInstanceOf(TripValidationException.class);
        assertThat(tripRepo.findById(trip.getId()).orElseThrow().getTripStatus())
                .isEqualTo(Trip.TripStatus.ARRIVED);
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
        settlementOutboxScheduler.processDueItems();

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
    @DisplayName("Driver cannot bypass shipper verification through either direct completion endpoint")
    void driverCannotCompleteWhileProofAwaitsVerification() throws Exception {
        Trip trip = startAndArrive(awardedFreight.getId());
        proofService.submitProof(trip.getId(), proofFile(), null, null, abcDriverA);

        mvc.perform(post("/trips/" + trip.getId() + "/complete")
                        .header("Authorization", auth(abcDriverA)))
                .andExpect(status().is4xxClientError());

        mvc.perform(patch("/trips/" + trip.getId() + "/status")
                        .header("Authorization", auth(abcDriverA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tripStatus\":\"COMPLETED\"}"))
                .andExpect(status().is4xxClientError());

        em.flush();
        em.clear();
        Trip unchangedTrip = tripRepo.findById(trip.getId()).orElseThrow();
        assertThat(unchangedTrip.getTripStatus())
                .isEqualTo(Trip.TripStatus.DELIVERY_PENDING_VERIFICATION);
        Booking booking = findBookingForFreight(awardedFreight.getId());
        assertThat(booking.getBookingStatus()).isEqualTo(Booking.BookingStatus.ACCEPTED);
        assertThat(settlementRepo.findByBooking_Id(booking.getId())).isEmpty();
    }

    @Test
    @DisplayName("Driver direct completion cannot bypass shipper confirmation")
    void legacyDirectCompletionRequiresShipperConfirmation() throws Exception {
        Trip trip = startAndArrive(awardedFreight.getId());

        mvc.perform(post("/trips/" + trip.getId() + "/complete")
                        .header("Authorization", auth(abcDriverA)))
                .andExpect(status().is4xxClientError());

        mvc.perform(patch("/trips/" + trip.getId() + "/status")
                        .header("Authorization", auth(abcDriverA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tripStatus\":\"COMPLETED\"}"))
                .andExpect(status().is4xxClientError());

        assertThat(tripRepo.findById(trip.getId()).orElseThrow().getTripStatus())
                .isEqualTo(Trip.TripStatus.ARRIVED);
        assertThat(tripRepo.findById(trip.getId()).orElseThrow().getShipperDeliveryConfirmedAt())
                .isNull();
        assertThat(carrierDriverRepo.findByCarrierCompanyIdAndDriverUserId(
                abcCompanyId, abcDriverA.getId()).orElseThrow().getAvailability())
                .isEqualTo(DriverAvailability.ON_TRIP);
        assertThat(vehicleRepo.findById(abcVehicle1Id).orElseThrow().getAvailabilityStatus())
                .isEqualTo("on_trip");
        assertThat(findBookingForFreight(awardedFreight.getId()).getBookingStatus())
                .isEqualTo(Booking.BookingStatus.ACCEPTED);
    }

    @Test
    @DisplayName("Admin force-completion preserves override behavior and releases assigned resources")
    void adminForcedCompletionRunsLifecycleCleanupWithoutProof() throws Exception {
        Trip trip = startTrip(awardedFreight.getId());

        mvc.perform(patch("/trips/" + trip.getId() + "/status")
                        .header("Authorization", auth(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tripStatus\":\"COMPLETED\",\"forceTransition\":true,\"reason\":\"Administrative override\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tripStatus").value("COMPLETED"));

        em.flush();
        em.clear();
        LocalDateTime originalCompletionTime = tripRepo.findById(trip.getId()).orElseThrow().getEndTime();
        mvc.perform(patch("/trips/" + trip.getId() + "/status")
                        .header("Authorization", auth(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tripStatus\":\"COMPLETED\",\"forceTransition\":true,\"reason\":\"Retry\"}"))
                .andExpect(status().isOk());
        em.flush();
        em.clear();
        assertThat(tripRepo.findById(trip.getId()).orElseThrow().getEndTime())
                .isEqualTo(originalCompletionTime);
        assertThat(carrierDriverRepo.findByCarrierCompanyIdAndDriverUserId(
                abcCompanyId, abcDriverA.getId()).orElseThrow().getAvailability())
                .isEqualTo(DriverAvailability.AVAILABLE);
        assertThat(vehicleRepo.findById(abcVehicle1Id).orElseThrow().getAvailabilityStatus())
                .isEqualTo("available");
        assertThat(findBookingForFreight(awardedFreight.getId()).getBookingStatus())
                .isEqualTo(Booking.BookingStatus.DELIVERED);
        assertThat(em.createQuery("SELECT f.status FROM Freight f WHERE f.id = :id",
                        Freight.FreightStatus.class)
                .setParameter("id", awardedFreight.getId())
                .getSingleResult()).isEqualTo(Freight.FreightStatus.DELIVERED);
        assertThat(proofRepo.findByTrip_Id(trip.getId())).isEmpty();
        assertThat(settlementRepo.findByBooking_Id(findBookingForFreight(awardedFreight.getId()).getId()))
                .isEmpty();
    }

    @Test
    @DisplayName("Scheduled processing never completes a multi-stop trip without shipper confirmation")
    void scheduledMultiStopCompletionWaitsForShipperConfirmation() {
        Trip trip = startTrip(awardedFreight.getId());
        trip.setTripType(Trip.TripType.MULTI_STOP);
        trip.setTotalStops(2);
        trip.setCompletedStops(1);
        tripRepo.saveAndFlush(trip);
        tripStopRepo.saveAndFlush(TripStop.builder()
                .trip(trip)
                .stopOrder(1)
                .locationName("Destination")
                .stopStatus(TripStop.StopStatus.COMPLETED)
                .build());
        em.clear();

        List<Trip> completedTrips = tripStatusTransitionService.processAutomaticTransitions();

        assertThat(completedTrips).isEmpty();
        em.flush();
        em.clear();
        assertThat(tripRepo.findById(trip.getId()).orElseThrow().getTripStatus())
                .isEqualTo(Trip.TripStatus.ONGOING);
        assertThat(carrierDriverRepo.findByCarrierCompanyIdAndDriverUserId(
                abcCompanyId, abcDriverA.getId()).orElseThrow().getAvailability())
                .isEqualTo(DriverAvailability.ON_TRIP);
        assertThat(vehicleRepo.findById(abcVehicle1Id).orElseThrow().getAvailabilityStatus())
                .isEqualTo("on_trip");
        assertThat(findBookingForFreight(awardedFreight.getId()).getBookingStatus())
                .isEqualTo(Booking.BookingStatus.ACCEPTED);
        assertThat(em.createQuery("SELECT f.status FROM Freight f WHERE f.id = :id",
                        Freight.FreightStatus.class)
                .setParameter("id", awardedFreight.getId())
                .getSingleResult()).isEqualTo(Freight.FreightStatus.IN_TRANSIT);
        assertThat(proofRepo.findByTrip_Id(trip.getId())).isEmpty();
        assertThat(tripRepo.findById(trip.getId()).orElseThrow().getShipperDeliveryConfirmedAt())
                .isNull();
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
        settlementOutboxScheduler.processDueItems();
        proofService.verifyOrRejectProof(resp.id(),
                new com.ethioloadai.delivery.dto.VerifyDeliveryRequest("VERIFY", null), shipper);
        settlementOutboxScheduler.processDueItems();

        long count = settlementRepo.findByCarrierCompanyId(abcCompanyId).size();
        assertThat(count).isEqualTo(1);
    }

    @Test
    @DisplayName("Committed POD delivery survives settlement failure and retries idempotently")
    void verifiedDeliverySurvivesSettlementFailureAndCanBeRetried() {
        Trip trip = startAndArrive(awardedFreight.getId());
        var proof = proofService.submitProof(trip.getId(), proofFile(), null, null, abcDriverA);
        Long bookingId = findBookingForFreight(awardedFreight.getId()).getId();

        TestTransaction.flagForCommit();
        TestTransaction.end();

        doThrow(new IllegalStateException("simulated commission dependency failure"))
                .when(commissionService).getCommissionRate(false);
        proofService.verifyOrRejectProof(proof.id(),
                new com.ethioloadai.delivery.dto.VerifyDeliveryRequest("VERIFY", null), shipper);
        settlementOutboxScheduler.processDueItems();

        assertThat(settlementOutboxRepo.findAll())
                .filteredOn(item -> item.getBookingId().equals(bookingId))
                .singleElement()
                .extracting(SettlementOutboxRecord::getAttemptCount)
                .isEqualTo(1);

        proofService.verifyOrRejectProof(proof.id(),
                new com.ethioloadai.delivery.dto.VerifyDeliveryRequest("VERIFY", null), shipper);

        SettlementOutboxRecord queued = settlementOutboxRepo.findAll().stream()
                .filter(item -> item.getBookingId().equals(bookingId))
                .findFirst().orElseThrow();
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            SettlementOutboxRecord retry = settlementOutboxRepo.findById(queued.getId()).orElseThrow();
            retry.setNextAttemptAt(LocalDateTime.now().minusSeconds(1));
            settlementOutboxRepo.save(retry);
        });
        settlementOutboxScheduler.processDueItems();

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            em.clear();
            assertThat(proofRepo.findById(proof.id()).orElseThrow().getStatus())
                    .isEqualTo(ProofOfDelivery.ProofStatus.VERIFIED);
            assertThat(tripRepo.findById(trip.getId()).orElseThrow().getTripStatus())
                    .isEqualTo(Trip.TripStatus.COMPLETED);
            assertThat(bookingRepo.findById(bookingId).orElseThrow().getBookingStatus())
                    .isEqualTo(Booking.BookingStatus.DELIVERED);
            assertThat(em.createQuery("SELECT f.status FROM Freight f WHERE f.id = :id",
                            Freight.FreightStatus.class)
                    .setParameter("id", awardedFreight.getId())
                    .getSingleResult()).isEqualTo(Freight.FreightStatus.DELIVERED);
            assertThat(carrierDriverRepo.findByCarrierCompanyIdAndDriverUserId(
                    abcCompanyId, abcDriverA.getId()).orElseThrow().getAvailability())
                    .isEqualTo(DriverAvailability.AVAILABLE);
            assertThat(vehicleRepo.findById(abcVehicle1Id).orElseThrow().getAvailabilityStatus())
                    .isEqualTo("available");
            assertThat(settlementRepo.findByBooking_Id(bookingId)).isEmpty();
            SettlementOutboxRecord failed = settlementOutboxRepo.findById(queued.getId()).orElseThrow();
            assertThat(failed.getStatus()).isEqualTo(SettlementOutboxRecord.Status.PENDING);
            assertThat(failed.getAttemptCount()).isEqualTo(2);
            assertThat(failed.getLastError()).isEqualTo("IllegalStateException");
        });

        doCallRealMethod().when(commissionService).getCommissionRate(false);
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            SettlementOutboxRecord retry = settlementOutboxRepo.findById(queued.getId()).orElseThrow();
            retry.setNextAttemptAt(LocalDateTime.now().minusSeconds(1));
            settlementOutboxRepo.save(retry);
        });

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        Future<?> first = executor.submit(() -> {
            await(start);
            settlementOutboxScheduler.processDueItems();
        });
        Future<?> second = executor.submit(() -> {
            await(start);
            settlementOutboxWorker.process(queued.getId());
        });
        start.countDown();
        try {
            first.get(10, TimeUnit.SECONDS);
            second.get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new AssertionError("Concurrent settlement retries did not complete", e);
        } finally {
            executor.shutdownNow();
        }

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            em.clear();
            assertThat(settlementRepo.findByBooking_Id(bookingId)).isPresent();
            assertThat(settlementRepo.findByCarrierCompanyId(abcCompanyId))
                    .filteredOn(settlement -> settlement.getBooking().getId().equals(bookingId))
                    .hasSize(1);
            assertThat(settlementOutboxRepo.findById(queued.getId()).orElseThrow().getStatus())
                    .isEqualTo(SettlementOutboxRecord.Status.PROCESSED);
            assertThat(settlementOutboxRepo.findById(queued.getId()).orElseThrow().getAttemptCount())
                    .isEqualTo(2);
        });
    }

    // ════════════════════════════════════════════════════════════════════════
    // 6. DELIVERY REJECTION
    // ════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Settlement outbox polling is registered with Spring scheduling")
    void settlementOutboxPollingIsRegistered() {
        assertThat(scheduledTaskHolder.getScheduledTasks())
                .extracting(task -> task.getTask().getRunnable())
                .filteredOn(ScheduledMethodRunnable.class::isInstance)
                .extracting(ScheduledMethodRunnable.class::cast)
                .anySatisfy(runnable -> {
                    assertThat(runnable.getTarget())
                            .isInstanceOf(SettlementOutboxScheduler.class);
                    assertThat(runnable.getMethod().getName())
                            .isEqualTo("processDueItems");
                });
    }

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
        settlementOutboxScheduler.processDueItems();

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
        settlementOutboxScheduler.processDueItems();
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
                new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff});
    }

    String auth(User u) {
        return "Bearer " + jwt.generateToken(u);
    }

    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new AssertionError("Timed out waiting for concurrent settlement workers");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted while waiting for concurrent settlement workers", e);
        }
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
