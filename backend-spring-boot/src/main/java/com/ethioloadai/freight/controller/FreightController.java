package com.ethioloadai.freight.controller;

import com.ethioloadai.freight.dto.*;
import com.ethioloadai.freight.service.FreightService;
import com.ethioloadai.security.model.UserDetailsImpl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/freight")
@RequiredArgsConstructor
@Validated
@Tag(name = "Freight Management", description = "Freight management endpoints")
public class FreightController {

    private final FreightService freightService;

    @GetMapping
    @Operation(summary = "List freight with filters", description = "Returns a list of freight with optional filters")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Freight retrieved successfully",
            content = @Content(schema = @Schema(implementation = FreightListResponse.class))),
        @ApiResponse(responseCode = "400", description = "Invalid parameters",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "500", description = "Internal server error",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<Map<String, Object>> listFreight(
            @Parameter(description = "Filter by status")
            @RequestParam(required = false) String status,
            @Parameter(description = "Filter by material type")
            @RequestParam(required = false) String materialType,
            @Parameter(description = "Limit results")
            @RequestParam(defaultValue = "20") Integer limit) {
        log.info("GET /api/freight - List freight request received with filters: status={}, materialType={}, limit={}", 
                status, materialType, limit);
        
        FreightFilterRequest filter = FreightFilterRequest.builder()
                .status(status)
                .materialType(materialType)
                .build();
        
        List<FreightResponse> freights = freightService.searchFreights(filter);
        
        // Apply limit
        if (limit > 0 && freights.size() > limit) {
            freights = freights.subList(0, limit);
        }
        
        Map<String, Object> response = new HashMap<>();
        response.put("freight", freights);
        response.put("total", freights.size());
        
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get freight by ID", description = "Returns a specific freight by ID")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Freight retrieved successfully",
            content = @Content(schema = @Schema(implementation = FreightResponse.class))),
        @ApiResponse(responseCode = "404", description = "Freight not found",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "500", description = "Internal server error",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<FreightResponse> getFreight(
            @Parameter(description = "Freight ID", required = true)
            @PathVariable Long id) {
        log.info("GET /api/freight/{} - Get freight by ID request received", id);
        FreightResponse response = freightService.getFreightById(id);
        return ResponseEntity.ok(response);
    }

    @PostMapping
    @Operation(summary = "Create freight", description = "Creates a new freight for the authenticated shipper")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "Freight created successfully",
            content = @Content(schema = @Schema(implementation = FreightResponse.class))),
        @ApiResponse(responseCode = "401", description = "Unauthorized",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "Forbidden - user is not a shipper",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "422", description = "Validation error",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "500", description = "Internal server error",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<FreightResponse> createFreight(
            @Parameter(description = "Freight creation data", required = true)
            @Valid @RequestBody CreateFreightRequest request,
            Authentication authentication) {
        log.info("POST /api/freight - Create freight request received");
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        Long userId = userDetails.getUser().getId();
        FreightResponse response = freightService.createFreight(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Update freight", description = "Updates a specific freight by ID")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Freight updated successfully",
            content = @Content(schema = @Schema(implementation = FreightResponse.class))),
        @ApiResponse(responseCode = "401", description = "Unauthorized",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "Forbidden - access denied",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "Freight not found",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "422", description = "Validation error",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "500", description = "Internal server error",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<FreightResponse> updateFreight(
            @Parameter(description = "Freight ID", required = true)
            @PathVariable Long id,
            @Parameter(description = "Freight update data", required = true)
            @Valid @RequestBody UpdateFreightRequest request,
            Authentication authentication) {
        log.info("PATCH /api/freight/{} - Update freight request received", id);
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        Long userId = userDetails.getUser().getId();
        FreightResponse response = freightService.updateFreight(id, userId, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Cancel/delete freight", description = "Cancels a specific freight by ID (only if in POSTED status)")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "204", description = "Freight cancelled successfully"),
        @ApiResponse(responseCode = "401", description = "Unauthorized",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "Forbidden - access denied",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "Freight not found",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "422", description = "Validation error - freight cannot be cancelled in current status",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "500", description = "Internal server error",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<Void> cancelFreight(
            @Parameter(description = "Freight ID", required = true)
            @PathVariable Long id,
            Authentication authentication) {
        log.info("DELETE /api/freight/{} - Cancel freight request received", id);
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        Long userId = userDetails.getUser().getId();
        freightService.cancelFreight(id, userId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/my-freights")
    @Operation(summary = "Get current user's freight", description = "Returns a list of freight owned by the authenticated shipper")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Freight retrieved successfully",
            content = @Content(schema = @Schema(implementation = FreightResponse.class))),
        @ApiResponse(responseCode = "401", description = "Unauthorized",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "Forbidden - user is not a shipper",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "500", description = "Internal server error",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<List<FreightResponse>> getMyFreights(Authentication authentication) {
        log.info("GET /api/freight/my-freights - Get my freight request received");
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        Long userId = userDetails.getUser().getId();
        List<FreightResponse> response = freightService.getUserFreights(userId);
        return ResponseEntity.ok(response);
    }

    // Helper DTOs for OpenAPI documentation
    record ErrorResponse(String message, String code) {}
    
    record FreightListResponse(List<FreightResponse> freight, Integer total) {}
}
