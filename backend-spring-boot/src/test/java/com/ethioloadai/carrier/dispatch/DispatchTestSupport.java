package com.ethioloadai.carrier.dispatch;

import com.ethioloadai.carrier.company.dto.CarrierCompanyRequest;
import com.ethioloadai.carrier.company.entity.VerificationStatus;
import com.ethioloadai.carrier.company.repository.CarrierCompanyRepository;
import com.ethioloadai.carrier.company.service.CarrierCompanyService;
import com.ethioloadai.carrier.dispatch.repository.DispatchAssignmentRepository;
import com.ethioloadai.carrier.fleet.dto.AddCarrierDriverRequest;
import com.ethioloadai.carrier.fleet.dto.RegisterCompanyVehicleRequest;
import com.ethioloadai.carrier.fleet.repository.CarrierDriverRepository;
import com.ethioloadai.carrier.fleet.service.CarrierFleetService;
import com.ethioloadai.carrier.offer.repository.CarrierOfferRepository;
import com.ethioloadai.carrier.offer.service.CarrierOfferService;
import com.ethioloadai.carrier.offer.dto.SubmitOfferRequest;
import com.ethioloadai.carrier.team.dto.InvitationRequest;
import com.ethioloadai.carrier.team.entity.CompanyRole;
import com.ethioloadai.carrier.team.repository.CompanyMembershipRepository;
import com.ethioloadai.carrier.team.service.CompanyInvitationService;
import com.ethioloadai.carrier.team.service.CompanyMembershipService;
import com.ethioloadai.carrier.team.service.InvitationTokens;
import com.ethioloadai.carrier.team.config.InvitationProperties;
import com.ethioloadai.document.storage.DocumentStorage;
import com.ethioloadai.email.EmailService;
import com.ethioloadai.freight.dto.CreateFreightRequest;
import com.ethioloadai.freight.entity.Freight;
import com.ethioloadai.freight.repository.FreightRepository;
import com.ethioloadai.freight.service.FreightService;
import com.ethioloadai.security.jwt.JwtService;
import com.ethioloadai.user.entity.User;
import com.ethioloadai.user.repository.UserRepository;
import com.ethioloadai.vehicle.entity.Vehicle;
import com.ethioloadai.vehicle.repository.VehicleRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doAnswer;

/**
 * Phase 6 — Base class for dispatch integration tests.
 *
 * <p>Creates a complete test scenario:
 * <ul>
 *   <li>ABC Logistics — Owner, Dispatcher, Fleet Manager, Driver A, Driver B, Vehicle 1</li>
 *   <li>XYZ Transport — Owner, Dispatcher, Driver X, Vehicle X</li>
 *   <li>Shipper</li>
 *   <li>Freight with a winning CarrierOffer by ABC Logistics</li>
 * </ul>
 */
@SpringBootTest(properties = {
    "carrier.invitations.cooldown-seconds=0",
    "carrier.invitations.max-per-hour=100"
})
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@ActiveProfiles("test")
@Transactional
abstract class DispatchTestSupport {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JwtService jwt;
    @Autowired UserRepository users;
    @Autowired FreightRepository freightRepo;
    @Autowired FreightService freightService;
    @Autowired CarrierCompanyRepository companies;
    @Autowired CarrierCompanyService companyService;
    @Autowired CompanyInvitationService invitations;
    @Autowired CompanyMembershipService teams;
    @Autowired CompanyMembershipRepository memberships;
    @Autowired InvitationTokens tokenHelper;
    @Autowired InvitationProperties invProps;
    @Autowired CarrierOfferRepository offerRepo;
    @Autowired CarrierOfferService offerService;
    @Autowired CarrierFleetService fleetService;
    @Autowired CarrierDriverRepository driverRepo;
    @Autowired VehicleRepository vehicleRepo;
    @Autowired DispatchAssignmentRepository dispatchRepo;

    @MockBean EmailService emailService;
    @MockBean DocumentStorage storage;

    // ── Actors ────────────────────────────────────────────────────────────────
    User admin;
    User shipper;
    User otherShipper;

    // ABC Logistics
    User abcOwner;
    User abcDispatcher;
    User abcFleetManager;
    User abcDriverA;
    User abcDriverB;
    Long abcCompanyId;
    Long abcVehicle1Id;   // APPROVED vehicle belonging to ABC

    // XYZ Transport
    User xyzOwner;
    User xyzDispatcher;
    User xyzDriver;
    Long xyzCompanyId;
    Long xyzVehicleId;

