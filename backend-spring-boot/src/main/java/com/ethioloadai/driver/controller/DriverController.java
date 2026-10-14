package com.ethioloadai.driver.controller;

import com.ethioloadai.driver.dto.DriverFilterRequest;
import com.ethioloadai.driver.dto.DriverResponse;
import com.ethioloadai.driver.service.DriverService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for driver directory listing.
 *
 * <h2>Endpoint summary</h2>
 * <pre>
 *   GET /api/drivers         — paginated driver listing with optional status filter
 * </pre>
 *
 * <h2>Authentication + Authorization</h2>
 * Phase 3: restricted to ADMIN, FLEET_OWNER, and SHIPPER roles.
 * Arbitrary authenticated users (drivers themselves) must not enumerate all platform
 * drivers and harvest contact information. SHIPPER is allowed because they legitimately
 * browse the driver network to select carriers for freight requests.
 *
 * <h2>No mutation</h2>
 * This controller is READ-ONLY. No POST/PUT/PATCH/DELETE endpoints exist here.
 * Driver creation and status management are handled by admin workflows.
 */
@RestController
@RequestMapping("/drivers")
@RequiredArgsConstructor
@Tag(name = "Driver Directory", description = "Read-only driver network listing for shippers and admins")
public class DriverController {

    private static final Logger log = LoggerFactory.getLogger(DriverController.class);

    private final DriverService driverService;

    /**
     * Maximum allowed page size.
     * Matches the project-wide convention established in {@code FreightController}.
     */
    private static final int MAX_PAGE_SIZE = 100;

    /**
     * Default page size for driver listing.
     * The frontend currently requests {@code limit=50} but uses Spring pagination
     * (page/size), so defaulting to 20 matches the project's other listing endpoints.
     */
    private static final int DEFAULT_PAGE_SIZE = 20;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','FLEET_OWNER','SHIPPER')")
    @Operation(
        summary = "List drivers (paginated)",
        description = "Returns a paginated list of drivers with role=DRIVER. " +
                      "Optional status filter maps to driver verification and active state. " +
                      "Requires ADMIN, FLEET_OWNER, or SHIPPER role. " +
                      "Defaults to page 0, size 20, sorted by fullName ascending.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Drivers retrieved successfully"),
        @ApiResponse(responseCode = "401", description = "Unauthorized — valid JWT required",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "400", description = "Invalid parameters",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "500", description = "Internal server error",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<Page<DriverResponse>> listDrivers(
            @Parameter(description = "Filter by driver status: active, approved, under_review, submitted, suspended")
            @RequestParam(required = false) String status,
            @Parameter(description = "Page number (0-based, default 0)")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size (default " + DEFAULT_PAGE_SIZE + ", max " + MAX_PAGE_SIZE + ")")
            @RequestParam(defaultValue = "" + DEFAULT_PAGE_SIZE) int size) {

        // Clamp size to prevent full-table fetches — matches FreightController convention
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);

        log.info("GET /api/drivers — page={}, size={}, status={}", page, safeSize, status);

        DriverFilterRequest filter = DriverFilterRequest.builder()
                .status(status)
                .build();

        Pageable pageable = PageRequest.of(page, safeSize, Sort.by(Sort.Direction.ASC, "fullName"));
        Page<DriverResponse> result = driverService.listDrivers(filter, pageable);

        return ResponseEntity.ok(result);
    }

    // Helper DTO for OpenAPI documentation only
    record ErrorResponse(String message, String code) {}
}
