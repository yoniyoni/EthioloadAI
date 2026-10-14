package com.ethioloadai.fleet.controller;

import com.ethioloadai.fleet.dto.AdminFleetOwnerResponse;
import com.ethioloadai.fleet.dto.AdminFleetOwnersListResponse;
import com.ethioloadai.fleet.service.FleetService;
import com.ethioloadai.user.entity.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST controller for administrative fleet owner inspection.
 *
 * <h2>Endpoint summary</h2>
 * <pre>
 *   GET /api/admin/fleet-owners — List all fleet owners with nested driver & vehicle summaries (Admin only)
 *   GET /api/fleet-owners       — Alias for legacy contract compatibility (Admin only)
 * </pre>
 *
 * <p>Batch queries are used under the hood to prevent N+1 queries when loading drivers and vehicles.
 */
@RestController
@RequestMapping({"/admin/fleet-owners", "/fleet-owners"})
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Fleet Owners Admin", description = "Administrative operations for inspecting fleet owners")
public class AdminFleetController {

    private static final Logger log = LoggerFactory.getLogger(AdminFleetController.class);

    private final FleetService fleetService;

    @GetMapping
    @Operation(
        summary = "List all fleet owners (Admin only)",
        description = "Returns all fleet owners along with their associated drivers and registered vehicles."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Fleet owners retrieved successfully"),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "403", description = "Forbidden — Admin role required")
    })
    public ResponseEntity<AdminFleetOwnersListResponse> getAllFleetOwners(
            @AuthenticationPrincipal User adminUser) {
        log.info("Admin {} fetching all fleet owners", adminUser != null ? adminUser.getId() : "unknown");
        List<AdminFleetOwnerResponse> fleetOwners = fleetService.getAllFleetOwners(adminUser);
        AdminFleetOwnersListResponse response = AdminFleetOwnersListResponse.builder()
                .fleetOwners(fleetOwners)
                .total(fleetOwners.size())
                .build();
        return ResponseEntity.ok(response);
    }
}
