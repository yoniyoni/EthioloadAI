package com.ethioloadai.trip.controller;

import com.ethioloadai.security.model.UserDetailsImpl;
import com.ethioloadai.trip.dto.*;
import com.ethioloadai.trip.entity.Trip;
import com.ethioloadai.trip.entity.TripStop;
import com.ethioloadai.trip.mapper.TripMapper;
import com.ethioloadai.trip.service.TripSecurityService;
import com.ethioloadai.trip.service.TripService;
import com.ethioloadai.trip.service.TripStatusTransitionService;
import com.ethioloadai.trip.service.RouteDataService;
import com.ethioloadai.user.entity.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/trips")
@RequiredArgsConstructor
@Validated
@Tag(name = "Trip Management", description = "Trip management endpoints for drivers, shippers, and admins")
public class TripController {
    private static final Logger log = LoggerFactory.getLogger(TripController.class);

    private final TripService tripService;
    private final TripSecurityService tripSecurityService;
    private final TripStatusTransitionService tripStatusTransitionService;
    private final TripMapper tripMapper;

    private static final int MAX_PAGE_SIZE = 100;

    @PostMapping
    @Operation(summary = "Create a new trip from booking", 
               description = "Creates a new trip from an eligible booking. Only the assigned driver can create the trip.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "Trip created successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid request or booking not eligible"),
        @ApiResponse(responseCode = "403", description = "Not authorized to create trip for this booking"),
        @ApiResponse(responseCode = "404", description = "Booking not found"),
        @ApiResponse(responseCode = "409", description = "Trip already exists for this booking")
    })
    public ResponseEntity<TripResponse> createTrip(
            @Valid @RequestBody CreateTripRequest request,
            Authentication authentication) {
        
        log.debug("Creating trip from booking {} for user {}", 
                 request.getBookingId(), getUserId(authentication));
        
        User user = getUserFromAuthentication(authentication);
        Trip trip = tripService.createTripFromBooking(request.getBookingId(), user);
        TripResponse response = tripMapper.toResponse(trip);
        
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{tripId}")
    @Operation(summary = "Get trip by ID", 
               description = "Retrieves a trip by ID. Users can only access trips they are authorized to view.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Trip retrieved successfully"),
        @ApiResponse(responseCode = "403", description = "Not authorized to view this trip"),
        @ApiResponse(responseCode = "404", description = "Trip not found")
    })
    public ResponseEntity<TripResponse> getTripById(
            @PathVariable Long tripId,
            Authentication authentication) {
        
        log.debug("Getting trip {} for user {}", tripId, getUserId(authentication));
        
        User user = getUserFromAuthentication(authentication);
        Optional<Trip> trip = tripSecurityService.getAuthorizedTrip(tripId, user);
        
        if (trip.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        
        TripResponse response = tripMapper.toResponse(trip.get());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{tripId}/location")
    @Operation(summary = "Get current trip location",
               description = "Retrieves the latest GPS point for an authorized trip")
    public ResponseEntity<RouteDataService.GpsLocationData> getCurrentTripLocation(
            @PathVariable Long tripId,
            Authentication authentication) {
        User user = getUserFromAuthentication(authentication);
        return ResponseEntity.ok(tripService.getCurrentTripLocation(tripId, user));
    }

    @GetMapping("/{tripId}/route")
    @Operation(summary = "Get trip route history",
               description = "Retrieves chronological GPS points for an authorized trip")
    public ResponseEntity<List<RouteDataService.GpsLocationData>> getTripRouteHistory(
            @PathVariable Long tripId,
            @RequestParam(defaultValue = "500") Integer limit,
            Authentication authentication) {
        User user = getUserFromAuthentication(authentication);
        int safeLimit = Math.min(Math.max(limit, 1), 500);
        return ResponseEntity.ok(tripService.getTripRouteHistory(tripId, safeLimit, user));
    }

    @GetMapping("/{tripId}/route/analytics")
    @Operation(summary = "Get trip route analytics",
               description = "Retrieves distance, speed, and GPS statistics for an authorized trip")
    public ResponseEntity<RouteDataService.RouteAnalytics> getTripRouteAnalytics(
            @PathVariable Long tripId,
            Authentication authentication) {
        User user = getUserFromAuthentication(authentication);
        return ResponseEntity.ok(tripService.getTripRouteAnalytics(tripId, user));
    }

    @GetMapping
    @Operation(summary = "Get trips for user", 
               description = "Retrieves trips accessible to the authenticated user with pagination and filtering")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Trips retrieved successfully")
    })
    public ResponseEntity<Page<TripResponse>> getTrips(
            @Parameter(description = "Filter by one or more trip statuses; repeat the parameter for multiple values")
            @RequestParam(required = false) List<String> status,
            
            @Parameter(description = "Filter by trip type")
            @RequestParam(required = false) String type,
            
            @Parameter(description = "Page number (0-based)")
            @RequestParam(defaultValue = "0") int page,
            
            @Parameter(description = "Page size (max 100)")
            @RequestParam(defaultValue = "20") int size,
            
            @Parameter(description = "Sort field (id, startTime, tripStatus)")
            @RequestParam(defaultValue = "startTime") String sortBy,
            
            @Parameter(description = "Sort direction (asc, desc)")
            @RequestParam(defaultValue = "desc") String sortDir,
            
            Authentication authentication) {
        
        log.debug("Getting trips for user {} with filters - status: {}, type: {}", 
                 getUserId(authentication), status, type);
        
        User user = getUserFromAuthentication(authentication);
        
        // Validate and limit page size
        if (page < 0 || size < 1) {
            return ResponseEntity.badRequest().build();
        }
        size = Math.min(size, MAX_PAGE_SIZE);
        
        // Create sort direction
        Sort.Direction direction = "asc".equalsIgnoreCase(sortDir) ? 
                Sort.Direction.ASC : Sort.Direction.DESC;
        Sort sort = Sort.by(direction, sortBy);
        if (!"id".equals(sortBy)) {
            sort = sort.and(Sort.by(direction, "id"));
        }
        Pageable pageable = PageRequest.of(page, size, sort);
        
        Page<Trip> trips;
        
        if (status != null) {
            try {
                List<Trip.TripStatus> tripStatuses = new LinkedHashSet<>(status.stream()
                        .map(value -> Trip.TripStatus.valueOf(value.trim().toUpperCase(Locale.ROOT)))
                        .toList()).stream().toList();
                if (tripStatuses.isEmpty()) {
                    return ResponseEntity.badRequest().build();
                }
                trips = tripStatuses.size() == 1
                        ? tripSecurityService.getAuthorizedTripsByStatus(user, tripStatuses.get(0), pageable)
                        : tripSecurityService.getAuthorizedTripsByStatuses(user, tripStatuses, pageable);
            } catch (IllegalArgumentException e) {
                log.warn("Invalid trip status: {}", status);
                return ResponseEntity.badRequest().build();
            }
        } else {
            trips = tripSecurityService.getAuthorizedTrips(user, pageable);
        }
        
        Page<TripResponse> response = trips.map(tripMapper::toResponse);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{tripId}/status")
    @Operation(summary = "Update trip status", 
               description = "Updates the status of a trip. Drivers can update their own trips, admins can update any trip.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Trip status updated successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid status transition"),
        @ApiResponse(responseCode = "403", description = "Not authorized to update this trip"),
        @ApiResponse(responseCode = "404", description = "Trip not found")
    })
    public ResponseEntity<TripResponse> updateTripStatus(
            @PathVariable Long tripId,
            @Valid @RequestBody UpdateTripStatusRequest request,
            Authentication authentication) {
        
        log.debug("Updating trip {} status to {} for user {}", 
                 tripId, request.getTripStatus(), getUserId(authentication));
        
        User user = getUserFromAuthentication(authentication);
        
        try {
            Trip.TripStatus targetStatus = Trip.TripStatus.valueOf(request.getTripStatus().toUpperCase());
            
            Trip updatedTrip;
            if (request.getForceTransition() && isAdmin(user)) {
                updatedTrip = tripStatusTransitionService.forceStatusTransition(
                    tripId, targetStatus, user, request.getReason());
            } else {
                updatedTrip = tripStatusTransitionService.executeStatusTransition(
                    tripId, targetStatus, user);
            }
            
            TripResponse response = tripMapper.toResponse(updatedTrip);
            return ResponseEntity.ok(response);
            
        } catch (IllegalArgumentException e) {
            log.warn("Invalid trip status: {}", request.getTripStatus());
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping("/{tripId}/gps")
    @Operation(summary = "Add GPS location", 
               description = "Adds a GPS location point to the trip. Only the assigned driver can add GPS locations.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "GPS location added successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid GPS data"),
        @ApiResponse(responseCode = "403", description = "Not authorized to update GPS for this trip"),
        @ApiResponse(responseCode = "404", description = "Trip not found")
    })
    public ResponseEntity<TripResponse> addGpsLocation(
            @PathVariable Long tripId,
            @Valid @RequestBody AddGpsLocationRequest request,
            Authentication authentication) {
        
        log.debug("Adding GPS location for trip {} by user {}", tripId, getUserId(authentication));
        
        User user = getUserFromAuthentication(authentication);
        
        // Convert request to GPS data map
        Map<String, Object> gpsData = new HashMap<>();
        gpsData.put("lat", request.getLatitude());
        gpsData.put("lng", request.getLongitude());
        
        if (request.getSpeed() != null) {
            gpsData.put("speed", request.getSpeed());
        }
        if (request.getAccuracy() != null) {
            gpsData.put("accuracy", request.getAccuracy());
        }
        if (request.getTimestamp() != null) {
            gpsData.put("ts", request.getTimestamp().toString());
        }
        
        Trip updatedTrip = tripService.updateLocation(tripId, gpsData, user);
        TripResponse response = tripMapper.toResponse(updatedTrip);
        
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{tripId}/start")
    @Operation(summary = "Start trip", 
               description = "Starts a trip. Only the assigned driver can start their trip.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Trip started successfully"),
        @ApiResponse(responseCode = "400", description = "Trip cannot be started"),
        @ApiResponse(responseCode = "403", description = "Not authorized to start this trip"),
        @ApiResponse(responseCode = "404", description = "Trip not found")
    })
    public ResponseEntity<TripResponse> startTrip(
            @PathVariable Long tripId,
            Authentication authentication) {
        
        log.debug("Starting trip {} for user {}", tripId, getUserId(authentication));
        
        User user = getUserFromAuthentication(authentication);
        Trip trip = tripService.startTrip(tripId, user);
        TripResponse response = tripMapper.toResponse(trip);
        
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{tripId}/arrive")
    @Operation(summary = "Mark arrival at destination",
               description = "Phase 7 — Driver confirms arrival at the delivery destination. " +
                             "Transitions trip from ONGOING to ARRIVED.")
    public ResponseEntity<TripResponse> markArrived(
            @PathVariable Long tripId,
            Authentication authentication) {
        log.debug("markArrived: tripId={} userId={}", tripId, getUserId(authentication));
        User user = getUserFromAuthentication(authentication);
        Trip trip = tripService.markArrived(tripId, user);
        return ResponseEntity.ok(tripMapper.toResponse(trip));
    }

    @PostMapping("/{tripId}/confirm-delivery")
    @PreAuthorize("hasRole('SHIPPER')")
    @Operation(summary = "Confirm delivery",
               description = "The freight's shipper confirms delivery after the assigned driver has arrived.")
    public ResponseEntity<TripResponse> confirmDelivery(
            @PathVariable Long tripId,
            Authentication authentication) {
        User shipper = getUserFromAuthentication(authentication);
        Trip trip = tripService.confirmDeliveryByShipper(tripId, shipper);
        return ResponseEntity.ok(tripMapper.toResponse(trip));
    }

    @PostMapping("/{tripId}/complete")
    @Operation(summary = "Complete trip", 
               description = "Legacy direct completion endpoint. Assigned drivers must use shipper-confirmed delivery.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Trip completed successfully"),
        @ApiResponse(responseCode = "400", description = "Trip cannot be completed"),
        @ApiResponse(responseCode = "403", description = "Not authorized to complete this trip"),
        @ApiResponse(responseCode = "404", description = "Trip not found")
    })
    public ResponseEntity<TripResponse> completeTrip(
            @PathVariable Long tripId,
            Authentication authentication) {
        
        log.debug("Completing trip {} for user {}", tripId, getUserId(authentication));
        
        User user = getUserFromAuthentication(authentication);
        Trip trip = tripService.completeTrip(tripId, user);
        TripResponse response = tripMapper.toResponse(trip);
        
        return ResponseEntity.ok(response);
    }

    // Helper methods
    private User getUserFromAuthentication(Authentication authentication) {
        return (User) authentication.getPrincipal();
    }

    private Long getUserId(Authentication authentication) {
        return getUserFromAuthentication(authentication).getId();
    }

    private boolean isAdmin(User user) {
        return user.getRole() == User.Role.ADMIN;
    }
}