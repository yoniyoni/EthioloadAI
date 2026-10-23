package com.ethioloadai.carrier.offer;

import com.ethioloadai.carrier.company.dto.CarrierCompanyRequest;
import com.ethioloadai.carrier.company.entity.VerificationStatus;
import com.ethioloadai.carrier.company.repository.CarrierCompanyRepository;
import com.ethioloadai.carrier.company.service.CarrierCompanyService;
import com.ethioloadai.carrier.fleet.dto.AddCarrierDriverRequest;
import com.ethioloadai.carrier.fleet.dto.CreateAssignmentRequest;
import com.ethioloadai.carrier.fleet.dto.RegisterCompanyVehicleRequest;
import com.ethioloadai.carrier.fleet.repository.CarrierDriverRepository;
import com.ethioloadai.carrier.fleet.service.CarrierFleetService;
import com.ethioloadai.carrier.offer.repository.CarrierOfferRepository;
import com.ethioloadai.carrier.offer.repository.OfferNegotiationEntryRepository;
import com.ethioloadai.carrier.team.dto.InvitationRequest;
import com.ethioloadai.carrier.team.dto.InvitationResponse;
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
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
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
 * Phase 5 — Base class for carrier offer integration tests.
 *
 * <p>Sets up two carrier companies (ABC Logistics + XYZ Transport) each with
 * an OWNER and DISPATCHER, plus a plain SHIPPER and a standalone DRIVER user.
 * Creates one negotiable freight and one fixed-price freight owned by the shipper.
 *
 * <p>All tests run in a transaction that is rolled back after each test, so
 * no cross-test data pollution occurs.
 */
@SpringBootTest(properties = {
    "carrier.invitations.cooldown-seconds=0",
    "carrier.invitations.max-per-hour=100"
})
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@ActiveProfiles("test")
@Transactional
abstract class OfferTestSupport {

    // ── Spring beans ──────────────────────────────────────────────────────────
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JwtService jwt;
    @Autowired UserRepository users;
    @Autowired FreightRepository freightRepo;
    @Autowired FreightService freightService;
    @Autowired CarrierCompanyRepository companies;
    @Autowired CarrierCompanyService companyService;
    @Autowired CarrierFleetService fleetService;
    @Autowired CarrierDriverRepository driverRepo;
    @Autowired VehicleRepository vehicleRepo;
    @Autowired CompanyInvitationService invitations;
    @Autowired CompanyMembershipService teams;
    @Autowired CompanyMembershipRepository memberships;
    @Autowired InvitationTokens tokenHelper;
    @Autowired InvitationProperties invProps;
    @Autowired CarrierOfferRepository offerRepo;
    @Autowired OfferNegotiationEntryRepository historyRepo;
    @Autowired EntityManager em;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager txManager;

    @MockBean EmailService emailService;
    @MockBean DocumentStorage storage;

    // ── Fixtures ──────────────────────────────────────────────────────────────
    User shipper;
    User otherShipper;      // unrelated shipper — must not access shipper's freight
    User driver;            // plain DRIVER — must not submit company offers
    User abcDriver;
    User xyzDriver;

    User abcOwner;
    User abcDispatcher;
    User abcFleetManager;
    Long abcCompanyId;
    Long abcVehicleId;

    User xyzOwner;
    User xyzDispatcher;
    Long xyzCompanyId;
    Long xyzVehicleId;

    User admin;

    Freight negotiableFreight;  // priceType = NEGOTIABLE, budget = 50,000
    Freight fixedFreight;       // priceType = FIXED,      budget = 50,000

    final Map<String, String> links = new ConcurrentHashMap<>();

