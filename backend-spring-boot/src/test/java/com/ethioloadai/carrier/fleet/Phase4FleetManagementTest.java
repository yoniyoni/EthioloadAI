package com.ethioloadai.carrier.fleet;

import com.ethioloadai.carrier.fleet.dto.*;
import com.ethioloadai.carrier.fleet.entity.*;
import com.ethioloadai.carrier.fleet.exception.CarrierFleetException;
import com.ethioloadai.user.entity.User;
import com.ethioloadai.vehicle.entity.Vehicle;
import org.junit.jupiter.api.*;
import org.springframework.http.MediaType;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Phase 4 comprehensive integration test suite.
 *
 * <p>Covers: carrier drivers, company vehicles, assignments, availability,
 * tenant isolation (IDOR), role-based access, and regression for Phases 1–3.
 */
class Phase4FleetManagementTest extends FleetTestSupport {

    // ══════════════════════════════════════════════════════════════════════════
    // 1. Carrier Driver — basics
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void ownerCanAddDriverToCompany() {
        User newDriver = mkUser(User.Role.DRIVER);
        var req = new AddCarrierDriverRequest();
        req.setDriverUserId(newDriver.getId());
        var resp = fleetService.addDriver(abcCompanyId, req, abcOwner);
        assertThat(resp.getDriverUserId()).isEqualTo(newDriver.getId());
        assertThat(resp.getStatus()).isEqualTo(CarrierDriverStatus.ACTIVE);
        assertThat(resp.getAvailability()).isEqualTo(DriverAvailability.AVAILABLE);
    }

    @Test
    void fleetManagerCanAddDriver() {
        User newDriver = mkUser(User.Role.DRIVER);
        var req = new AddCarrierDriverRequest();
        req.setDriverUserId(newDriver.getId());
        var resp = fleetService.addDriver(abcCompanyId, req, abcFm);
        assertThat(resp.getId()).isNotNull();
    }

    @Test
    void cannotAddNonDriverUser() {
        var req = new AddCarrierDriverRequest();
        req.setDriverUserId(abcFm.getId()); // FLEET_MANAGER, not DRIVER
        assertThatThrownBy(() -> fleetService.addDriver(abcCompanyId, req, abcOwner))
                .isInstanceOf(CarrierFleetException.class)
                .hasMessageContaining("role DRIVER");
    }

    @Test
    void addingExistingDriverIsIdempotent() {
        var req = new AddCarrierDriverRequest();
        req.setDriverUserId(abcDriver1.getId());
        var resp1 = fleetService.addDriver(abcCompanyId, req, abcOwner);
        var resp2 = fleetService.addDriver(abcCompanyId, req, abcOwner);
        assertThat(resp1.getId()).isEqualTo(resp2.getId());
    }

    @Test
    void ownerListsOwnActiveDrivers() {
        var page = fleetService.listDrivers(abcCompanyId, "ACTIVE",
                org.springframework.data.domain.PageRequest.of(0, 20));
        assertThat(page.getTotalElements()).isGreaterThanOrEqualTo(2);
        assertThat(page.getContent()).allMatch(d -> d.getStatus() == CarrierDriverStatus.ACTIVE);
    }

