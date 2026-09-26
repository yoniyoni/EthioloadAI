package com.ethioloadai.carrier.fleet;

import com.ethioloadai.carrier.company.dto.CarrierCompanyRequest;
import com.ethioloadai.carrier.company.entity.VerificationStatus;
import com.ethioloadai.carrier.company.repository.CarrierCompanyRepository;
import com.ethioloadai.carrier.company.service.CarrierCompanyService;
import com.ethioloadai.carrier.fleet.dto.*;
import com.ethioloadai.carrier.fleet.entity.*;
import com.ethioloadai.carrier.fleet.repository.CarrierDriverRepository;
import com.ethioloadai.carrier.fleet.repository.VehicleDriverAssignmentRepository;
import com.ethioloadai.carrier.fleet.service.CarrierFleetService;
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

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;

import org.springframework.transaction.annotation.Transactional;

/**
 * Base class for all Phase 4 carrier fleet integration tests.
 * Extends the pattern established by {@code TeamTestSupport} in Phase 2/3.
 *
 * <p>Pre-wires two companies (ABC + XYZ) each with an owner, a fleet manager, and a
 * dispatcher, plus pre-registered drivers and vehicles for each company.
 *
 * <p>{@code @Transactional} ensures each test rolls back its database changes,
 * preventing plate-number and other uniqueness-constraint collisions across tests
 * that share the same H2 in-memory database context.
 */
@SpringBootTest(properties = {
    "carrier.invitations.cooldown-seconds=0",
    "carrier.invitations.max-per-hour=100"
})
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@ActiveProfiles("test")
@Transactional
abstract class FleetTestSupport {

    // ── Spring wiring ──────────────────────────────────────────────────────────
    @Autowired CarrierFleetService fleetService;
    @Autowired CarrierDriverRepository driverRepo;
    @Autowired VehicleDriverAssignmentRepository assignmentRepo;
    @Autowired VehicleRepository vehicleRepo;
    @Autowired UserRepository users;
    @Autowired CarrierCompanyRepository companies;
    @Autowired CarrierCompanyService companyService;
    @Autowired CompanyInvitationService invitations;
    @Autowired CompanyMembershipService teams;
    @Autowired CompanyMembershipRepository memberships;
    @Autowired InvitationTokens tokenHelper;
    @Autowired InvitationProperties invProps;
    @Autowired JwtService jwt;
    @Autowired ObjectMapper json;
    @Autowired MockMvc mvc;
    @Autowired EntityManager em;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager txManager;

    @MockBean EmailService emailService;
    @MockBean DocumentStorage storage;

    // ── Test fixtures — ABC Logistics ──────────────────────────────────────────
    User abcOwner;
    User abcFm;        // FLEET_MANAGER
    User abcDispatcher;
    User abcDriver1;
    User abcDriver2;
    Long abcCompanyId;
    CarrierDriver abcCd1;  // carrier_drivers row for abcDriver1
    Vehicle abcVehicle1;
    Vehicle abcVehicle2;

    // ── Test fixtures — XYZ Transport ──────────────────────────────────────────
    User xyzOwner;
    User xyzDriver;
    Long xyzCompanyId;
    CarrierDriver xyzCd;
    Vehicle xyzVehicle;

    User admin;

    final Map<String, String> links = new ConcurrentHashMap<>();

