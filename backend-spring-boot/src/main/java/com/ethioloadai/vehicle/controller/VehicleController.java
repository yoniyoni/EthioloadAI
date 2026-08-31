package com.ethioloadai.vehicle.controller;

import com.ethioloadai.security.model.UserDetailsImpl;
import com.ethioloadai.vehicle.dto.*;
import com.ethioloadai.vehicle.service.VehicleService;
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

import java.math.BigDecimal;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/vehicles")
@RequiredArgsConstructor
@Validated
@Tag(name = "Vehicle Management", description = "Vehicle management endpoints")
public class VehicleController {

    private final VehicleService vehicleService;

    @PostMapping
    @Operation(summary = "Create a new vehicle", description = "Creates a new vehicle for the authenticated user")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "Vehicle created successfully",
            content = @Content(schema = @Schema(implementation = VehicleResponse.class))),
        @ApiResponse(responseCode = "422", description = "Validation error",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "401", description = "Unauthorized",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "500", description = "Internal server error",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<VehicleResponse> createVehicle(
            @Parameter(description = "Vehicle creation data", required = true)
            @Valid @RequestBody VehicleCreateRequest request,
            Authentication authentication) {
        log.info("POST /api/vehicles - Create vehicle request received");
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        Long userId = userDetails.getUser().getId();
        VehicleResponse response = vehicleService.createVehicle(request, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    @Operation(summary = "Get all vehicles", description = "Returns a list of all vehicles (admin only)")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Vehicles retrieved successfully",
            content = @Content(schema = @Schema(implementation = VehicleResponse.class))),
        @ApiResponse(responseCode = "401", description = "Unauthorized",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "Forbidden",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "500", description = "Internal server error",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<List<VehicleResponse>> getAllVehicles(Authentication authentication) {
        log.info("GET /api/vehicles - Get all vehicles request received");
        List<VehicleResponse> response = vehicleService.getAllVehicles();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/my-vehicles")
    @Operation(summary = "Get my vehicles", description = "Returns a list of vehicles owned by the authenticated user")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Vehicles retrieved successfully",
            content = @Content(schema = @Schema(implementation = VehicleResponse.class))),
        @ApiResponse(responseCode = "401", description = "Unauthorized",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "500", description = "Internal server error",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<List<VehicleResponse>> getMyVehicles(Authentication authentication) {
        log.info("GET /api/vehicles/my-vehicles - Get my vehicles request received");
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        Long userId = userDetails.getUser().getId();
        List<VehicleResponse> response = vehicleService.getMyVehicles(userId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get vehicle by ID", description = "Returns a specific vehicle by ID")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Vehicle retrieved successfully",
            content = @Content(schema = @Schema(implementation = VehicleResponse.class))),
        @ApiResponse(responseCode = "401", description = "Unauthorized",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "Forbidden - access denied",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "Vehicle not found",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "500", description = "Internal server error",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<VehicleResponse> getVehicleById(
            @Parameter(description = "Vehicle ID", required = true)
            @PathVariable Long id,
            Authentication authentication) {
        log.info("GET /api/vehicles/{} - Get vehicle by ID request received", id);
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        Long userId = userDetails.getUser().getId();
        VehicleResponse response = vehicleService.getVehicleById(id, userId);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Update vehicle", description = "Updates a specific vehicle by ID")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Vehicle updated successfully",
            content = @Content(schema = @Schema(implementation = VehicleResponse.class))),
        @ApiResponse(responseCode = "401", description = "Unauthorized",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "Forbidden - access denied",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "Vehicle not found",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "422", description = "Validation error",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "500", description = "Internal server error",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<VehicleResponse> updateVehicle(
            @Parameter(description = "Vehicle ID", required = true)
            @PathVariable Long id,
            @Parameter(description = "Vehicle update data", required = true)
            @Valid @RequestBody VehicleUpdateRequest request,
            Authentication authentication) {
        log.info("PATCH /api/vehicles/{} - Update vehicle request received", id);
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        Long userId = userDetails.getUser().getId();
        VehicleResponse response = vehicleService.updateVehicle(id, request, userId);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete vehicle", description = "Deletes a specific vehicle by ID")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "204", description = "Vehicle deleted successfully"),
        @ApiResponse(responseCode = "401", description = "Unauthorized",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "Forbidden - access denied",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "Vehicle not found",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "500", description = "Internal server error",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<Void> deleteVehicle(
            @Parameter(description = "Vehicle ID", required = true)
            @PathVariable Long id,
            Authentication authentication) {
        log.info("DELETE /api/vehicles/{} - Delete vehicle request received", id);
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        Long userId = userDetails.getUser().getId();
        vehicleService.deleteVehicle(id, userId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/location")
    @Operation(summary = "Update vehicle location", description = "Updates the GPS location of a specific vehicle")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Vehicle location updated successfully",
            content = @Content(schema = @Schema(implementation = VehicleResponse.class))),
        @ApiResponse(responseCode = "401", description = "Unauthorized",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "Forbidden - access denied",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "Vehicle not found",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "422", description = "Validation error",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "500", description = "Internal server error",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<VehicleResponse> updateVehicleLocation(
            @Parameter(description = "Vehicle ID", required = true)
            @PathVariable Long id,
            @Parameter(description = "Location update data", required = true)
            @Valid @RequestBody VehicleLocationRequest request,
            Authentication authentication) {
        log.info("PATCH /api/vehicles/{}/location - Update vehicle location request received", id);
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        Long userId = userDetails.getUser().getId();
        VehicleResponse response = vehicleService.updateVehicleLocation(id, request, userId);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{id}/current-city")
    @Operation(summary = "Update vehicle current city", description = "Updates the current city of a specific vehicle")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Vehicle current city updated successfully",
            content = @Content(schema = @Schema(implementation = VehicleResponse.class))),
        @ApiResponse(responseCode = "401", description = "Unauthorized",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "Forbidden - access denied",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "Vehicle not found",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "422", description = "Validation error",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "500", description = "Internal server error",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<VehicleResponse> updateCurrentCity(
            @Parameter(description = "Vehicle ID", required = true)
            @PathVariable Long id,
            @Parameter(description = "Current city", required = true)
            @RequestParam String currentCity,
            Authentication authentication) {
        log.info("PATCH /api/vehicles/{}/current-city - Update current city request received", id);
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        Long userId = userDetails.getUser().getId();
        VehicleResponse response = vehicleService.updateCurrentCity(id, currentCity, userId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/driver/location")
    @Operation(summary = "Update driver location", description = "Updates the GPS location of the driver's primary vehicle")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Driver location updated successfully",
            content = @Content(schema = @Schema(implementation = VehicleResponse.class))),
        @ApiResponse(responseCode = "401", description = "Unauthorized",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "Vehicle not found",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "422", description = "Validation error",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "500", description = "Internal server error",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<VehicleResponse> updateDriverLocation(
            @Parameter(description = "Location update data", required = true)
            @Valid @RequestBody VehicleLocationRequest request,
            Authentication authentication) {
        log.info("POST /api/vehicles/driver/location - Update driver location request received");
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        Long userId = userDetails.getUser().getId();
        VehicleResponse response = vehicleService.updateDriverLocation(request, userId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/nearby")
    @Operation(summary = "Get nearby vehicles", description = "Returns available vehicles within a specified radius")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Nearby vehicles retrieved successfully",
            content = @Content(schema = @Schema(implementation = VehicleResponse.class))),
        @ApiResponse(responseCode = "400", description = "Invalid parameters",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "401", description = "Unauthorized",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "500", description = "Internal server error",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<List<VehicleResponse>> getNearbyVehicles(
            @Parameter(description = "Latitude", required = true)
            @RequestParam BigDecimal latitude,
            @Parameter(description = "Longitude", required = true)
            @RequestParam BigDecimal longitude,
            @Parameter(description = "Search radius in kilometers", required = false)
            @RequestParam(defaultValue = "10.0") Double radius,
            Authentication authentication) {
        log.info("GET /api/vehicles/nearby - Get nearby vehicles request received");
        List<VehicleResponse> response = vehicleService.getNearbyVehicles(latitude, longitude, radius);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/available/{city}")
    @Operation(summary = "Get available vehicles in city", description = "Returns available vehicles in a specific city")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Available vehicles retrieved successfully",
            content = @Content(schema = @Schema(implementation = VehicleResponse.class))),
        @ApiResponse(responseCode = "401", description = "Unauthorized",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "500", description = "Internal server error",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<List<VehicleResponse>> getAvailableVehiclesInCity(
            @Parameter(description = "City name", required = true)
            @PathVariable String city,
            Authentication authentication) {
        log.info("GET /api/vehicles/available/{} - Get available vehicles in city request received", city);
        List<VehicleResponse> response = vehicleService.getAvailableVehiclesInCity(city);
        return ResponseEntity.ok(response);
    }

    // Helper DTOs for OpenAPI documentation
    record ErrorResponse(String message, String code) {}
}
