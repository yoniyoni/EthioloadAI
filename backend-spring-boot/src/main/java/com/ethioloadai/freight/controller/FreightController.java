package com.ethioloadai.freight.controller;

import com.ethioloadai.freight.dto.*;
import com.ethioloadai.freight.service.FreightService;
import com.ethioloadai.security.model.UserDetailsImpl;
import com.ethioloadai.user.entity.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping({"/freight", "/cargo-requests"})
@RequiredArgsConstructor
@Validated
@Tag(name = "Freight Management", description = "Freight management endpoints")
public class FreightController {
    private static final Logger log = LoggerFactory.getLogger(FreightController.class);

    private final FreightService freightService;

    /** Maximum page size allowed — prevents a caller from requesting the entire table. */
    private static final int MAX_PAGE_SIZE = 100;

    @GetMapping
    @Operation(
        summary = "List freight with filters (paginated)",
        description = "Returns a paginated list of freight postings with optional filters. " +
                      "Supports both intercity and intracity freight filtering. " +
                      "Defaults to page 0, size 20, sorted by createdAt descending.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Freight retrieved successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid parameters",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "500", description = "Internal server error",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<Page<FreightResponse>> listFreight(
            @Parameter(description = "Filter by status (posted, matched, in_transit, delivered, completed, cancelled)")
            @RequestParam(required = false) String status,
            @Parameter(description = "Filter by material type")
            @RequestParam(required = false) String materialType,
            @Parameter(description = "Filter by pickup location (partial match)")
            @RequestParam(required = false) String pickupLocation,
            @Parameter(description = "Filter by destination (partial match)")
            @RequestParam(required = false) String destination,
            @Parameter(description = "Filter by service type (intercity, intracity)")
            @RequestParam(required = false) String serviceType,
            @Parameter(description = "Filter by price type (negotiable, fixed)")
            @RequestParam(required = false) String priceType,
            @Parameter(description = "Filter by city (for intracity freight)")
            @RequestParam(required = false) String city,
            @Parameter(description = "Filter by pickup area (for intracity freight)")
            @RequestParam(required = false) String pickupArea,
            @Parameter(description = "Filter by dropoff area (for intracity freight)")
            @RequestParam(required = false) String dropoffArea,
            @Parameter(description = "Filter by vehicle type needed")
            @RequestParam(required = false) String vehicleTypeNeeded,
            @Parameter(description = "Filter freight with bids still open")
            @RequestParam(required = false) Boolean bidsOpen,
            @Parameter(description = "Page number (0-based, default 0)")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size (default 20, max " + MAX_PAGE_SIZE + ")")
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication) {

        // Clamp size to a safe maximum to prevent full-table fetches
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);

        log.info("GET /api/freight — page={}, size={}, status={}, serviceType={}, city={}", 
                page, safeSize, status, serviceType, city);

        FreightFilterRequest filter = FreightFilterRequest.builder()
                .status(status)
                .materialType(materialType)
                .pickupLocation(pickupLocation)
                .destination(destination)
                .serviceType(serviceType)
                .priceType(priceType)
                .city(city)
                .pickupArea(pickupArea)
                .dropoffArea(dropoffArea)
                .vehicleTypeNeeded(vehicleTypeNeeded)
                .bidsOpen(bidsOpen)
                .build();

        Pageable pageable = PageRequest.of(page, safeSize, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        User caller = authentication != null && authentication.isAuthenticated()
            ? (User) authentication.getPrincipal() : null;
        Page<FreightResponse> result = freightService.searchFreightsPaged(filter, pageable, caller);

        return ResponseEntity.ok(result);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get freight by ID", description = "Returns a specific freight posting by ID. Publicly accessible.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Freight retrieved successfully"),
        @ApiResponse(responseCode = "404", description = "Freight not found",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "500", description = "Internal server error",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<FreightResponse> getFreight(@PathVariable Long id) {
        log.info("GET /api/freight/{}", id);
        return ResponseEntity.ok(freightService.getFreightById(id));
    }

    @PostMapping({"", "/create"})
    @Operation(summary = "Create freight", description = "Creates a new freight posting for the authenticated shipper or admin.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "Freight created successfully"),
        @ApiResponse(responseCode = "401", description = "Unauthorized",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "Forbidden — shipper or admin role required",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "422", description = "Validation error",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "500", description = "Internal server error",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<FreightResponse> createFreight(
            @Valid @RequestBody CreateFreightRequest request,
            Authentication authentication) {
        log.info("POST /api/freight — create freight");
        User user = (User) authentication.getPrincipal();
        FreightResponse response = freightService.createFreight(user.getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Update freight", description = "Updates a freight posting. Caller must own the posting or be an admin.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Freight updated successfully"),
        @ApiResponse(responseCode = "401", description = "Unauthorized",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "Forbidden — not the owner or not an admin",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "Freight not found",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "422", description = "Validation error or invalid status transition",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "500", description = "Internal server error",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<FreightResponse> updateFreight(
            @PathVariable Long id,
            @Valid @RequestBody UpdateFreightRequest request,
            Authentication authentication) {
        log.info("PATCH /api/freight/{}", id);
        User user = (User) authentication.getPrincipal();
        return ResponseEntity.ok(freightService.updateFreight(id, user.getId(), request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Cancel freight", description = "Cancels a freight posting in POSTED status. Caller must own the posting or be an admin.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "204", description = "Freight cancelled successfully"),
        @ApiResponse(responseCode = "401", description = "Unauthorized",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "Forbidden — not the owner or not an admin",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "Freight not found",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "422", description = "Freight cannot be cancelled in current status",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "500", description = "Internal server error",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<Void> cancelFreight(@PathVariable Long id, Authentication authentication) {
        log.info("DELETE /api/freight/{}", id);
        User user = (User) authentication.getPrincipal();
        freightService.cancelFreight(id, user.getId());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/my-freights")
    @Operation(summary = "Get my freight", description = "Returns all freight postings owned by the authenticated shipper.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Freight retrieved successfully"),
        @ApiResponse(responseCode = "401", description = "Unauthorized",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "Forbidden — shipper or admin role required",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "500", description = "Internal server error",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<List<FreightResponse>> getMyFreights(Authentication authentication) {
        log.info("GET /api/freight/my-freights");
        User user = (User) authentication.getPrincipal();
        return ResponseEntity.ok(freightService.getUserFreights(user.getId()));
    }

    // Helper DTO for OpenAPI documentation
    record ErrorResponse(String message, String code) {}
}
