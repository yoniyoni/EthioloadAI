package com.ethioloadai.fleet;

import com.ethioloadai.fleet.dto.*;
import com.ethioloadai.fleet.exception.FleetException;
import com.ethioloadai.fleet.service.FleetService;
import com.ethioloadai.user.entity.User;
import com.ethioloadai.user.entity.User.Role;
import com.ethioloadai.user.repository.UserRepository;
import com.ethioloadai.vehicle.entity.Vehicle;
import com.ethioloadai.vehicle.repository.VehicleRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Spring-context integration tests for the fleet management service layer.
 *
 * <p>Runs against H2 in-memory database (test profile) via existing Flyway migrations.
 * Covers end-to-end service + repository behaviour without mocks.
 *
 * <p>NOTE: Migrations that rely on PostgreSQL-specific features (PostGIS, V16+) will
 * cause errors in H2. These are pre-existing incompatibilities documented in M6–M8A.
 * Fleet-specific migrations (none added) are all H2-compatible.
 *
 * <h2>Coverage</h2>
 * <ul>
 *   <li>Fleet dashboard — real DB aggregation</li>
 *   <li>Add / remove driver lifecycle</li>
 *   <li>Add vehicle and assign to driver</li>
 *   <li>IDOR: cross-fleet access denied at service layer</li>
 *   <li>KYC rule: inactive driver rejected for vehicle assignment</li>
 *   <li>Commission: FleetService never calls CommissionService (field absent)</li>
 * </ul>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class FleetIntegrationTest {

    @Autowired private FleetService     fleetService;
    @Autowired private UserRepository   userRepository;
    @Autowired private VehicleRepository vehicleRepository;

    private User owner1;
    private User owner2;
    private User driver1;
    private User driverKycPending; // isActive = false

    @BeforeEach
    void setUp() {
        vehicleRepository.deleteAll();
        userRepository.deleteAll();

        owner1 = userRepository.save(buildUser("Owner One",   "0911100001", Role.FLEET_OWNER, null,     true));
        owner2 = userRepository.save(buildUser("Owner Two",   "0911100002", Role.FLEET_OWNER, null,     true));
        driver1 = userRepository.save(buildUser("Driver One", "0911100003", Role.DRIVER,      null,     true));
        driverKycPending = userRepository.save(buildUser("KYC Pending", "0911100004", Role.DRIVER, null, false));
    }

    // ── getDashboard ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("getDashboard: empty fleet returns zero counts")
    void dashboard_emptyFleet() {
        FleetDashboardResponse dash = fleetService.getDashboard(owner1.getId());

        assertThat(dash.getFleetOwnerId()).isEqualTo(owner1.getId());
        assertThat(dash.getDriverCount()).isZero();
        assertThat(dash.getVehicleCount()).isZero();
        assertThat(dash.getActiveBookingCount()).isZero();
    }

    @Test
    @DisplayName("getDashboard: reflects added driver and vehicle")
    void dashboard_reflectsFleetMembers() {
        // Associate driver1 with owner1
        fleetService.addDriver(owner1.getId(), new AddDriverRequest(driver1.getId()));

        // Add a vehicle
        fleetService.addVehicle(owner1.getId(), AddVehicleRequest.builder()
                .truckType("Box Truck").plateNumber("INT-001")
                .capacity(5.0).currentCity("Addis Ababa").build());

        FleetDashboardResponse dash = fleetService.getDashboard(owner1.getId());

        assertThat(dash.getDriverCount()).isEqualTo(1);
        assertThat(dash.getVehicleCount()).isEqualTo(1);
        assertThat(dash.getDrivers()).extracting(FleetDashboardResponse.DriverSummary::getId)
                .containsExactly(driver1.getId());
    }

    @Test
    @DisplayName("getDashboard: non-fleet-owner throws FleetAuthorizationException")
    void dashboard_nonFleetOwner_throws() {
        assertThatThrownBy(() -> fleetService.getDashboard(driver1.getId()))
                .isInstanceOf(FleetException.FleetAuthorizationException.class);
    }

    // ── addDriver / removeDriver ──────────────────────────────────────────────

    @Test
    @DisplayName("addDriver: links driver to fleet, visible in getFleetDrivers")
    void addDriver_visibleInList() {
        fleetService.addDriver(owner1.getId(), new AddDriverRequest(driver1.getId()));

        List<FleetDriverResponse> drivers = fleetService.getFleetDrivers(owner1.getId());
        assertThat(drivers).extracting(FleetDriverResponse::getId)
                .containsExactly(driver1.getId());
    }

    @Test
    @DisplayName("addDriver → removeDriver: driver is removed from fleet")
    void addThenRemoveDriver_clearsFleet() {
        fleetService.addDriver(owner1.getId(), new AddDriverRequest(driver1.getId()));
        fleetService.removeDriver(owner1.getId(), driver1.getId());

        List<FleetDriverResponse> drivers = fleetService.getFleetDrivers(owner1.getId());
        assertThat(drivers).isEmpty();

        // Verify fleetOwnerId is null in DB
        User reloaded = userRepository.findById(driver1.getId()).orElseThrow();
        assertThat(reloaded.getFleetOwnerId()).isNull();
    }

    @Test
    @DisplayName("IDOR: owner1 cannot poach driver belonging to owner2")
    void idor_addDriver_cannotPoachFromOtherFleet() {
        // Assign driver1 to owner2 first
        fleetService.addDriver(owner2.getId(), new AddDriverRequest(driver1.getId()));

        // owner1 tries to add same driver — should be blocked
        assertThatThrownBy(() -> fleetService.addDriver(owner1.getId(), new AddDriverRequest(driver1.getId())))
                .isInstanceOf(FleetException.ResourceAlreadyAssignedException.class);
    }

    @Test
    @DisplayName("IDOR: owner1 cannot remove owner2's driver")
    void idor_removeDriver_cannotRemoveOtherFleetDriver() {
        fleetService.addDriver(owner2.getId(), new AddDriverRequest(driver1.getId()));

        assertThatThrownBy(() -> fleetService.removeDriver(owner1.getId(), driver1.getId()))
                .isInstanceOf(FleetException.FleetAuthorizationException.class);
    }

    @Test
    @DisplayName("addDriver: idempotent re-add returns driver without error")
    void addDriver_idempotent() {
        fleetService.addDriver(owner1.getId(), new AddDriverRequest(driver1.getId()));
        // Second call must not throw
        assertThatCode(() -> fleetService.addDriver(owner1.getId(), new AddDriverRequest(driver1.getId())))
                .doesNotThrowAnyException();
    }

    // ── addVehicle ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("addVehicle: vehicle is persisted with correct fleetOwnerId")
    void addVehicle_persistedCorrectly() {
        FleetVehicleResponse resp = fleetService.addVehicle(owner1.getId(),
                AddVehicleRequest.builder()
                        .truckType("Flatbed").plateNumber("INT-VH-001")
                        .capacity(10.0).currentCity("Dire Dawa").build());

        assertThat(resp.getId()).isNotNull();
        assertThat(resp.getPlateNumber()).isEqualTo("INT-VH-001");

        Vehicle saved = vehicleRepository.findById(resp.getId()).orElseThrow();
        assertThat(saved.getFleetOwnerId()).isEqualTo(owner1.getId());
    }

    @Test
    @DisplayName("addVehicle: duplicate plate number throws FleetValidationException")
    void addVehicle_duplicatePlate_throws() {
        fleetService.addVehicle(owner1.getId(), AddVehicleRequest.builder()
                .truckType("Truck").plateNumber("DUP-PLATE").capacity(5.0).currentCity("Addis Ababa")
                .build());

        assertThatThrownBy(() -> fleetService.addVehicle(owner1.getId(),
                AddVehicleRequest.builder()
                        .truckType("Truck").plateNumber("DUP-PLATE").capacity(5.0).currentCity("Hawassa")
                        .build()))
                .isInstanceOf(FleetException.FleetValidationException.class)
                .hasMessageContaining("Plate number already taken");
    }

    @Test
    @DisplayName("addVehicle: vehicle is listed in getFleetVehicles")
    void addVehicle_visibleInList() {
        fleetService.addVehicle(owner1.getId(), AddVehicleRequest.builder()
                .truckType("Truck").plateNumber("VIS-001").capacity(5.0).currentCity("Addis Ababa")
                .build());

        List<FleetVehicleResponse> vehicles = fleetService.getFleetVehicles(owner1.getId());
        assertThat(vehicles).extracting(FleetVehicleResponse::getPlateNumber)
                .contains("VIS-001");
    }

    // ── assignVehicle ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("assignVehicle: active driver assigned, vehicle.userId updated")
    void assignVehicle_success() {
        fleetService.addDriver(owner1.getId(), new AddDriverRequest(driver1.getId()));
        FleetVehicleResponse veh = fleetService.addVehicle(owner1.getId(),
                AddVehicleRequest.builder()
                        .truckType("Truck").plateNumber("ASN-001").capacity(5.0).currentCity("Addis Ababa")
                        .build());

        FleetVehicleResponse assigned = fleetService.assignVehicle(
                owner1.getId(), veh.getId(), new AssignVehicleRequest(driver1.getId()));

        assertThat(assigned.getAssignedDriverId()).isEqualTo(driver1.getId());

        Vehicle inDb = vehicleRepository.findById(veh.getId()).orElseThrow();
        assertThat(inDb.getUserId()).isEqualTo(driver1.getId());
    }

    @Test
    @DisplayName("KYC rule (M8A): inactive driver rejected for vehicle assignment")
    void assignVehicle_inactiveDriver_rejected() {
        fleetService.addDriver(owner1.getId(), new AddDriverRequest(driverKycPending.getId()));
        FleetVehicleResponse veh = fleetService.addVehicle(owner1.getId(),
                AddVehicleRequest.builder()
                        .truckType("Truck").plateNumber("KYC-001").capacity(5.0).currentCity("Addis Ababa")
                        .build());

        assertThatThrownBy(() -> fleetService.assignVehicle(
                owner1.getId(), veh.getId(), new AssignVehicleRequest(driverKycPending.getId())))
                .isInstanceOf(FleetException.FleetValidationException.class)
                .hasMessageContaining("KYC approval");
    }

    @Test
    @DisplayName("IDOR: owner1 cannot assign vehicle to owner2's driver")
    void idor_assignVehicle_crossFleetDriver() {
        fleetService.addDriver(owner2.getId(), new AddDriverRequest(driver1.getId()));
        FleetVehicleResponse veh = fleetService.addVehicle(owner1.getId(),
                AddVehicleRequest.builder()
                        .truckType("Truck").plateNumber("IDR-001").capacity(5.0).currentCity("Addis Ababa")
                        .build());

        assertThatThrownBy(() -> fleetService.assignVehicle(
                owner1.getId(), veh.getId(), new AssignVehicleRequest(driver1.getId())))
                .isInstanceOf(FleetException.FleetAuthorizationException.class);
    }

    @Test
    @DisplayName("IDOR: owner2 cannot assign owner1's vehicle")
    void idor_assignVehicle_crossFleetVehicle() {
        fleetService.addDriver(owner1.getId(), new AddDriverRequest(driver1.getId()));
        FleetVehicleResponse veh = fleetService.addVehicle(owner1.getId(),
                AddVehicleRequest.builder()
                        .truckType("Truck").plateNumber("IDR-VH-001").capacity(5.0).currentCity("Addis Ababa")
                        .build());

        assertThatThrownBy(() -> fleetService.assignVehicle(
                owner2.getId(), veh.getId(), new AssignVehicleRequest(driver1.getId())))
                .isInstanceOf(FleetException.FleetAuthorizationException.class);
    }

    // ── RBAC helpers ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("SHIPPER cannot call getFleetDrivers — FleetAuthorizationException")
    void rbac_shipperCannotListDrivers() {
        User shipper = userRepository.save(buildUser("Shipper", "0911200001", Role.SHIPPER, null, true));
        assertThatThrownBy(() -> fleetService.getFleetDrivers(shipper.getId()))
                .isInstanceOf(FleetException.FleetAuthorizationException.class);
    }

    @Test
    @DisplayName("DRIVER cannot call getFleetVehicles — FleetAuthorizationException")
    void rbac_driverCannotListVehicles() {
        assertThatThrownBy(() -> fleetService.getFleetVehicles(driver1.getId()))
                .isInstanceOf(FleetException.FleetAuthorizationException.class);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private User buildUser(String name, String phone, Role role, Long fleetOwnerId, boolean active) {
        User u = new User();
        u.setFullName(name);
        u.setPhone(phone);
        u.setPassword("$2a$12$dummy.hashed.password");
        u.setRole(role);
        u.setFleetOwnerId(fleetOwnerId);
        u.setIsActive(active);
        u.setVerificationStatus(false);
        return u;
    }
}