    @BeforeEach
    void initFleet() {
        invProps.setCooldownSeconds(0);
        invProps.setMaxPerHour(100);

        // Capture invitation links
        links.clear();
        doAnswer(call -> { links.put(call.getArgument(0), call.getArgument(3)); return null; })
                .when(emailService).sendInvitation(anyString(), anyString(), any(), anyString(), any());

        admin = mkUser(User.Role.ADMIN);

        // ── ABC Logistics ──────────────────────────────────────────────────────
        abcOwner = mkUser(User.Role.SHIPPER);
        abcFm    = mkUser(User.Role.SHIPPER);
        abcDispatcher = mkUser(User.Role.SHIPPER);
        abcDriver1 = mkUser(User.Role.DRIVER);
        abcDriver2 = mkUser(User.Role.DRIVER);

        abcCompanyId = mkCompany(abcOwner, true);
        addMember(abcCompanyId, abcFm, CompanyRole.FLEET_MANAGER, abcOwner);
        addMember(abcCompanyId, abcDispatcher, CompanyRole.DISPATCHER, abcOwner);

        // Register ABC carrier drivers
        abcCd1 = addCarrierDriver(abcCompanyId, abcDriver1, abcOwner);
        addCarrierDriver(abcCompanyId, abcDriver2, abcOwner);

        // Register ABC vehicles (approval_status = UNDER_REVIEW by default)
        abcVehicle1 = mkVehicle(abcCompanyId, abcOwner, "ABC-V001");
        abcVehicle2 = mkVehicle(abcCompanyId, abcOwner, "ABC-V002");

        // ── XYZ Transport ──────────────────────────────────────────────────────
        xyzOwner  = mkUser(User.Role.SHIPPER);
        xyzDriver = mkUser(User.Role.DRIVER);

        xyzCompanyId = mkCompany(xyzOwner, true);
        xyzCd      = addCarrierDriver(xyzCompanyId, xyzDriver, xyzOwner);
        xyzVehicle = mkVehicle(xyzCompanyId, xyzOwner, "XYZ-V001");
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    User mkUser(User.Role role) {
        return users.saveAndFlush(User.builder()
                .fullName("Fleet test")
                .phone(UUID.randomUUID().toString().substring(0, 15))
                .email(UUID.randomUUID() + "@fleet.test")
                .password("unused")
                .role(role)
                .isActive(true)
                .verificationStatus(role == User.Role.DRIVER)  // drivers start KYC-approved
                .build());
    }

    Long mkCompany(User creator, boolean approved) {
        var req = new CarrierCompanyRequest();
        req.setCompanyName("Test Company " + UUID.randomUUID().toString().substring(0, 8));
        Long id = companyService.create(req, creator).getId();
        if (approved) {
            var c = companies.findById(id).orElseThrow();
            c.setVerificationStatus(VerificationStatus.APPROVED);
            c.setSubmittedAt(LocalDateTime.now());
            c.setReviewedAt(LocalDateTime.now());
            c.setReviewedBy(admin.getId());
            companies.saveAndFlush(c);
        }
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

    CarrierDriver addCarrierDriver(Long companyId, User driver, User caller) {
        var req = new AddCarrierDriverRequest();
        req.setDriverUserId(driver.getId());
        return driverRepo.findByCarrierCompanyIdAndDriverUserId(companyId, driver.getId())
                .orElseGet(() -> {
                    var resp = fleetService.addDriver(companyId, req, caller);
                    return driverRepo.findById(resp.getId()).orElseThrow();
                });
    }

    Vehicle mkVehicle(Long companyId, User caller, String platePrefix) {
        // Use enough entropy to avoid cross-test collisions in the shared H2 context
        String plate = (platePrefix + UUID.randomUUID().toString().replace("-", "").substring(0, 8)).toUpperCase();
        var req = new RegisterCompanyVehicleRequest();
        req.setTruckType("Truck");
        req.setPlateNumber(plate);
        req.setCapacity(10.0);
        req.setCurrentCity("Addis Ababa");
        var resp = fleetService.registerVehicle(companyId, req, caller);
        return vehicleRepo.findById(resp.getId()).orElseThrow();
    }

    /** Approve a vehicle so it can be assigned. */
    Vehicle approveVehicle(Vehicle v) {
        v.setApprovalStatus(Vehicle.VehicleApprovalStatus.APPROVED);
        return vehicleRepo.saveAndFlush(v);
    }

    String auth(User user) { return "Bearer " + jwt.generateToken(user); }
    String fleet(Long companyId) { return "/carrier-companies/" + companyId + "/fleet"; }
    String body(Object obj) throws Exception { return json.writeValueAsString(obj); }
}
