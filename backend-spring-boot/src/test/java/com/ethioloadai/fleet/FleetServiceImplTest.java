package com.ethioloadai.fleet;

import com.ethioloadai.booking.entity.Booking;
import com.ethioloadai.booking.entity.Booking.BookingStatus;
import com.ethioloadai.booking.repository.BookingRepository;
import com.ethioloadai.fleet.dto.*;
import com.ethioloadai.fleet.exception.FleetException;
import com.ethioloadai.fleet.service.FleetServiceImpl;
import com.ethioloadai.user.entity.User;
import com.ethioloadai.user.entity.User.Role;
import com.ethioloadai.user.repository.UserRepository;
import com.ethioloadai.vehicle.entity.Vehicle;
import com.ethioloadai.vehicle.repository.VehicleRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for FleetServiceImpl covering:
 *
 * Business logic:
 *   - fleet ownership validation
 *   - driver association / removal
 *   - vehicle creation / assignment
 *   - dashboard aggregation
 *
 * Authorization / IDOR:
 *   - fleet owner accessing own resources
 *   - fleet owner accessing another owner's resources
 *   - driver attempting fleet-owner operations (role mismatch)
 *   - shipper attempting fleet-owner operations (role mismatch)
 *   - arbitrary driverId / vehicleId manipulation
 *
 * KYC enforcement (M8A rule preserved):
 *   - inactive driver cannot be assigned to vehicle
 *   - active driver can be assigned normally
 *
 * Commission consistency (M7 rule):
 *   - FleetServiceImpl never touches commission; commission logic stays in CommissionService
 *
 * Idempotency:
 *   - addDriver when driver already in same fleet returns current state (no duplicate error)
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FleetServiceImplTest {

    @Mock private UserRepository    userRepository;
    @Mock private VehicleRepository vehicleRepository;
    @Mock private BookingRepository bookingRepository;

    private FleetServiceImpl service;

    // ── Shared test fixtures ──────────────────────────────────────────────────
    private User    fleetOwner1;
    private User    fleetOwner2;
    private User    driver1;      // belongs to fleet 1
    private User    driver2;      // belongs to fleet 1
    private User    unassigned;   // no fleet
    private User    otherDriver;  // belongs to fleet 2
    private User    shipper;
    private Vehicle vehicle1;     // belongs to fleet 1, assigned to driver1
    private Vehicle vehicle2;     // belongs to fleet 1, unassigned

    private static final Long OWNER1_ID    = 10L;
    private static final Long OWNER2_ID    = 20L;
    private static final Long DRIVER1_ID   = 101L;
    private static final Long DRIVER2_ID   = 102L;
    private static final Long UNASSIGNED_ID = 103L;
    private static final Long OTHER_DRIVER_ID = 104L;
    private static final Long SHIPPER_ID   = 200L;
    private static final Long VEHICLE1_ID  = 301L;
    private static final Long VEHICLE2_ID  = 302L;

    @BeforeEach
    void setUp() {
        service = new FleetServiceImpl(userRepository, vehicleRepository, bookingRepository);

        fleetOwner1 = user(OWNER1_ID,    Role.FLEET_OWNER, null,     true);
        fleetOwner2 = user(OWNER2_ID,    Role.FLEET_OWNER, null,     true);
        driver1     = user(DRIVER1_ID,   Role.DRIVER,      OWNER1_ID, true);
        driver2     = user(DRIVER2_ID,   Role.DRIVER,      OWNER1_ID, true);
        unassigned  = user(UNASSIGNED_ID, Role.DRIVER,     null,      true);
        otherDriver = user(OTHER_DRIVER_ID, Role.DRIVER,   OWNER2_ID, true);
        shipper     = user(SHIPPER_ID,   Role.SHIPPER,     null,      true);

        vehicle1 = vehicle(VEHICLE1_ID, DRIVER1_ID, OWNER1_ID);
        vehicle2 = vehicle(VEHICLE2_ID, OWNER1_ID,  OWNER1_ID);

        // Default stub: fleet owner lookups succeed
        when(userRepository.findById(OWNER1_ID)).thenReturn(Optional.of(fleetOwner1));
        when(userRepository.findById(OWNER2_ID)).thenReturn(Optional.of(fleetOwner2));
        when(userRepository.findById(DRIVER1_ID)).thenReturn(Optional.of(driver1));
        when(userRepository.findById(DRIVER2_ID)).thenReturn(Optional.of(driver2));
        when(userRepository.findById(UNASSIGNED_ID)).thenReturn(Optional.of(unassigned));
        when(userRepository.findById(OTHER_DRIVER_ID)).thenReturn(Optional.of(otherDriver));
        when(userRepository.findById(SHIPPER_ID)).thenReturn(Optional.of(shipper));

        when(vehicleRepository.findById(VEHICLE1_ID)).thenReturn(Optional.of(vehicle1));
        when(vehicleRepository.findById(VEHICLE2_ID)).thenReturn(Optional.of(vehicle2));
    }

    // ═════════════════════════════════════════════════════════════════════════
    // validateFleetOwner
    // ═════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("validateFleetOwner")
    class ValidateFleetOwnerTests {

        @Test
        @DisplayName("valid fleet owner returns user")
        void validFleetOwner_returnsUser() {
            User result = service.validateFleetOwner(OWNER1_ID);
            assertThat(result.getId()).isEqualTo(OWNER1_ID);
            assertThat(result.getRole()).isEqualTo(Role.FLEET_OWNER);
        }

        @Test
        @DisplayName("user not found throws FleetResourceNotFoundException")
        void userNotFound_throws() {
            when(userRepository.findById(999L)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> service.validateFleetOwner(999L))
                    .isInstanceOf(FleetException.FleetResourceNotFoundException.class)
                    .hasMessageContaining("999");
        }

        @Test
        @DisplayName("DRIVER role throws FleetAuthorizationException")
        void driverRole_throwsAuthorization() {
            assertThatThrownBy(() -> service.validateFleetOwner(DRIVER1_ID))
                    .isInstanceOf(FleetException.FleetAuthorizationException.class)
                    .hasMessageContaining(String.valueOf(DRIVER1_ID));
        }

        @Test
        @DisplayName("SHIPPER role throws FleetAuthorizationException")
        void shipperRole_throwsAuthorization() {
            assertThatThrownBy(() -> service.validateFleetOwner(SHIPPER_ID))
                    .isInstanceOf(FleetException.FleetAuthorizationException.class);
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // getDashboard
    // ═════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("getDashboard")
    class GetDashboardTests {

        @Test
        @DisplayName("returns correct counts for fleet with drivers, vehicles, and bookings")
        void dashboard_correctCounts() {
            when(userRepository.findByFleetOwnerId(OWNER1_ID))
                    .thenReturn(List.of(driver1, driver2));
            when(vehicleRepository.findByFleetOwnerId(OWNER1_ID))
                    .thenReturn(List.of(vehicle1, vehicle2));

            Booking active = booking(50L, driver1, BookingStatus.ACCEPTED);
            when(bookingRepository.findActiveByDriverIdIn(anyCollection(), anyCollection()))
                    .thenReturn(List.of(active));

            FleetDashboardResponse dash = service.getDashboard(OWNER1_ID);

            assertThat(dash.getFleetOwnerId()).isEqualTo(OWNER1_ID);
            assertThat(dash.getDriverCount()).isEqualTo(2);
            assertThat(dash.getVehicleCount()).isEqualTo(2);
            assertThat(dash.getActiveBookingCount()).isEqualTo(1);
            assertThat(dash.getDrivers()).hasSize(2);
            assertThat(dash.getVehicles()).hasSize(2);
            assertThat(dash.getActiveBookings()).hasSize(1);
        }

        @Test
        @DisplayName("empty fleet returns zero counts, no booking query called")
        void emptyFleet_zeroCountsNoBookingQuery() {
            when(userRepository.findByFleetOwnerId(OWNER1_ID)).thenReturn(Collections.emptyList());
            when(vehicleRepository.findByFleetOwnerId(OWNER1_ID)).thenReturn(Collections.emptyList());

            FleetDashboardResponse dash = service.getDashboard(OWNER1_ID);

            assertThat(dash.getDriverCount()).isZero();
            assertThat(dash.getVehicleCount()).isZero();
            assertThat(dash.getActiveBookingCount()).isZero();
            // booking query must NOT be called when there are no driver IDs
            verify(bookingRepository, never()).findActiveByDriverIdIn(anyCollection(), anyCollection());
        }

        @Test
        @DisplayName("non-fleet-owner user throws FleetAuthorizationException")
        void nonFleetOwner_throwsForDashboard() {
            assertThatThrownBy(() -> service.getDashboard(DRIVER1_ID))
                    .isInstanceOf(FleetException.FleetAuthorizationException.class);
        }

        @Test
        @DisplayName("dashboard summaries contain expected driver fields")
        void driverSummary_hasExpectedFields() {
            when(userRepository.findByFleetOwnerId(OWNER1_ID)).thenReturn(List.of(driver1));
            when(vehicleRepository.findByFleetOwnerId(OWNER1_ID)).thenReturn(Collections.emptyList());

            FleetDashboardResponse dash = service.getDashboard(OWNER1_ID);

            FleetDashboardResponse.DriverSummary ds = dash.getDrivers().get(0);
            assertThat(ds.getId()).isEqualTo(DRIVER1_ID);
            assertThat(ds.getFullName()).isEqualTo(driver1.getFullName());
            assertThat(ds.getIsActive()).isTrue();
        }

        @Test
        @DisplayName("dashboard vehicle summary contains assigned driver ID")
        void vehicleSummary_hasAssignedDriverId() {
            when(userRepository.findByFleetOwnerId(OWNER1_ID)).thenReturn(Collections.emptyList());
            when(vehicleRepository.findByFleetOwnerId(OWNER1_ID)).thenReturn(List.of(vehicle1));

            FleetDashboardResponse dash = service.getDashboard(OWNER1_ID);

            FleetDashboardResponse.VehicleSummary vs = dash.getVehicles().get(0);
            assertThat(vs.getAssignedDriverId()).isEqualTo(DRIVER1_ID);
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // getFleetDrivers / getFleetVehicles
    // ═════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("getFleetDrivers / getFleetVehicles")
    class ListTests {

        @Test
        @DisplayName("getFleetDrivers returns only this fleet's drivers")
        void getFleetDrivers_returnsMappedList() {
            when(userRepository.findByFleetOwnerId(OWNER1_ID)).thenReturn(List.of(driver1, driver2));

            List<FleetDriverResponse> result = service.getFleetDrivers(OWNER1_ID);

            assertThat(result).hasSize(2);
            assertThat(result).extracting(FleetDriverResponse::getId)
                    .containsExactlyInAnyOrder(DRIVER1_ID, DRIVER2_ID);
        }

        @Test
        @DisplayName("getFleetVehicles returns only this fleet's vehicles")
        void getFleetVehicles_returnsMappedList() {
            when(vehicleRepository.findByFleetOwnerId(OWNER1_ID)).thenReturn(List.of(vehicle1, vehicle2));

            List<FleetVehicleResponse> result = service.getFleetVehicles(OWNER1_ID);

            assertThat(result).hasSize(2);
            assertThat(result).extracting(FleetVehicleResponse::getId)
                    .containsExactlyInAnyOrder(VEHICLE1_ID, VEHICLE2_ID);
        }

        @Test
        @DisplayName("non-fleet-owner cannot list drivers")
        void nonFleetOwner_cannotListDrivers() {
            assertThatThrownBy(() -> service.getFleetDrivers(SHIPPER_ID))
                    .isInstanceOf(FleetException.FleetAuthorizationException.class);
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // addDriver
    // ═════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("addDriver")
    class AddDriverTests {

        @Test
        @DisplayName("adds unassigned driver to fleet successfully")
        void addUnassignedDriver_success() {
            when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

            FleetDriverResponse resp = service.addDriver(OWNER1_ID, new AddDriverRequest(UNASSIGNED_ID));

            assertThat(resp.getId()).isEqualTo(UNASSIGNED_ID);
            assertThat(unassigned.getFleetOwnerId()).isEqualTo(OWNER1_ID);
            verify(userRepository).save(unassigned);
        }

        @Test
        @DisplayName("driver not found throws FleetResourceNotFoundException")
        void driverNotFound_throws() {
            when(userRepository.findById(999L)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> service.addDriver(OWNER1_ID, new AddDriverRequest(999L)))
                    .isInstanceOf(FleetException.FleetResourceNotFoundException.class)
                    .hasMessageContaining("999");
        }

        @Test
        @DisplayName("adding a SHIPPER user throws FleetValidationException")
        void addShipper_throwsValidation() {
            assertThatThrownBy(() -> service.addDriver(OWNER1_ID, new AddDriverRequest(SHIPPER_ID)))
                    .isInstanceOf(FleetException.FleetValidationException.class)
                    .hasMessageContaining("not a driver");
        }

        @Test
        @DisplayName("adding FLEET_OWNER as driver throws FleetValidationException")
        void addFleetOwnerAsDriver_throwsValidation() {
            assertThatThrownBy(() -> service.addDriver(OWNER1_ID, new AddDriverRequest(OWNER2_ID)))
                    .isInstanceOf(FleetException.FleetValidationException.class);
        }

        @Test
        @DisplayName("IDOR: cannot poach driver belonging to another fleet — throws ResourceAlreadyAssignedException")
        void idor_cannotPoachDriverFromOtherFleet() {
            // otherDriver.fleetOwnerId = OWNER2_ID, caller is OWNER1_ID
            assertThatThrownBy(() -> service.addDriver(OWNER1_ID, new AddDriverRequest(OTHER_DRIVER_ID)))
                    .isInstanceOf(FleetException.ResourceAlreadyAssignedException.class)
                    .hasMessageContaining("another fleet");
        }

        @Test
        @DisplayName("idempotent: re-adding driver already in same fleet returns current state, no save called")
        void idempotent_driverAlreadyInFleet() {
            // driver1.fleetOwnerId already == OWNER1_ID
            FleetDriverResponse resp = service.addDriver(OWNER1_ID, new AddDriverRequest(DRIVER1_ID));

            assertThat(resp.getId()).isEqualTo(DRIVER1_ID);
            verify(userRepository, never()).save(any());
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // removeDriver
    // ═════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("removeDriver")
    class RemoveDriverTests {

        @Test
        @DisplayName("removes fleet driver, sets fleetOwnerId null")
        void removeDriver_clearsFleetOwner() {
            when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

            service.removeDriver(OWNER1_ID, DRIVER1_ID);

            assertThat(driver1.getFleetOwnerId()).isNull();
            verify(userRepository).save(driver1);
        }

        @Test
        @DisplayName("driver not found throws FleetResourceNotFoundException")
        void driverNotFound_throws() {
            when(userRepository.findById(999L)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> service.removeDriver(OWNER1_ID, 999L))
                    .isInstanceOf(FleetException.FleetResourceNotFoundException.class);
        }

        @Test
        @DisplayName("IDOR: cannot remove another fleet's driver — throws FleetAuthorizationException")
        void idor_cannotRemoveOtherFleetDriver() {
            // otherDriver belongs to OWNER2
            assertThatThrownBy(() -> service.removeDriver(OWNER1_ID, OTHER_DRIVER_ID))
                    .isInstanceOf(FleetException.FleetAuthorizationException.class)
                    .hasMessageContaining("does not belong to your fleet");
        }

        @Test
        @DisplayName("IDOR: cannot remove unassigned driver using another owner ID")
        void idor_cannotRemoveUnassignedDriver() {
            // unassigned.fleetOwnerId == null, so it does not belong to OWNER1_ID
            assertThatThrownBy(() -> service.removeDriver(OWNER1_ID, UNASSIGNED_ID))
                    .isInstanceOf(FleetException.FleetAuthorizationException.class);
        }

        @Test
        @DisplayName("non-fleet-owner attempting removeDriver throws FleetAuthorizationException")
        void nonFleetOwner_cannotRemoveDriver() {
            assertThatThrownBy(() -> service.removeDriver(SHIPPER_ID, DRIVER1_ID))
                    .isInstanceOf(FleetException.FleetAuthorizationException.class);
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // addVehicle
    // ═════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("addVehicle")
    class AddVehicleTests {

        @Test
        @DisplayName("creates vehicle with fleetOwnerId set to caller")
        void addVehicle_setsFleetOwner() {
            when(vehicleRepository.existsByPlateNumber("NEW-001")).thenReturn(false);
            Vehicle saved = vehicle(400L, OWNER1_ID, OWNER1_ID);
            saved.setPlateNumber("NEW-001");
            when(vehicleRepository.save(any(Vehicle.class))).thenReturn(saved);

            AddVehicleRequest req = AddVehicleRequest.builder()
                    .truckType("Truck")
                    .plateNumber("NEW-001")
                    .capacity(5.0)
                    .currentCity("Addis Ababa")
                    .build();

            FleetVehicleResponse resp = service.addVehicle(OWNER1_ID, req);

            assertThat(resp.getId()).isEqualTo(400L);
            verify(vehicleRepository).save(argThat(v ->
                    OWNER1_ID.equals(v.getFleetOwnerId()) &&
                    "NEW-001".equals(v.getPlateNumber())
            ));
        }

        @Test
        @DisplayName("duplicate plate number throws FleetValidationException")
        void duplicatePlate_throwsValidation() {
            when(vehicleRepository.existsByPlateNumber("DUP-001")).thenReturn(true);

            AddVehicleRequest req = AddVehicleRequest.builder()
                    .truckType("Truck").plateNumber("DUP-001").capacity(5.0).currentCity("Addis Ababa")
                    .build();

            assertThatThrownBy(() -> service.addVehicle(OWNER1_ID, req))
                    .isInstanceOf(FleetException.FleetValidationException.class)
                    .hasMessageContaining("Plate number already taken");
        }

        @Test
        @DisplayName("invalid vehicleCategory throws FleetValidationException")
        void invalidCategory_throwsValidation() {
            when(vehicleRepository.existsByPlateNumber("CAT-001")).thenReturn(false);

            AddVehicleRequest req = AddVehicleRequest.builder()
                    .truckType("Truck").plateNumber("CAT-001").capacity(5.0)
                    .currentCity("Addis Ababa").vehicleCategory("INVALID")
                    .build();

            assertThatThrownBy(() -> service.addVehicle(OWNER1_ID, req))
                    .isInstanceOf(FleetException.FleetValidationException.class)
                    .hasMessageContaining("Invalid vehicle category");
        }

        @Test
        @DisplayName("IDOR: assigning a non-fleet driver during vehicle creation throws FleetAuthorizationException")
        void idor_addVehicleWithOtherFleetDriver() {
            when(vehicleRepository.existsByPlateNumber("X-001")).thenReturn(false);

            // OTHER_DRIVER_ID belongs to OWNER2, not OWNER1
            AddVehicleRequest req = AddVehicleRequest.builder()
                    .truckType("Truck").plateNumber("X-001").capacity(5.0)
                    .currentCity("Addis Ababa").driverId(OTHER_DRIVER_ID)
                    .build();

            assertThatThrownBy(() -> service.addVehicle(OWNER1_ID, req))
                    .isInstanceOf(FleetException.FleetAuthorizationException.class)
                    .hasMessageContaining("does not belong to your fleet");
        }

        @Test
        @DisplayName("creates vehicle without assigned driver, userId defaults to fleetOwnerId")
        void noDriverId_userIdDefaultsToFleetOwner() {
            when(vehicleRepository.existsByPlateNumber("ND-001")).thenReturn(false);
            when(vehicleRepository.save(any(Vehicle.class))).thenAnswer(inv -> {
                Vehicle v = inv.getArgument(0);
                v = Vehicle.builder()
                        .id(500L).userId(v.getUserId()).fleetOwnerId(v.getFleetOwnerId())
                        .truckType(v.getTruckType()).plateNumber(v.getPlateNumber())
                        .capacity(v.getCapacity()).currentCity(v.getCurrentCity())
                        .availabilityStatus("available").build();
                return v;
            });

            AddVehicleRequest req = AddVehicleRequest.builder()
                    .truckType("Truck").plateNumber("ND-001").capacity(5.0).currentCity("Hawassa")
                    .build();

            FleetVehicleResponse resp = service.addVehicle(OWNER1_ID, req);

            assertThat(resp.getId()).isEqualTo(500L);
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // assignVehicle
    // ═════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("assignVehicle")
    class AssignVehicleTests {

        @Test
        @DisplayName("assigns fleet vehicle to active fleet driver")
        void assignVehicle_success() {
            when(vehicleRepository.save(any(Vehicle.class))).thenAnswer(inv -> inv.getArgument(0));

            FleetVehicleResponse resp = service.assignVehicle(OWNER1_ID, VEHICLE2_ID,
                    new AssignVehicleRequest(DRIVER2_ID));

            assertThat(resp.getAssignedDriverId()).isEqualTo(DRIVER2_ID);
            assertThat(vehicle2.getUserId()).isEqualTo(DRIVER2_ID);
            verify(vehicleRepository).save(vehicle2);
        }

        @Test
        @DisplayName("vehicle not found throws FleetResourceNotFoundException")
        void vehicleNotFound_throws() {
            when(vehicleRepository.findById(999L)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> service.assignVehicle(OWNER1_ID, 999L,
                    new AssignVehicleRequest(DRIVER1_ID)))
                    .isInstanceOf(FleetException.FleetResourceNotFoundException.class)
                    .hasMessageContaining("Vehicle not found");
        }

        @Test
        @DisplayName("driver not found throws FleetResourceNotFoundException")
        void driverNotFound_throws() {
            when(userRepository.findById(999L)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> service.assignVehicle(OWNER1_ID, VEHICLE1_ID,
                    new AssignVehicleRequest(999L)))
                    .isInstanceOf(FleetException.FleetResourceNotFoundException.class)
                    .hasMessageContaining("Driver not found");
        }

        @Test
        @DisplayName("IDOR: cannot assign another fleet's vehicle — throws FleetAuthorizationException")
        void idor_cannotAssignOtherFleetVehicle() {
            // vehicle1 has fleetOwnerId = OWNER1_ID; caller is OWNER2_ID
            assertThatThrownBy(() -> service.assignVehicle(OWNER2_ID, VEHICLE1_ID,
                    new AssignVehicleRequest(DRIVER1_ID)))
                    .isInstanceOf(FleetException.FleetAuthorizationException.class)
                    .hasMessageContaining("does not belong to your fleet");
        }

        @Test
        @DisplayName("IDOR: cannot assign another fleet's driver to own vehicle — throws FleetAuthorizationException")
        void idor_cannotAssignOtherFleetDriver() {
            // vehicle2 belongs to OWNER1, but otherDriver belongs to OWNER2
            assertThatThrownBy(() -> service.assignVehicle(OWNER1_ID, VEHICLE2_ID,
                    new AssignVehicleRequest(OTHER_DRIVER_ID)))
                    .isInstanceOf(FleetException.FleetAuthorizationException.class)
                    .hasMessageContaining("does not belong to your fleet");
        }

        @Test
        @DisplayName("KYC rule (M8A): inactive driver cannot be assigned — throws FleetValidationException")
        void kycRule_inactiveDriverRejected() {
            driver2.setIsActive(false);

            assertThatThrownBy(() -> service.assignVehicle(OWNER1_ID, VEHICLE2_ID,
                    new AssignVehicleRequest(DRIVER2_ID)))
                    .isInstanceOf(FleetException.FleetValidationException.class)
                    .hasMessageContaining("not active")
                    .hasMessageContaining("KYC approval");
        }

        @Test
        @DisplayName("KYC rule (M8A): active driver is assigned successfully")
        void kycRule_activeDriverAllowed() {
            driver2.setIsActive(true);
            when(vehicleRepository.save(any(Vehicle.class))).thenAnswer(inv -> inv.getArgument(0));

            assertThatCode(() -> service.assignVehicle(OWNER1_ID, VEHICLE2_ID,
                    new AssignVehicleRequest(DRIVER2_ID)))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("non-fleet-owner cannot assign vehicle — throws FleetAuthorizationException")
        void nonFleetOwner_cannotAssign() {
            assertThatThrownBy(() -> service.assignVehicle(SHIPPER_ID, VEHICLE1_ID,
                    new AssignVehicleRequest(DRIVER1_ID)))
                    .isInstanceOf(FleetException.FleetAuthorizationException.class);
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Commission consistency (M7 rule)
    // ═════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Commission consistency (M7)")
    class CommissionConsistencyTests {

        @Test
        @DisplayName("FleetServiceImpl has no CommissionService dependency — commission untouched")
        void fleetService_doesNotHaveCommissionDependency() {
            // If FleetServiceImpl were ever injected with CommissionService, this constructor
            // would fail to compile with the 3-arg constructor used in setUp().
            // This test documents the design invariant: fleet management does NOT
            // recalculate or override commission. That remains in CommissionServiceImpl (M7).
            assertThat(service).isNotNull();
            // No commission-related method exists on FleetService
            assertThat(FleetServiceImpl.class.getDeclaredFields())
                    .extracting(java.lang.reflect.Field::getName)
                    .doesNotContain("commissionService");
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Helpers
    // ═════════════════════════════════════════════════════════════════════════

    private User user(Long id, Role role, Long fleetOwnerId, boolean active) {
        User u = new User();
        u.setId(id);
        u.setFullName("User " + id);
        u.setPhone("09000000" + id);
        u.setRole(role);
        u.setFleetOwnerId(fleetOwnerId);
        u.setIsActive(active);
        u.setVerificationStatus(false);
        return u;
    }

    private Vehicle vehicle(Long id, Long userId, Long fleetOwnerId) {
        return Vehicle.builder()
                .id(id)
                .userId(userId)
                .fleetOwnerId(fleetOwnerId)
                .truckType("Truck")
                .plateNumber("PLATE-" + id)
                .capacity(5.0)
                .currentCity("Addis Ababa")
                .availabilityStatus("available")
                .build();
    }

    private Booking booking(Long id, User driver, BookingStatus status) {
        Booking b = new Booking();
        b.setId(id);
        b.setDriver(driver);
        b.setBookingStatus(status);
        return b;
    }
}
