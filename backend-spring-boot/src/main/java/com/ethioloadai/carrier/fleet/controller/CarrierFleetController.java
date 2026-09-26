package com.ethioloadai.carrier.fleet.controller;

import com.ethioloadai.carrier.fleet.dto.*;
import com.ethioloadai.carrier.fleet.entity.VehicleAvailability;
import com.ethioloadai.carrier.fleet.service.CarrierFleetService;
import com.ethioloadai.carrier.team.service.CompanyContextService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import com.ethioloadai.user.entity.User;

import java.util.List;

/**
 * Phase 4 — Carrier company fleet management controller.
 *
 * <p>All endpoints are under {@code /carrier-companies/{companyId}/fleet/}.
 * Authorization is enforced by {@code @PreAuthorize} SpEL using the Phase 3
 * {@link com.ethioloadai.carrier.team.service.CompanySecurityService} bean.
 *
 * <p>Role matrix:
 * <ul>
 *   <li>Read fleet data — any active company member (CARRIER_OWNER, FLEET_MANAGER, DISPATCHER)</li>
 *   <li>Write fleet data — CARRIER_OWNER or FLEET_MANAGER ({@code canManageFleet})</li>
 *   <li>Assignments — CARRIER_OWNER or FLEET_MANAGER</li>
 * </ul>
 *
 * <p>The {@code carrierCompanyId} is always taken from the URL path.
 * It is never accepted from the request body to prevent mass-assignment attacks.
 */
@RestController
@RequestMapping("/carrier-companies/{companyId}/fleet")
@RequiredArgsConstructor
@Tag(name = "Carrier Fleet Management (Phase 4)",
     description = "Company-scoped driver, vehicle, and assignment management")
public class CarrierFleetController {

    private final CarrierFleetService fleetService;
    private final CompanyContextService companyContext;

    // ══════════════════════════════════════════════════════════════════════════
    // Carrier Drivers
    // ══════════════════════════════════════════════════════════════════════════

    @PostMapping("/drivers")
    @PreAuthorize("@companySecurity.canManageFleet(#companyId, authentication)")
    @Operation(summary = "Add a DRIVER user to the company fleet")
    public ResponseEntity<CarrierDriverResponse> addDriver(
            @PathVariable Long companyId,
            @Valid @RequestBody AddCarrierDriverRequest req,
            @AuthenticationPrincipal User caller) {
        companyContext.resolveForCompany(companyId, caller); // validate membership
        CarrierDriverResponse response = fleetService.addDriver(companyId, req, caller);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/drivers")
    @PreAuthorize("@companySecurity.isMemberOf(#companyId, authentication)")
    @Operation(summary = "List company drivers (filtered by status, default ACTIVE)")
    public ResponseEntity<Page<CarrierDriverResponse>> listDrivers(
            @PathVariable Long companyId,
            @RequestParam(defaultValue = "ACTIVE") String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, Math.min(size, 100),
                Sort.by("joinedAt").descending());
        return ResponseEntity.ok(fleetService.listDrivers(companyId, status, pageable));
    }

    @GetMapping("/drivers/{carrierDriverId}")
    @PreAuthorize("@companySecurity.isMemberOf(#companyId, authentication)")
    @Operation(summary = "Get a single company driver")
    public ResponseEntity<CarrierDriverResponse> getDriver(
            @PathVariable Long companyId,
            @PathVariable Long carrierDriverId) {
        return ResponseEntity.ok(fleetService.getDriver(companyId, carrierDriverId));
    }

    @PatchMapping("/drivers/{carrierDriverId}")
    @PreAuthorize("@companySecurity.canManageFleet(#companyId, authentication)")
    @Operation(summary = "Update mutable driver fields (notes, manual availability)")
    public ResponseEntity<CarrierDriverResponse> updateDriver(
            @PathVariable Long companyId,
            @PathVariable Long carrierDriverId,
            @RequestBody UpdateCarrierDriverRequest req,
            @AuthenticationPrincipal User caller) {
        return ResponseEntity.ok(fleetService.updateDriver(companyId, carrierDriverId, req, caller));
    }

    @PostMapping("/drivers/{carrierDriverId}/activate")
    @PreAuthorize("@companySecurity.canManageFleet(#companyId, authentication)")
    @Operation(summary = "Re-activate an INACTIVE driver")
    public ResponseEntity<CarrierDriverResponse> activateDriver(
            @PathVariable Long companyId,
            @PathVariable Long carrierDriverId,
            @AuthenticationPrincipal User caller) {
        return ResponseEntity.ok(fleetService.activateDriver(companyId, carrierDriverId, caller));
    }

    @PostMapping("/drivers/{carrierDriverId}/deactivate")
    @PreAuthorize("@companySecurity.canManageFleet(#companyId, authentication)")
    @Operation(summary = "Suspend a driver (INACTIVE). Ends any active assignment.")
    public ResponseEntity<CarrierDriverResponse> deactivateDriver(
            @PathVariable Long companyId,
            @PathVariable Long carrierDriverId,
            @AuthenticationPrincipal User caller) {
        return ResponseEntity.ok(fleetService.deactivateDriver(companyId, carrierDriverId, caller));
    }

    @DeleteMapping("/drivers/{carrierDriverId}")
    @PreAuthorize("@companySecurity.canManageFleet(#companyId, authentication)")
    @Operation(summary = "Remove a driver from the company (REMOVED). Preserves history. Does NOT delete the user account.")
    public ResponseEntity<CarrierDriverResponse> removeDriver(
            @PathVariable Long companyId,
            @PathVariable Long carrierDriverId,
            @AuthenticationPrincipal User caller) {
        return ResponseEntity.ok(fleetService.removeDriver(companyId, carrierDriverId, caller));
    }

