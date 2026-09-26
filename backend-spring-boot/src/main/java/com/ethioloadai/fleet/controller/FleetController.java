package com.ethioloadai.fleet.controller;

import com.ethioloadai.fleet.dto.*;
import com.ethioloadai.fleet.service.FleetService;
import com.ethioloadai.user.entity.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for Fleet Management.
 *
 * <h2>Endpoint summary</h2>
 * <pre>
 *   GET    /api/fleet/dashboard               — fleet owner dashboard summary
 *   GET    /api/fleet/drivers                 — list fleet drivers
 *   POST   /api/fleet/drivers                 — add existing driver to fleet
 *   DELETE /api/fleet/drivers/{driverId}      — remove driver from fleet
 *   GET    /api/fleet/vehicles                — list fleet vehicles
 *   POST   /api/fleet/vehicles                — register new vehicle under fleet
 *   PUT    /api/fleet/vehicles/{vehicleId}/assign — assign vehicle to a fleet driver
 * </pre>
 *
 * <p>All endpoints require FLEET_OWNER or ADMIN role.
 * Service-layer ownership checks protect against IDOR on every mutating operation.
 *
 * <p>Commission rates, driver KYC activation, and booking state machines
 * remain fully owned by their respective services (M7 / M8A rules preserved).
 */
@RestController
@RequestMapping("/fleet")
@RequiredArgsConstructor
@Tag(name = "Fleet Management", description = "Fleet owner operations: drivers, vehicles, dashboard")
public class FleetController {

    private static final Logger log = LoggerFactory.getLogger(FleetController.class);

    private final FleetService fleetService;

    // ── Dashboard ─────────────────────────────────────────────────────────────

    @GetMapping("/dashboard")
    @PreAuthorize("hasAnyRole('FLEET_OWNER','ADMIN')")
    @Operation(
        summary = "Fleet dashboard",
        description = "Returns a summary of the fleet owner's drivers, vehicles, and active bookings. " +
                      "Admin receives results for the authenticated user's own fleet view.")
    public ResponseEntity<FleetDashboardResponse> getDashboard(
            @AuthenticationPrincipal User caller) {

        log.info("GET /api/fleet/dashboard caller={}", caller.getId());
        return ResponseEntity.ok(fleetService.getDashboard(caller.getId()));
    }

    // ── Drivers ───────────────────────────────────────────────────────────────

    @GetMapping("/drivers")
    @PreAuthorize("hasAnyRole('FLEET_OWNER','ADMIN')")
    @Operation(
        summary = "List fleet drivers",
        description = "Returns all drivers whose fleet_owner_id matches the authenticated fleet owner.")
    public ResponseEntity<List<FleetDriverResponse>> getDrivers(
            @AuthenticationPrincipal User caller) {

        log.info("GET /api/fleet/drivers caller={}", caller.getId());
        return ResponseEntity.ok(fleetService.getFleetDrivers(caller.getId()));
    }

    @PostMapping("/drivers")
    @PreAuthorize("hasAnyRole('FLEET_OWNER','ADMIN')")
    @Operation(
        summary = "Add driver to fleet",
        description = "Associates an existing driver (by ID) with this fleet. " +
                      "The driver must have role DRIVER and must not already belong to another fleet. " +
                      "KYC activation is not granted by this operation.")
    public ResponseEntity<FleetDriverResponse> addDriver(
            @AuthenticationPrincipal User caller,
            @Valid @RequestBody AddDriverRequest request) {

        log.info("POST /api/fleet/drivers caller={} driverId={}", caller.getId(), request.getDriverId());
        FleetDriverResponse response = fleetService.addDriver(caller.getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @DeleteMapping("/drivers/{driverId}")
    @PreAuthorize("hasAnyRole('FLEET_OWNER','ADMIN')")
    @Operation(
        summary = "Remove driver from fleet",
        description = "Unassigns the driver from this fleet (sets fleet_owner_id = null). " +
                      "Server validates that the driver belongs to the caller's fleet before removal.")
    public ResponseEntity<Void> removeDriver(
            @AuthenticationPrincipal User caller,
            @Parameter(description = "ID of the driver to remove") @PathVariable Long driverId) {

        log.info("DELETE /api/fleet/drivers/{} caller={}", driverId, caller.getId());
        fleetService.removeDriver(caller.getId(), driverId);
        return ResponseEntity.noContent().build();
    }

    // ── Vehicles ──────────────────────────────────────────────────────────────

    @GetMapping("/vehicles")
    @PreAuthorize("hasAnyRole('FLEET_OWNER','ADMIN')")
    @Operation(
        summary = "List fleet vehicles",
        description = "Returns all vehicles whose fleet_owner_id matches the authenticated fleet owner.")
    public ResponseEntity<List<FleetVehicleResponse>> getVehicles(
            @AuthenticationPrincipal User caller) {

        log.info("GET /api/fleet/vehicles caller={}", caller.getId());
        return ResponseEntity.ok(fleetService.getFleetVehicles(caller.getId()));
    }

    @PostMapping("/vehicles")
    @PreAuthorize("hasAnyRole('FLEET_OWNER','ADMIN')")
    @Operation(
        summary = "Register new fleet vehicle",
        description = "Creates a new vehicle with fleet_owner_id = caller. " +
                      "Optionally assigns an existing fleet driver immediately. " +
                      "Plate number uniqueness is enforced server-side.")
    public ResponseEntity<FleetVehicleResponse> addVehicle(
            @AuthenticationPrincipal User caller,
            @Valid @RequestBody AddVehicleRequest request) {

        log.info("POST /api/fleet/vehicles caller={} plate={}", caller.getId(), request.getPlateNumber());
        FleetVehicleResponse response = fleetService.addVehicle(caller.getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/vehicles/{vehicleId}/assign")
    @PreAuthorize("hasAnyRole('FLEET_OWNER','ADMIN')")
    @Operation(
        summary = "Assign vehicle to fleet driver",
        description = "Updates vehicle.user_id to the specified driver. " +
                      "Both vehicle and driver must belong to this fleet. " +
                      "Driver must be KYC-active (isActive=true) before assignment.")
    public ResponseEntity<FleetVehicleResponse> assignVehicle(
            @AuthenticationPrincipal User caller,
            @Parameter(description = "ID of the vehicle to assign") @PathVariable Long vehicleId,
            @Valid @RequestBody AssignVehicleRequest request) {

        log.info("PUT /api/fleet/vehicles/{}/assign caller={} driverId={}",
                vehicleId, caller.getId(), request.getDriverId());
        FleetVehicleResponse response = fleetService.assignVehicle(caller.getId(), vehicleId, request);
        return ResponseEntity.ok(response);
    }
}