    @Test
    void fleetManagerListsOwnDrivers() {
        var page = fleetService.listDrivers(abcCompanyId, "ACTIVE",
                org.springframework.data.domain.PageRequest.of(0, 20));
        assertThat(page.getTotalElements()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void getDriverReturnsCorrectInfo() {
        var resp = fleetService.getDriver(abcCompanyId, abcCd1.getId());
        assertThat(resp.getDriverUserId()).isEqualTo(abcDriver1.getId());
        assertThat(resp.getFullName()).isEqualTo(abcDriver1.getFullName());
    }

    @Test
    void removeDriverDoesNotDeleteUserAccount() {
        Long driverUserId = abcDriver1.getId();
        fleetService.removeDriver(abcCompanyId, abcCd1.getId(), abcOwner);
        // User account must still exist
        assertThat(users.findById(driverUserId)).isPresent();
        // But carrier_driver row is REMOVED
        var cd = driverRepo.findById(abcCd1.getId()).orElseThrow();
        assertThat(cd.getStatus()).isEqualTo(CarrierDriverStatus.REMOVED);
        assertThat(cd.getLeftAt()).isNotNull();
    }

    @Test
    void deactivateAndActivateDriver() {
        var deactivated = fleetService.deactivateDriver(abcCompanyId, abcCd1.getId(), abcOwner);
        assertThat(deactivated.getStatus()).isEqualTo(CarrierDriverStatus.INACTIVE);

        var reactivated = fleetService.activateDriver(abcCompanyId, abcCd1.getId(), abcOwner);
        assertThat(reactivated.getStatus()).isEqualTo(CarrierDriverStatus.ACTIVE);
        assertThat(reactivated.getAvailability()).isEqualTo(DriverAvailability.AVAILABLE);
    }

    @Test
    void cannotActivateRemovedDriver() {
        fleetService.removeDriver(abcCompanyId, abcCd1.getId(), abcOwner);
        assertThatThrownBy(() -> fleetService.activateDriver(abcCompanyId, abcCd1.getId(), abcOwner))
                .isInstanceOf(CarrierFleetException.class)
                .hasMessageContaining("REMOVED");
    }

    @Test
    void updateDriverNotes() {
        var req = new UpdateCarrierDriverRequest();
        req.setNotes("Experienced long-haul driver");
        var resp = fleetService.updateDriver(abcCompanyId, abcCd1.getId(), req, abcOwner);
        assertThat(resp.getNotes()).isEqualTo("Experienced long-haul driver");
    }

    @Test
    void cannotManuallySetAssignedAvailability() {
        var req = new UpdateCarrierDriverRequest();
        req.setAvailability(DriverAvailability.ASSIGNED);
        assertThatThrownBy(() -> fleetService.updateDriver(abcCompanyId, abcCd1.getId(), req, abcOwner))
                .isInstanceOf(CarrierFleetException.class)
                .hasMessageContaining("system-managed");
    }

    @Test
    void setDriverUnavailableManually() {
        var req = new UpdateCarrierDriverRequest();
        req.setAvailability(DriverAvailability.UNAVAILABLE);
        var resp = fleetService.updateDriver(abcCompanyId, abcCd1.getId(), req, abcOwner);
        assertThat(resp.getAvailability()).isEqualTo(DriverAvailability.UNAVAILABLE);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 2. Company Vehicles
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void ownerListsOwnVehicles() {
        var page = fleetService.listVehicles(abcCompanyId,
                org.springframework.data.domain.PageRequest.of(0, 20));
        assertThat(page.getTotalElements()).isGreaterThanOrEqualTo(2);
        assertThat(page.getContent()).allMatch(v -> v.getCarrierCompanyId().equals(abcCompanyId));
    }

    @Test
    void getVehicleReturnsCorrectCompany() {
        var resp = fleetService.getVehicle(abcCompanyId, abcVehicle1.getId());
        assertThat(resp.getCarrierCompanyId()).isEqualTo(abcCompanyId);
        assertThat(resp.getPlateNumber()).isEqualTo(abcVehicle1.getPlateNumber());
    }

    @Test
    void registerVehicleSetsCorrectDefaults() {
        var req = new RegisterCompanyVehicleRequest();
        req.setTruckType("Pickup");
        req.setPlateNumber("NEW-" + java.util.UUID.randomUUID().toString().substring(0, 4).toUpperCase());
        req.setCapacity(3.0);
        var resp = fleetService.registerVehicle(abcCompanyId, req, abcOwner);
        assertThat(resp.getCarrierCompanyId()).isEqualTo(abcCompanyId);
        assertThat(resp.getApprovalStatus()).isEqualTo("UNDER_REVIEW");
        assertThat(resp.getAvailabilityStatus()).isEqualTo("available");
    }

    @Test
    void duplicatePlateRejected() {
        var req = new RegisterCompanyVehicleRequest();
        req.setTruckType("Truck");
        req.setPlateNumber(abcVehicle1.getPlateNumber()); // existing plate
        req.setCapacity(5.0);
        assertThatThrownBy(() -> fleetService.registerVehicle(abcCompanyId, req, abcOwner))
                .isInstanceOf(CarrierFleetException.class)
                .hasMessageContaining("Plate number already registered");
    }

    @Test
    void setVehicleMaintenance() {
        var resp = fleetService.setVehicleAvailability(
                abcCompanyId, abcVehicle1.getId(), VehicleAvailability.MAINTENANCE, abcOwner);
        assertThat(resp.getAvailabilityStatus()).isEqualTo("maintenance");
    }

    @Test
    void cannotManuallySetVehicleAssigned() {
        assertThatThrownBy(() -> fleetService.setVehicleAvailability(
                abcCompanyId, abcVehicle1.getId(), VehicleAvailability.ASSIGNED, abcOwner))
                .isInstanceOf(CarrierFleetException.class)
                .hasMessageContaining("system-managed");
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 3. Driver ↔ Vehicle Assignments
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void successfulAssignment() {
        abcVehicle1 = approveVehicle(abcVehicle1);
        var req = new CreateAssignmentRequest();
        req.setCarrierDriverId(abcCd1.getId());
        req.setVehicleId(abcVehicle1.getId());
        var resp = fleetService.assign(abcCompanyId, req, abcOwner);
        assertThat(resp.getStatus()).isEqualTo(AssignmentStatus.ACTIVE);
        assertThat(resp.getAssignedBy()).isEqualTo(abcOwner.getId());
        assertThat(resp.getCarrierCompanyId()).isEqualTo(abcCompanyId);

        // Driver becomes ASSIGNED
        var cd = driverRepo.findById(abcCd1.getId()).orElseThrow();
        assertThat(cd.getAvailability()).isEqualTo(DriverAvailability.ASSIGNED);

        // Vehicle becomes assigned
        var v = vehicleRepo.findById(abcVehicle1.getId()).orElseThrow();
        assertThat(v.getAvailabilityStatus()).isEqualTo("assigned");
    }

    @Test
    void assignmentByFleetManager() {
        abcVehicle1 = approveVehicle(abcVehicle1);
        var req = new CreateAssignmentRequest();
        req.setCarrierDriverId(abcCd1.getId());
        req.setVehicleId(abcVehicle1.getId());
        var resp = fleetService.assign(abcCompanyId, req, abcFm);
        assertThat(resp.getStatus()).isEqualTo(AssignmentStatus.ACTIVE);
        assertThat(resp.getAssignedBy()).isEqualTo(abcFm.getId());
    }

    @Test
    void cannotAssignUnapprovedVehicle() {
        // abcVehicle1 is UNDER_REVIEW (default)
        var req = new CreateAssignmentRequest();
        req.setCarrierDriverId(abcCd1.getId());
        req.setVehicleId(abcVehicle1.getId());
        assertThatThrownBy(() -> fleetService.assign(abcCompanyId, req, abcOwner))
                .isInstanceOf(CarrierFleetException.class)
                .hasMessageContaining("APPROVED");
    }

    @Test
    void cannotAssignInactiveDriver() {
        abcVehicle1 = approveVehicle(abcVehicle1);
        fleetService.deactivateDriver(abcCompanyId, abcCd1.getId(), abcOwner);

        var req = new CreateAssignmentRequest();
        req.setCarrierDriverId(abcCd1.getId());
        req.setVehicleId(abcVehicle1.getId());
        assertThatThrownBy(() -> fleetService.assign(abcCompanyId, req, abcOwner))
                .isInstanceOf(CarrierFleetException.class)
                .hasMessageContaining("ACTIVE");
    }

    @Test
    void cannotAssignUnavailableDriver() {
        abcVehicle1 = approveVehicle(abcVehicle1);
        var updReq = new UpdateCarrierDriverRequest();
        updReq.setAvailability(DriverAvailability.UNAVAILABLE);
        fleetService.updateDriver(abcCompanyId, abcCd1.getId(), updReq, abcOwner);

        var req = new CreateAssignmentRequest();
        req.setCarrierDriverId(abcCd1.getId());
        req.setVehicleId(abcVehicle1.getId());
        assertThatThrownBy(() -> fleetService.assign(abcCompanyId, req, abcOwner))
                .isInstanceOf(CarrierFleetException.class)
                .hasMessageContaining("UNAVAILABLE");
    }

    @Test
    void cannotAssignVehicleUnderMaintenance() {
        abcVehicle1 = approveVehicle(abcVehicle1);
        fleetService.setVehicleAvailability(abcCompanyId, abcVehicle1.getId(),
                VehicleAvailability.MAINTENANCE, abcOwner);

        var req = new CreateAssignmentRequest();
        req.setCarrierDriverId(abcCd1.getId());
        req.setVehicleId(abcVehicle1.getId());
        assertThatThrownBy(() -> fleetService.assign(abcCompanyId, req, abcOwner))
                .isInstanceOf(CarrierFleetException.class)
                .hasMessageContaining("MAINTENANCE");
    }

    @Test
    void endAssignmentRestoresAvailability() {
        abcVehicle1 = approveVehicle(abcVehicle1);
        var assignReq = new CreateAssignmentRequest();
        assignReq.setCarrierDriverId(abcCd1.getId());
        assignReq.setVehicleId(abcVehicle1.getId());
        var a = fleetService.assign(abcCompanyId, assignReq, abcOwner);
        assertThat(a.getStatus()).isEqualTo(AssignmentStatus.ACTIVE);

        // End it
        var endReq = new EndAssignmentRequest();
        endReq.setNotes("Job complete");
        var ended = fleetService.endAssignment(abcCompanyId, a.getId(), endReq, abcOwner);
        assertThat(ended.getStatus()).isEqualTo(AssignmentStatus.ENDED);
        assertThat(ended.getEndedAt()).isNotNull();

        // Driver availability restored
        var cd = driverRepo.findById(abcCd1.getId()).orElseThrow();
        assertThat(cd.getAvailability()).isEqualTo(DriverAvailability.AVAILABLE);

        // Vehicle availability restored
        var v = vehicleRepo.findById(abcVehicle1.getId()).orElseThrow();
        assertThat(v.getAvailabilityStatus()).isEqualTo("available");
    }

    @Test
    void reassignmentEndsOldAssignment() {
        abcVehicle1 = approveVehicle(abcVehicle1);
        abcVehicle2 = approveVehicle(abcVehicle2);

        var req1 = new CreateAssignmentRequest();
        req1.setCarrierDriverId(abcCd1.getId());
        req1.setVehicleId(abcVehicle1.getId());
        var first = fleetService.assign(abcCompanyId, req1, abcOwner);
        assertThat(first.getStatus()).isEqualTo(AssignmentStatus.ACTIVE);

        // Reassign to vehicle2 — should end first assignment
        var req2 = new CreateAssignmentRequest();
        req2.setCarrierDriverId(abcCd1.getId());
        req2.setVehicleId(abcVehicle2.getId());
        var second = fleetService.assign(abcCompanyId, req2, abcOwner);
        assertThat(second.getStatus()).isEqualTo(AssignmentStatus.ACTIVE);
        assertThat(second.getVehicleId()).isEqualTo(abcVehicle2.getId());

        // Old assignment is now ENDED
        var oldA = assignmentRepo.findById(first.getId()).orElseThrow();
        assertThat(oldA.getStatus()).isEqualTo(AssignmentStatus.ENDED);

        // Vehicle1 is available again
        var v1 = vehicleRepo.findById(abcVehicle1.getId()).orElseThrow();
        assertThat(v1.getAvailabilityStatus()).isEqualTo("available");
    }

    @Test
    void assignmentHistoryPreservedAfterEnd() {
        abcVehicle1 = approveVehicle(abcVehicle1);
        var req = new CreateAssignmentRequest();
        req.setCarrierDriverId(abcCd1.getId());
        req.setVehicleId(abcVehicle1.getId());
        var a = fleetService.assign(abcCompanyId, req, abcOwner);
        fleetService.endAssignment(abcCompanyId, a.getId(), null, abcOwner);

        var history = fleetService.driverAssignmentHistory(abcCompanyId, abcCd1.getId());
        assertThat(history).isNotEmpty();
        assertThat(history.get(0).getId()).isEqualTo(a.getId());
        assertThat(history.get(0).getStatus()).isEqualTo(AssignmentStatus.ENDED);
    }

    @Test
    void cannotEndAlreadyEndedAssignment() {
        abcVehicle1 = approveVehicle(abcVehicle1);
        var req = new CreateAssignmentRequest();
        req.setCarrierDriverId(abcCd1.getId());
        req.setVehicleId(abcVehicle1.getId());
        var a = fleetService.assign(abcCompanyId, req, abcOwner);
        fleetService.endAssignment(abcCompanyId, a.getId(), null, abcOwner);

        assertThatThrownBy(() -> fleetService.endAssignment(abcCompanyId, a.getId(), null, abcOwner))
                .isInstanceOf(CarrierFleetException.class)
                .hasMessageContaining("already");
    }

    @Test
    void deactivatingDriverEndsActiveAssignment() {
        abcVehicle1 = approveVehicle(abcVehicle1);
        var req = new CreateAssignmentRequest();
        req.setCarrierDriverId(abcCd1.getId());
        req.setVehicleId(abcVehicle1.getId());
        var a = fleetService.assign(abcCompanyId, req, abcOwner);

        // Deactivate driver — should auto-end assignment
        fleetService.deactivateDriver(abcCompanyId, abcCd1.getId(), abcOwner);

        var endedA = assignmentRepo.findById(a.getId()).orElseThrow();
        assertThat(endedA.getStatus()).isEqualTo(AssignmentStatus.ENDED);

        var v = vehicleRepo.findById(abcVehicle1.getId()).orElseThrow();
        assertThat(v.getAvailabilityStatus()).isEqualTo("available");
    }

    @Test
    void removingDriverEndsActiveAssignment() {
        abcVehicle1 = approveVehicle(abcVehicle1);
        var req = new CreateAssignmentRequest();
        req.setCarrierDriverId(abcCd1.getId());
        req.setVehicleId(abcVehicle1.getId());
        var a = fleetService.assign(abcCompanyId, req, abcOwner);

        fleetService.removeDriver(abcCompanyId, abcCd1.getId(), abcOwner);

        var endedA = assignmentRepo.findById(a.getId()).orElseThrow();
        assertThat(endedA.getStatus()).isEqualTo(AssignmentStatus.ENDED);
    }

    @Test
    void listAssignmentsPaginated() {
        abcVehicle1 = approveVehicle(abcVehicle1);
        var req = new CreateAssignmentRequest();
        req.setCarrierDriverId(abcCd1.getId());
        req.setVehicleId(abcVehicle1.getId());
        fleetService.assign(abcCompanyId, req, abcOwner);

        var page = fleetService.listAssignments(abcCompanyId,
                org.springframework.data.domain.PageRequest.of(0, 20));
        assertThat(page.getTotalElements()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void vehicleAssignmentHistoryScoped() {
        abcVehicle1 = approveVehicle(abcVehicle1);
        var req = new CreateAssignmentRequest();
        req.setCarrierDriverId(abcCd1.getId());
        req.setVehicleId(abcVehicle1.getId());
        fleetService.assign(abcCompanyId, req, abcOwner);

        List<AssignmentResponse> history = fleetService.vehicleAssignmentHistory(
                abcCompanyId, abcVehicle1.getId());
        assertThat(history).isNotEmpty();
        assertThat(history).allMatch(a -> a.getVehicleId().equals(abcVehicle1.getId()));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 4. Tenant Isolation — Driver IDOR
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void abcCannotGetXyzDriver() {
        // abcOwner tries to get XYZ's carrier driver using ABC companyId — should fail
        assertThatThrownBy(() -> fleetService.getDriver(abcCompanyId, xyzCd.getId()))
                .isInstanceOf(CarrierFleetException.class);
    }

    @Test
    void abcCannotDeactivateXyzDriver() {
        assertThatThrownBy(() ->
                fleetService.deactivateDriver(abcCompanyId, xyzCd.getId(), abcOwner))
                .isInstanceOf(CarrierFleetException.class);
    }

    @Test
    void abcCannotRemoveXyzDriver() {
        assertThatThrownBy(() ->
                fleetService.removeDriver(abcCompanyId, xyzCd.getId(), abcOwner))
                .isInstanceOf(CarrierFleetException.class);
    }

    @Test
    void xyzCannotGetAbcDriver() {
        assertThatThrownBy(() -> fleetService.getDriver(xyzCompanyId, abcCd1.getId()))
                .isInstanceOf(CarrierFleetException.class);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 5. Tenant Isolation — Vehicle IDOR
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void abcCannotGetXyzVehicle() {
        assertThatThrownBy(() -> fleetService.getVehicle(abcCompanyId, xyzVehicle.getId()))
                .isInstanceOf(CarrierFleetException.class);
    }

    @Test
    void xyzCannotGetAbcVehicle() {
        assertThatThrownBy(() -> fleetService.getVehicle(xyzCompanyId, abcVehicle1.getId()))
                .isInstanceOf(CarrierFleetException.class);
    }

    @Test
    void abcCannotUpdateXyzVehicle() {
        var req = new UpdateCompanyVehicleRequest();
        req.setTruckType("Hacked");
        assertThatThrownBy(() -> fleetService.updateVehicle(abcCompanyId, xyzVehicle.getId(), req, abcOwner))
                .isInstanceOf(CarrierFleetException.class);
    }

    @Test
    void abcListVehiclesOnlyShowsAbcVehicles() {
        var page = fleetService.listVehicles(abcCompanyId,
                org.springframework.data.domain.PageRequest.of(0, 100));
        assertThat(page.getContent())
                .noneMatch(v -> v.getCarrierCompanyId().equals(xyzCompanyId));
    }

    @Test
    void xyzListVehiclesOnlyShowsXyzVehicles() {
        var page = fleetService.listVehicles(xyzCompanyId,
                org.springframework.data.domain.PageRequest.of(0, 100));
        assertThat(page.getContent())
                .noneMatch(v -> v.getCarrierCompanyId().equals(abcCompanyId));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 6. Cross-company Assignment IDOR
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void abcDriverCannotBeAssignedToXyzVehicle() {
        xyzVehicle = approveVehicle(xyzVehicle);
        // ABC carrier_driver_id + XYZ vehicle_id, scoped to ABC company
        var req = new CreateAssignmentRequest();
        req.setCarrierDriverId(abcCd1.getId());
        req.setVehicleId(xyzVehicle.getId()); // XYZ vehicle
        assertThatThrownBy(() -> fleetService.assign(abcCompanyId, req, abcOwner))
                .isInstanceOf(CarrierFleetException.class);
    }

    @Test
    void xyzDriverCannotBeAssignedToAbcVehicle() {
        abcVehicle1 = approveVehicle(abcVehicle1);
        var req = new CreateAssignmentRequest();
        req.setCarrierDriverId(xyzCd.getId()); // XYZ driver
        req.setVehicleId(abcVehicle1.getId());
        // Scoped to ABC — XYZ driver id won't be found in ABC
        assertThatThrownBy(() -> fleetService.assign(abcCompanyId, req, abcOwner))
                .isInstanceOf(CarrierFleetException.class);
    }

    @Test
    void xyzCannotEndAbcAssignment() {
        abcVehicle1 = approveVehicle(abcVehicle1);
        var req = new CreateAssignmentRequest();
        req.setCarrierDriverId(abcCd1.getId());
        req.setVehicleId(abcVehicle1.getId());
        var a = fleetService.assign(abcCompanyId, req, abcOwner);

        // XYZ owner tries to end ABC's assignment using XYZ company ID
        assertThatThrownBy(() ->
                fleetService.endAssignment(xyzCompanyId, a.getId(), null, xyzOwner))
                .isInstanceOf(CarrierFleetException.class);
    }

    @Test
    void abcCannotGetXyzAssignment() {
        xyzVehicle = approveVehicle(xyzVehicle);
        var req = new CreateAssignmentRequest();
        req.setCarrierDriverId(xyzCd.getId());
        req.setVehicleId(xyzVehicle.getId());
        var a = fleetService.assign(xyzCompanyId, req, xyzOwner);

        // ABC tries to read XYZ assignment
        assertThatThrownBy(() -> fleetService.getAssignment(abcCompanyId, a.getId()))
                .isInstanceOf(CarrierFleetException.class);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 7. HTTP endpoint authorization (MockMvc)
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void dispatcherCannotAddDriver() throws Exception {
        // Use a valid body with a real driverUserId so authorization is reached
        // (Bean Validation runs before method security when body is invalid)
        var req = new AddCarrierDriverRequest();
        req.setDriverUserId(abcDriver1.getId());
        mvc.perform(post(fleet(abcCompanyId) + "/drivers")
                        .header("Authorization", auth(abcDispatcher))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(req)))
                .andExpect(status().isForbidden());
    }

    @Test
    void dispatcherCanReadDrivers() throws Exception {
        mvc.perform(get(fleet(abcCompanyId) + "/drivers")
                        .header("Authorization", auth(abcDispatcher)))
                .andExpect(status().isOk());
    }

    @Test
    void dispatcherCannotRegisterVehicle() throws Exception {
        var req = new RegisterCompanyVehicleRequest();
        req.setTruckType("Truck");
        req.setPlateNumber("DISP-001");
        req.setCapacity(5.0);
        mvc.perform(post(fleet(abcCompanyId) + "/vehicles")
                        .header("Authorization", auth(abcDispatcher))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(req)))
                .andExpect(status().isForbidden());
    }

    @Test
    void dispatcherCanReadVehicles() throws Exception {
        mvc.perform(get(fleet(abcCompanyId) + "/vehicles")
                        .header("Authorization", auth(abcDispatcher)))
                .andExpect(status().isOk());
    }

    @Test
    void dispatcherCannotAssign() throws Exception {
        // Use a valid body so authorization check is reached before validation
        var req = new CreateAssignmentRequest();
        req.setCarrierDriverId(abcCd1.getId());
        req.setVehicleId(abcVehicle1.getId());
        mvc.perform(post(fleet(abcCompanyId) + "/assignments")
                        .header("Authorization", auth(abcDispatcher))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(req)))
                .andExpect(status().isForbidden());
    }

    @Test
    void dispatcherCanReadAssignments() throws Exception {
        mvc.perform(get(fleet(abcCompanyId) + "/assignments")
                        .header("Authorization", auth(abcDispatcher)))
                .andExpect(status().isOk());
    }

    @Test
    void plainDriverCannotListFleet() throws Exception {
        mvc.perform(get(fleet(abcCompanyId) + "/vehicles")
                        .header("Authorization", auth(abcDriver1)))
                .andExpect(status().isForbidden());
    }

    @Test
    void xyzCannotReadAbcDriversViaHttp() throws Exception {
        mvc.perform(get(fleet(abcCompanyId) + "/drivers")
                        .header("Authorization", auth(xyzOwner)))
                .andExpect(status().isForbidden());
    }

    @Test
    void xyzCannotReadAbcVehiclesViaHttp() throws Exception {
        mvc.perform(get(fleet(abcCompanyId) + "/vehicles")
                        .header("Authorization", auth(xyzOwner)))
                .andExpect(status().isForbidden());
    }

    @Test
    void unauthenticatedFleetDenied() throws Exception {
        mvc.perform(get(fleet(abcCompanyId) + "/drivers"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get(fleet(abcCompanyId) + "/vehicles"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get(fleet(abcCompanyId) + "/assignments"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void fleetManagerCanRegisterVehicleViaHttp() throws Exception {
        var req = new RegisterCompanyVehicleRequest();
        req.setTruckType("Truck");
        req.setPlateNumber("FM-" + java.util.UUID.randomUUID().toString().substring(0, 6).toUpperCase());
        req.setCapacity(8.0);
        mvc.perform(post(fleet(abcCompanyId) + "/vehicles")
                        .header("Authorization", auth(abcFm))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.carrierCompanyId").value(abcCompanyId))
                .andExpect(jsonPath("$.approvalStatus").value("UNDER_REVIEW"));
    }

    @Test
    void ownerCanAddDriverViaHttp() throws Exception {
        User newDriver = mkUser(User.Role.DRIVER);
        var req = new AddCarrierDriverRequest();
        req.setDriverUserId(newDriver.getId());
        mvc.perform(post(fleet(abcCompanyId) + "/drivers")
                        .header("Authorization", auth(abcOwner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.availability").value("AVAILABLE"));
    }

    @Test
    void xyzCannotAddDriverToAbcFleetViaHttp() throws Exception {
        User newDriver = mkUser(User.Role.DRIVER);
        var req = new AddCarrierDriverRequest();
        req.setDriverUserId(newDriver.getId());
        mvc.perform(post(fleet(abcCompanyId) + "/drivers")
                        .header("Authorization", auth(xyzOwner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(req)))
                .andExpect(status().isForbidden());
    }

    @Test
    void assignEndWorkflowViaHttp() throws Exception {
        abcVehicle1 = approveVehicle(abcVehicle1);
        var req = new CreateAssignmentRequest();
        req.setCarrierDriverId(abcCd1.getId());
        req.setVehicleId(abcVehicle1.getId());

        // Assign
        String createResp = mvc.perform(post(fleet(abcCompanyId) + "/assignments")
                        .header("Authorization", auth(abcOwner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andReturn().getResponse().getContentAsString();

        Long assignmentId = json.readTree(createResp).get("id").asLong();

        // End assignment
        mvc.perform(post(fleet(abcCompanyId) + "/assignments/" + assignmentId + "/end")
                        .header("Authorization", auth(abcOwner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ENDED"))
                .andExpect(jsonPath("$.endedAt").isNotEmpty());
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 8. Mass-assignment protection
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void cannotMassAssignCarrierCompanyIdViaDriverRequest() throws Exception {
        // Request body with a carrierCompanyId field should be ignored
        // (field doesn't exist on AddCarrierDriverRequest)
        User newDriver = mkUser(User.Role.DRIVER);
        String maliciousBody = "{\"driverUserId\":" + newDriver.getId()
                + ",\"carrierCompanyId\":" + xyzCompanyId + "}";
        mvc.perform(post(fleet(abcCompanyId) + "/drivers")
                        .header("Authorization", auth(abcOwner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(maliciousBody))
                .andExpect(status().isCreated());

        // Driver must be in ABC, not XYZ
        var cd = driverRepo.findByCarrierCompanyIdAndDriverUserId(abcCompanyId, newDriver.getId());
        assertThat(cd).isPresent();
        assertThat(cd.get().getCarrierCompanyId()).isEqualTo(abcCompanyId);
        assertThat(driverRepo.findByCarrierCompanyIdAndDriverUserId(xyzCompanyId, newDriver.getId()))
                .isEmpty();
    }

    @Test
    void vehicleCarrierCompanyIdIsNotWritable() throws Exception {
        // The RegisterCompanyVehicleRequest has no carrierCompanyId field
        // Sending it in the body must not change the company assignment
        String maliciousBody = "{\"truckType\":\"Truck\",\"plateNumber\":\"MASS-001\","
                + "\"capacity\":5.0,\"carrierCompanyId\":" + xyzCompanyId + "}";
        String resp = mvc.perform(post(fleet(abcCompanyId) + "/vehicles")
                        .header("Authorization", auth(abcOwner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(maliciousBody))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long vehicleId = json.readTree(resp).get("id").asLong();
        var vehicle = vehicleRepo.findById(vehicleId).orElseThrow();
        assertThat(vehicle.getCarrierCompanyId()).isEqualTo(abcCompanyId);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 9. Membership deactivation immediately revokes fleet access
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void inactiveMemberLosesFleetAccess() throws Exception {
        // Deactivate the fleet manager
        var m = memberships.findByCarrierCompanyIdAndUserId(abcCompanyId, abcFm.getId()).orElseThrow();
        m.setStatus(com.ethioloadai.carrier.team.entity.MembershipStatus.INACTIVE);
        memberships.saveAndFlush(m);

        // Next request must be denied
        mvc.perform(get(fleet(abcCompanyId) + "/drivers")
                        .header("Authorization", auth(abcFm)))
                .andExpect(status().isForbidden());
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 10. Phase 3 regression — company/team endpoints still work
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void phase3CompanyReadStillWorks() throws Exception {
        mvc.perform(get("/carrier-companies/" + abcCompanyId)
                        .header("Authorization", auth(abcOwner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(abcCompanyId));
    }

    @Test
    void phase3TeamMembersStillListable() throws Exception {
        mvc.perform(get("/carrier-companies/" + abcCompanyId + "/team/members")
                        .header("Authorization", auth(abcOwner)))
                .andExpect(status().isOk());
    }

    @Test
    void phase3CrossCompanyTeamAccessStillDenied() throws Exception {
        mvc.perform(get("/carrier-companies/" + xyzCompanyId + "/team/members")
                        .header("Authorization", auth(abcOwner)))
                .andExpect(status().isForbidden());
    }
}