    @GetMapping("/drivers/{carrierDriverId}/assignments")
    @PreAuthorize("@companySecurity.isMemberOf(#companyId, authentication)")
    @Operation(summary = "Assignment history for a specific driver")
    public ResponseEntity<List<AssignmentResponse>> driverAssignmentHistory(
            @PathVariable Long companyId,
            @PathVariable Long carrierDriverId) {
        return ResponseEntity.ok(fleetService.driverAssignmentHistory(companyId, carrierDriverId));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Company Vehicles
    // ══════════════════════════════════════════════════════════════════════════

    @PostMapping("/vehicles")
    @PreAuthorize("@companySecurity.canManageFleet(#companyId, authentication)")
    @Operation(summary = "Register a new vehicle under the company")
    public ResponseEntity<CompanyVehicleResponse> registerVehicle(
            @PathVariable Long companyId,
            @Valid @RequestBody RegisterCompanyVehicleRequest req,
            @AuthenticationPrincipal User caller) {
        companyContext.resolveForCompany(companyId, caller);
        CompanyVehicleResponse response = fleetService.registerVehicle(companyId, req, caller);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/vehicles")
    @PreAuthorize("@companySecurity.isMemberOf(#companyId, authentication)")
    @Operation(summary = "List all company vehicles")
    public ResponseEntity<Page<CompanyVehicleResponse>> listVehicles(
            @PathVariable Long companyId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, Math.min(size, 100),
                Sort.by("createdAt").descending());
        return ResponseEntity.ok(fleetService.listVehicles(companyId, pageable));
    }

    @GetMapping("/vehicles/{vehicleId}")
    @PreAuthorize("@companySecurity.isMemberOf(#companyId, authentication)")
    @Operation(summary = "Get a single company vehicle")
    public ResponseEntity<CompanyVehicleResponse> getVehicle(
            @PathVariable Long companyId,
            @PathVariable Long vehicleId) {
        return ResponseEntity.ok(fleetService.getVehicle(companyId, vehicleId));
    }

    @PatchMapping("/vehicles/{vehicleId}")
    @PreAuthorize("@companySecurity.canManageFleet(#companyId, authentication)")
    @Operation(summary = "Update mutable vehicle fields")
    public ResponseEntity<CompanyVehicleResponse> updateVehicle(
            @PathVariable Long companyId,
            @PathVariable Long vehicleId,
            @RequestBody UpdateCompanyVehicleRequest req,
            @AuthenticationPrincipal User caller) {
        return ResponseEntity.ok(fleetService.updateVehicle(companyId, vehicleId, req, caller));
    }

    @PatchMapping("/vehicles/{vehicleId}/availability")
    @PreAuthorize("@companySecurity.canManageFleet(#companyId, authentication)")
    @Operation(summary = "Manually set vehicle availability (e.g. MAINTENANCE, UNAVAILABLE)")
    public ResponseEntity<CompanyVehicleResponse> setVehicleAvailability(
            @PathVariable Long companyId,
            @PathVariable Long vehicleId,
            @RequestParam VehicleAvailability availability,
            @AuthenticationPrincipal User caller) {
        return ResponseEntity.ok(
                fleetService.setVehicleAvailability(companyId, vehicleId, availability, caller));
    }

    @GetMapping("/vehicles/{vehicleId}/assignments")
    @PreAuthorize("@companySecurity.isMemberOf(#companyId, authentication)")
    @Operation(summary = "Assignment history for a specific vehicle")
    public ResponseEntity<List<AssignmentResponse>> vehicleAssignmentHistory(
            @PathVariable Long companyId,
            @PathVariable Long vehicleId) {
        return ResponseEntity.ok(fleetService.vehicleAssignmentHistory(companyId, vehicleId));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Driver ↔ Vehicle Assignments
    // ══════════════════════════════════════════════════════════════════════════

    @PostMapping("/assignments")
    @PreAuthorize("@companySecurity.canManageFleet(#companyId, authentication)")
    @Operation(summary = "Assign a driver to a vehicle. Ends any prior active assignment for the driver/vehicle.")
    public ResponseEntity<AssignmentResponse> assign(
            @PathVariable Long companyId,
            @Valid @RequestBody CreateAssignmentRequest req,
            @AuthenticationPrincipal User caller) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(fleetService.assign(companyId, req, caller));
    }

    @GetMapping("/assignments")
    @PreAuthorize("@companySecurity.isMemberOf(#companyId, authentication)")
    @Operation(summary = "Paginated assignment history for the company")
    public ResponseEntity<Page<AssignmentResponse>> listAssignments(
            @PathVariable Long companyId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, Math.min(size, 100),
                Sort.by("assignedAt").descending());
        return ResponseEntity.ok(fleetService.listAssignments(companyId, pageable));
    }

    @GetMapping("/assignments/{assignmentId}")
    @PreAuthorize("@companySecurity.isMemberOf(#companyId, authentication)")
    @Operation(summary = "Get a specific assignment")
    public ResponseEntity<AssignmentResponse> getAssignment(
            @PathVariable Long companyId,
            @PathVariable Long assignmentId) {
        return ResponseEntity.ok(fleetService.getAssignment(companyId, assignmentId));
    }

    @PostMapping("/assignments/{assignmentId}/end")
    @PreAuthorize("@companySecurity.canManageFleet(#companyId, authentication)")
    @Operation(summary = "End an active assignment and return driver/vehicle to AVAILABLE")
    public ResponseEntity<AssignmentResponse> endAssignment(
            @PathVariable Long companyId,
            @PathVariable Long assignmentId,
            @RequestBody(required = false) EndAssignmentRequest req,
            @AuthenticationPrincipal User caller) {
        return ResponseEntity.ok(fleetService.endAssignment(companyId, assignmentId, req, caller));
    }
}