    // ── Freight fixtures ──────────────────────────────────────────────────────
    Freight awardedFreight;     // freight with ABC winning offer (carrier_award_offer_id set)
    Long abcAwardOfferId;       // the winning offer ID
    BigDecimal agreedAmount;    // the frozen agreed price

    // Additional freight for redispatch / rejection tests
    Freight awardedFreight2;
    Long abc2ndOfferId;

    final Map<String, String> links = new ConcurrentHashMap<>();

    @BeforeEach
    void initDispatchFixtures() {
        invProps.setCooldownSeconds(0);
        invProps.setMaxPerHour(100);
        links.clear();
        doAnswer(call -> {
            links.put(call.getArgument(0), call.getArgument(3));
            return null;
        }).when(emailService).sendInvitation(anyString(), anyString(), any(), anyString(), any());

        admin        = mkUser(User.Role.ADMIN);
        shipper      = mkUser(User.Role.SHIPPER);
        otherShipper = mkUser(User.Role.SHIPPER);

        // ── ABC Logistics ──────────────────────────────────────────────────────
        abcOwner        = mkUser(User.Role.SHIPPER);
        abcDispatcher   = mkUser(User.Role.SHIPPER);
        abcFleetManager = mkUser(User.Role.SHIPPER);
        abcDriverA      = mkUser(User.Role.DRIVER);
        abcDriverB      = mkUser(User.Role.DRIVER);
        abcCompanyId    = mkApprovedCompany(abcOwner);
        addMember(abcCompanyId, abcDispatcher,   CompanyRole.DISPATCHER,    abcOwner);
        addMember(abcCompanyId, abcFleetManager, CompanyRole.FLEET_MANAGER, abcOwner);

        // Add drivers to ABC fleet
        var addDriverReqA = new AddCarrierDriverRequest();
        addDriverReqA.setDriverUserId(abcDriverA.getId());
        fleetService.addDriver(abcCompanyId, addDriverReqA, abcOwner);
        var addDriverReqB = new AddCarrierDriverRequest();
        addDriverReqB.setDriverUserId(abcDriverB.getId());
        fleetService.addDriver(abcCompanyId, addDriverReqB, abcOwner);

        // Register ABC vehicle (APPROVED)
        RegisterCompanyVehicleRequest vReq = new RegisterCompanyVehicleRequest();
        vReq.setTruckType("Heavy Truck");
        vReq.setPlateNumber("AA-" + UUID.randomUUID().toString().substring(0,6).toUpperCase());
        vReq.setCapacity(20.0);   // 20 tons
        vReq.setVehicleCategory("HEAVY");
        vReq.setCurrentCity("Addis Ababa");
        Long rawVehicleId = fleetService.registerVehicle(abcCompanyId, vReq, abcOwner).getId();
        // Approve the vehicle
        Vehicle v = vehicleRepo.findById(rawVehicleId).orElseThrow();
        v.setApprovalStatus(Vehicle.VehicleApprovalStatus.APPROVED);
        vehicleRepo.saveAndFlush(v);
        abcVehicle1Id = rawVehicleId;

        // ── XYZ Transport ──────────────────────────────────────────────────────
        xyzOwner      = mkUser(User.Role.SHIPPER);
        xyzDispatcher = mkUser(User.Role.SHIPPER);
        xyzDriver     = mkUser(User.Role.DRIVER);
        xyzCompanyId  = mkApprovedCompany(xyzOwner);
        addMember(xyzCompanyId, xyzDispatcher, CompanyRole.DISPATCHER, xyzOwner);

        fleetService.addDriver(xyzCompanyId,
                new AddCarrierDriverRequest() {{ setDriverUserId(xyzDriver.getId()); }}, xyzOwner);

        RegisterCompanyVehicleRequest xyzVReq = new RegisterCompanyVehicleRequest();
        xyzVReq.setTruckType("Medium Truck");
        xyzVReq.setPlateNumber("XY-" + UUID.randomUUID().toString().substring(0,6).toUpperCase());
        xyzVReq.setCapacity(15.0);
        xyzVReq.setCurrentCity("Addis Ababa");
        Long xyzRawId = fleetService.registerVehicle(xyzCompanyId, xyzVReq, xyzOwner).getId();
        Vehicle xyzV = vehicleRepo.findById(xyzRawId).orElseThrow();
        xyzV.setApprovalStatus(Vehicle.VehicleApprovalStatus.APPROVED);
        vehicleRepo.saveAndFlush(xyzV);
        xyzVehicleId = xyzRawId;

        // ── Create awarded freight ─────────────────────────────────────────────
        awardedFreight = mkFreight(shipper, Freight.PriceType.NEGOTIABLE, new BigDecimal("50000.00"));
        agreedAmount   = new BigDecimal("45000.00");
        abcAwardOfferId = createAndAwardOffer(abcOwner, abcCompanyId, awardedFreight, agreedAmount, shipper);

        // Second awarded freight for redispatch/rejection tests
        awardedFreight2 = mkFreight(shipper, Freight.PriceType.NEGOTIABLE, new BigDecimal("60000.00"));
        abc2ndOfferId   = createAndAwardOffer(abcOwner, abcCompanyId, awardedFreight2,
                new BigDecimal("55000.00"), shipper);
    }