    @BeforeEach
    void initOfferFixtures() {
        invProps.setCooldownSeconds(0);
        invProps.setMaxPerHour(100);

        links.clear();
        doAnswer(call -> { links.put(call.getArgument(0), call.getArgument(3)); return null; })
                .when(emailService).sendInvitation(anyString(), anyString(), any(), anyString(), any());

        // Users
        admin        = mkUser(User.Role.ADMIN);
        shipper      = mkUser(User.Role.SHIPPER);
        otherShipper = mkUser(User.Role.SHIPPER);
        driver       = mkUser(User.Role.DRIVER);

        // ABC Logistics
        abcOwner       = mkUser(User.Role.SHIPPER);
        abcDispatcher  = mkUser(User.Role.SHIPPER);
        abcFleetManager = mkUser(User.Role.SHIPPER);
        abcDriver      = mkUser(User.Role.DRIVER);
        abcCompanyId   = mkApprovedCompany(abcOwner);
        addMember(abcCompanyId, abcDispatcher,  CompanyRole.DISPATCHER,    abcOwner);
        addMember(abcCompanyId, abcFleetManager, CompanyRole.FLEET_MANAGER, abcOwner);

        // XYZ Transport
        xyzOwner      = mkUser(User.Role.SHIPPER);
        xyzDispatcher = mkUser(User.Role.SHIPPER);
        xyzDriver     = mkUser(User.Role.DRIVER);
        xyzCompanyId  = mkApprovedCompany(xyzOwner);
        addMember(xyzCompanyId, xyzDispatcher, CompanyRole.DISPATCHER, xyzOwner);

        abcVehicleId = addOfferPairing(abcCompanyId, abcOwner, abcDriver, "ABC");
        xyzVehicleId = addOfferPairing(xyzCompanyId, xyzOwner, xyzDriver, "XYZ");

        // Freight
        negotiableFreight = mkFreight(shipper, Freight.PriceType.NEGOTIABLE, new BigDecimal("50000.00"));
        fixedFreight      = mkFreight(shipper, Freight.PriceType.FIXED,      new BigDecimal("50000.00"));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    User mkUser(User.Role role) {
        return users.saveAndFlush(User.builder()
                .fullName("Test user")
                .phone(UUID.randomUUID().toString().substring(0, 15))
                .email(UUID.randomUUID() + "@offer.test")
                .password("unused")
                .role(role)
                .isActive(true)
                .verificationStatus(role == User.Role.DRIVER)
                .build());
    }

    Long mkApprovedCompany(User creator) {
        var req = new CarrierCompanyRequest();
        req.setCompanyName("Test Co " + UUID.randomUUID().toString().substring(0, 8));
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

    private Long addOfferPairing(Long companyId, User owner, User assignedDriver, String prefix) {
        AddCarrierDriverRequest driverRequest = new AddCarrierDriverRequest();
        driverRequest.setDriverUserId(assignedDriver.getId());
        fleetService.addDriver(companyId, driverRequest, owner);
        Long carrierDriverId = driverRepo.findByCarrierCompanyIdAndDriverUserId(
                companyId, assignedDriver.getId()).orElseThrow().getId();

        RegisterCompanyVehicleRequest vehicleRequest = new RegisterCompanyVehicleRequest();
        vehicleRequest.setTruckType("Heavy Truck");
        vehicleRequest.setPlateNumber(prefix + "-" + UUID.randomUUID().toString().substring(0, 8));
        vehicleRequest.setCapacity(20.0);
        vehicleRequest.setVehicleCategory("HEAVY");
        Long vehicleId = fleetService.registerVehicle(companyId, vehicleRequest, owner).getId();
        Vehicle vehicle = vehicleRepo.findById(vehicleId).orElseThrow();
        vehicle.setApprovalStatus(Vehicle.VehicleApprovalStatus.APPROVED);
        vehicleRepo.saveAndFlush(vehicle);

        CreateAssignmentRequest assignmentRequest = new CreateAssignmentRequest();
        assignmentRequest.setCarrierDriverId(carrierDriverId);
        assignmentRequest.setVehicleId(vehicleId);
        fleetService.assign(companyId, assignmentRequest, owner);
        return vehicleId;
    }

    Freight mkFreight(User owner, Freight.PriceType priceType, BigDecimal budget) {
        var req = CreateFreightRequest.builder()
                .pickupLocation("Addis Ababa")
                .destination("Dire Dawa")
                .materialType("general")
                .weightTons(new BigDecimal("10.0"))
                .budget(budget)
                .urgencyLevel("normal")
                .serviceType(Freight.ServiceType.INTERCITY)
                .priceType(priceType)
                .build();
        var resp = freightService.createFreight(owner.getId(), req);
        return freightRepo.findById(resp.getId()).orElseThrow();
    }

    // ── Auth helpers ──────────────────────────────────────────────────────────
    String auth(User user)   { return "Bearer " + jwt.generateToken(user); }
    String body(Object obj) throws Exception { return json.writeValueAsString(obj); }

    // ── URL helpers ───────────────────────────────────────────────────────────
    String offerUrl(Long companyId, Long freightId) {
        return "/carrier-companies/" + companyId + "/freight/" + freightId + "/offers";
    }
    String counterCarrierUrl(Long companyId, Long offerId) {
        return "/carrier-companies/" + companyId + "/offers/" + offerId + "/counter";
    }
    String acceptCarrierUrl(Long companyId, Long offerId) {
        return "/carrier-companies/" + companyId + "/offers/" + offerId + "/accept";
    }
    String withdrawUrl(Long companyId, Long offerId) {
        return "/carrier-companies/" + companyId + "/offers/" + offerId + "/withdraw";
    }
    String shipperCounterUrl(Long freightId, Long offerId) {
        return "/freight/" + freightId + "/carrier-offers/" + offerId + "/counter";
    }
    String shipperAcceptUrl(Long freightId, Long offerId) {
        return "/freight/" + freightId + "/carrier-offers/" + offerId + "/accept";
    }
    String shipperRejectUrl(Long freightId, Long offerId) {
        return "/freight/" + freightId + "/carrier-offers/" + offerId + "/reject";
    }
    String listOffersUrl(Long freightId) {
        return "/freight/" + freightId + "/carrier-offers";
    }
    String historyUrl(Long freightId, Long offerId) {
        return "/freight/" + freightId + "/carrier-offers/" + offerId + "/history";
    }
    String companyOffersUrl(Long companyId) {
        return "/carrier-companies/" + companyId + "/offers";
    }
}
