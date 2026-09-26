package com.ethioloadai.carrier.dispatch.controller;

import com.ethioloadai.carrier.dispatch.dto.*;
import com.ethioloadai.carrier.dispatch.service.DispatchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import com.ethioloadai.user.entity.User;

import java.util.List;

/**
 * Phase 6 — Carrier internal dispatch and driver assignment endpoints.
 *
 * <h2>URL structure</h2>
 * <pre>
 *   Carrier-facing (requires active company membership + dispatch role):
 *     GET  /carrier-companies/{companyId}/awarded-freights
 *     POST /carrier-companies/{companyId}/freight/{freightId}/dispatch
 *     GET  /carrier-companies/{companyId}/dispatches
 *     GET  /carrier-companies/{companyId}/dispatches/{dispatchId}
 *     POST /carrier-companies/{companyId}/dispatches/{dispatchId}/cancel
 *     GET  /carrier-companies/{companyId}/freight/{freightId}/dispatches
 *
 *   Driver-facing (authenticated DRIVER only):
 *     GET  /api/driver/assignments
 *     GET  /api/driver/assignments/{dispatchId}
 *     POST /api/driver/assignments/{dispatchId}/accept
 *     POST /api/driver/assignments/{dispatchId}/reject
 * </pre>
 *
 * <h2>Security</h2>
 * <ul>
 *   <li>Company ID is always from the URL path — never from request body.</li>
 *   <li>{@code @companySecurity.isMemberOf} gates carrier endpoints.</li>
 *   <li>Driver endpoints gate on {@code hasRole('DRIVER')}.</li>
 *   <li>The service layer enforces winning-carrier company match, driver/vehicle
 *       company ownership, and IDOR (only assigned driver can accept/reject).</li>
 * </ul>
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "Carrier Dispatch (Phase 6)",
     description = "Carrier internal dispatch — assign drivers and vehicles to awarded shipments")
public class DispatchController {

    private final DispatchService dispatchService;

    // ════════════════════════════════════════════════════════════════════════════
    // Carrier portal endpoints
    // ════════════════════════════════════════════════════════════════════════════

    /**
     * List all shipments that have been commercially awarded to this carrier company.
     * Each entry includes the current dispatch status (if dispatched).
     */
    @GetMapping("/carrier-companies/{companyId}/awarded-freights")
    @PreAuthorize("@companySecurity.isMemberOf(#companyId, authentication)")
    @Operation(summary = "List awarded shipments for the carrier company (dispatch portal)")
    public ResponseEntity<List<AwardedFreightSummary>> getAwardedFreights(
            @PathVariable Long companyId,
            @AuthenticationPrincipal User caller) {
        return ResponseEntity.ok(dispatchService.getAwardedFreights(companyId, caller));
    }

    /**
     * Create a dispatch assignment for an awarded freight.
     * Caller must be CARRIER_OWNER, FLEET_MANAGER, or DISPATCHER.
     * The company ID is taken from the URL (never from the request body).
     */
    @PostMapping("/carrier-companies/{companyId}/freight/{freightId}/dispatch")
    @PreAuthorize("@companySecurity.isMemberOf(#companyId, authentication)")
    @Operation(summary = "Dispatch a driver and vehicle to an awarded freight")
    public ResponseEntity<DispatchAssignmentResponse> createDispatch(
            @PathVariable Long companyId,
            @PathVariable Long freightId,
            @Valid @RequestBody CreateDispatchRequest request,
            @AuthenticationPrincipal User caller) {
        DispatchAssignmentResponse response = dispatchService.createDispatch(freightId, companyId, request, caller);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Paginated list of all dispatch assignments for a carrier company.
     */
    @GetMapping("/carrier-companies/{companyId}/dispatches")
    @PreAuthorize("@companySecurity.isMemberOf(#companyId, authentication)")
    @Operation(summary = "List all dispatch assignments for a company")
    public ResponseEntity<Page<DispatchAssignmentResponse>> listDispatches(
            @PathVariable Long companyId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal User caller) {
        Page<DispatchAssignmentResponse> result = dispatchService.listCompanyDispatches(
                companyId, caller,
                PageRequest.of(page, size, Sort.by("assignedAt").descending()));
        return ResponseEntity.ok(result);
    }

    /**
     * Get a specific dispatch assignment by ID (company-scoped).
     */
    @GetMapping("/carrier-companies/{companyId}/dispatches/{dispatchId}")
    @PreAuthorize("@companySecurity.isMemberOf(#companyId, authentication)")
    @Operation(summary = "Get a specific dispatch assignment")
    public ResponseEntity<DispatchAssignmentResponse> getDispatch(
            @PathVariable Long companyId,
            @PathVariable Long dispatchId,
            @AuthenticationPrincipal User caller) {
        return ResponseEntity.ok(dispatchService.getDispatch(dispatchId, companyId, caller));
    }

    /**
     * Cancel a pending dispatch assignment.
     * Only allowed when the assignment is in ASSIGNED status.
     */
    @PostMapping("/carrier-companies/{companyId}/dispatches/{dispatchId}/cancel")
    @PreAuthorize("@companySecurity.isMemberOf(#companyId, authentication)")
    @Operation(summary = "Cancel a pending dispatch assignment (carrier operator only)")
    public ResponseEntity<DispatchAssignmentResponse> cancelDispatch(
            @PathVariable Long companyId,
            @PathVariable Long dispatchId,
            @AuthenticationPrincipal User caller) {
        return ResponseEntity.ok(dispatchService.cancelDispatch(dispatchId, companyId, caller));
    }

    /**
     * All dispatch assignments for a specific freight within a company.
     * Includes full history (accepted, rejected, cancelled, superseded).
     */
    @GetMapping("/carrier-companies/{companyId}/freight/{freightId}/dispatches")
    @PreAuthorize("@companySecurity.isMemberOf(#companyId, authentication)")
    @Operation(summary = "List all dispatch history for a specific awarded freight")
    public ResponseEntity<List<DispatchAssignmentResponse>> listFreightDispatches(
            @PathVariable Long companyId,
            @PathVariable Long freightId,
            @AuthenticationPrincipal User caller) {
        return ResponseEntity.ok(dispatchService.listFreightDispatches(freightId, companyId, caller));
    }

    // ════════════════════════════════════════════════════════════════════════════
    // Driver-facing endpoints
    // ════════════════════════════════════════════════════════════════════════════

    /**
     * Driver's assignment inbox — all assignments directed to this driver.
     * Only the authenticated DRIVER's own assignments are returned (IDOR-safe).
     */
    @GetMapping("/api/driver/assignments")
    @PreAuthorize("hasRole('DRIVER')")
    @Operation(summary = "Driver: list own dispatch assignments (inbox)")
    public ResponseEntity<List<DispatchAssignmentResponse>> getMyAssignments(
            @AuthenticationPrincipal User driver) {
        return ResponseEntity.ok(dispatchService.getMyAssignments(driver));
    }

    /**
     * Driver views a specific assignment.
     * Returns 404 if the assignment belongs to a different driver.
     */
    @GetMapping("/api/driver/assignments/{dispatchId}")
    @PreAuthorize("hasRole('DRIVER')")
    @Operation(summary = "Driver: get a specific assignment detail")
    public ResponseEntity<DispatchAssignmentResponse> getMyAssignment(
            @PathVariable Long dispatchId,
            @AuthenticationPrincipal User driver) {
        return ResponseEntity.ok(dispatchService.getMyAssignment(dispatchId, driver));
    }

    /**
     * Driver accepts a dispatch assignment.
     * Atomically creates a Booking and transitions to DRIVER_ACCEPTED.
     */
    @PostMapping("/api/driver/assignments/{dispatchId}/accept")
    @PreAuthorize("hasRole('DRIVER')")
    @Operation(summary = "Driver: accept a dispatch assignment")
    public ResponseEntity<DispatchAssignmentResponse> acceptAssignment(
            @PathVariable Long dispatchId,
            @AuthenticationPrincipal User driver) {
        return ResponseEntity.ok(dispatchService.acceptAssignment(dispatchId, driver));
    }

    /**
     * Driver rejects a dispatch assignment.
     * Releases resources; carrier can redispatch to another driver.
     * Commercial carrier award is NOT affected.
     */
    @PostMapping("/api/driver/assignments/{dispatchId}/reject")
    @PreAuthorize("hasRole('DRIVER')")
    @Operation(summary = "Driver: reject a dispatch assignment")
    public ResponseEntity<DispatchAssignmentResponse> rejectAssignment(
            @PathVariable Long dispatchId,
            @RequestBody(required = false) DriverRespondRequest request,
            @AuthenticationPrincipal User driver) {
        return ResponseEntity.ok(dispatchService.rejectAssignment(dispatchId, request, driver));
    }
}