    // ── Fixture helpers ───────────────────────────────────────────────────────

    User mkUser(User.Role role) {
        return users.saveAndFlush(User.builder()
                .fullName("Test User " + UUID.randomUUID().toString().substring(0, 6))
                .phone(UUID.randomUUID().toString().substring(0, 15))
                .email(UUID.randomUUID() + "@dispatch.test")
                .password("unused")
                .role(role)
                .isActive(true)
                .verificationStatus(role == User.Role.DRIVER)
                .build());
    }

    Long mkApprovedCompany(User creator) {
        var req = new CarrierCompanyRequest();
        req.setCompanyName("Co " + UUID.randomUUID().toString().substring(0, 8));
        Long id = companyService.create(req, creator).getId();
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
        String link = links.get(target.getEmail().strip().toLowerCase(Locale.ROOT));
        String token = link.substring(link.indexOf("#token=") + 7);
        invitations.accept(token, target);
    }

    Freight mkFreight(User owner, Freight.PriceType priceType, BigDecimal budget) {
        var req = CreateFreightRequest.builder()
                .pickupLocation("Addis Ababa")
                .destination("Dire Dawa")
                .materialType("general cargo")
                .weightTons(new BigDecimal("10.0"))
                .budget(budget)
                .urgencyLevel("normal")
                .serviceType(Freight.ServiceType.INTERCITY)
                .priceType(priceType)
                .build();
        var resp = freightService.createFreight(owner.getId(), req);
        return freightRepo.findById(resp.getId()).orElseThrow();
    }

    /**
     * Creates a carrier offer for the given company, then has the shipper accept it.
     * Returns the winning offer ID.
     */
    Long createAndAwardOffer(User companyActor, Long companyId, Freight freight,
                              BigDecimal amount, User freightOwner) {
        var req = new SubmitOfferRequest(amount, null);
        var offer = offerService.submitOffer(freight.getId(), companyId, companyActor, req);
        // Shipper accepts immediately
        offerService.shipperAccept(freight.getId(), offer.id(), freightOwner);
        // Reload freight to confirm award
        freightRepo.flush();
        return offer.id();
    }

    // ── Auth + URL helpers ─────────────────────────────────────────────────────
    String auth(User user)   { return "Bearer " + jwt.generateToken(user); }
    String body(Object obj) throws Exception { return json.writeValueAsString(obj); }

    String dispatchUrl(Long companyId, Long freightId) {
        return "/carrier-companies/" + companyId + "/freight/" + freightId + "/dispatch";
    }
    String companyDispatchesUrl(Long companyId) {
        return "/carrier-companies/" + companyId + "/dispatches";
    }
    String dispatchDetailUrl(Long companyId, Long dispatchId) {
        return "/carrier-companies/" + companyId + "/dispatches/" + dispatchId;
    }
    String cancelDispatchUrl(Long companyId, Long dispatchId) {
        return "/carrier-companies/" + companyId + "/dispatches/" + dispatchId + "/cancel";
    }
    String freightDispatchesUrl(Long companyId, Long freightId) {
        return "/carrier-companies/" + companyId + "/freight/" + freightId + "/dispatches";
    }
    String awardedFreightsUrl(Long companyId) {
        return "/carrier-companies/" + companyId + "/awarded-freights";
    }
    String driverAssignmentsUrl()                { return "/api/driver/assignments"; }
    String driverAssignmentDetailUrl(Long id)    { return "/api/driver/assignments/" + id; }
    String driverAcceptUrl(Long id)              { return "/api/driver/assignments/" + id + "/accept"; }
    String driverRejectUrl(Long id)              { return "/api/driver/assignments/" + id + "/reject"; }
}
