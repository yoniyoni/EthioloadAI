package com.ethioloadai.trip.controller;

import com.ethioloadai.security.model.UserDetailsImpl;
import com.ethioloadai.trip.dto.*;
import com.ethioloadai.trip.entity.TripStop;
import com.ethioloadai.trip.mapper.TripMapper;
import com.ethioloadai.trip.service.TripStopService;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/trips/{tripId}/stops")
@RequiredArgsConstructor
@Validated
@Tag(name = "Trip Stop Management", description = "Multi-stop management endpoints for drivers, shippers, and admins")
public class TripStopController {
    private static final Logger log = LoggerFactory.getLogger(TripStopController.class);

    private final TripStopService tripStopService;
    private final TripMapper tripMapper;

    @PostMapping
    @Operation(summary = "Add a stop to a trip", 
               description = "Adds a new stop to a multi-stop trip. Validates ordering and business rules. Only the assigned driver or admin can add stops.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "Stop added successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid stop data or ordering"),
        @ApiResponse(responseCode = "403", description = "Not authorized to add stops to this trip"),
        @ApiResponse(responseCode = "404", description = "Trip not found"),
        @ApiResponse(responseCode = "422", description = "Business validation failed")
    })
    public ResponseEntity<TripStopResponse> addStop(
            @PathVariable Long tripId,
            @Valid @RequestBody CreateTripStopRequest request,
            Authentication authentication) {
        
        log.debug("Adding stop to trip {} by user {} - order: {}, location: {}", 
                 tripId, getUserId(authentication), request.getStopOrder(), request.getLocationName());
        
        User user = getUserFromAuthentication(authentication);
        
        TripStop stop = tripStopService.addStop(
                tripId,
                request.getLocationName(),
                request.getStopOrder(),
                request.getPickupLatitude(),
                request.getPickupLongitude(),
                request.getAgreedPrice(),
                request.getCargoRequestId(),
                null, // notes - not in current DTO but could be added
                user
        );
        
        TripStopResponse response = tripMapper.toTripStopResponse(stop);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    @Operation(summary = "Get all stops for a trip", 
               description = "Retrieves all stops for a trip in order. Users can only view stops for trips they have access to.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Trip stops retrieved successfully"),
        @ApiResponse(responseCode = "403", description = "Not authorized to view stops for this trip"),
        @ApiResponse(responseCode = "404", description = "Trip not found")
    })
    public ResponseEntity<List<TripStopResponse>> getTripStops(
            @PathVariable Long tripId,
            Authentication authentication) {
        
        log.debug("Getting stops for trip {} by user {}", tripId, getUserId(authentication));
        
        User user = getUserFromAuthentication(authentication);
        List<TripStop> stops = tripStopService.getTripStops(tripId, user);
        
        List<TripStopResponse> response = stops.stream()
                .map(tripMapper::toTripStopResponse)
                .toList();
        
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{stopId}")
    @Operation(summary = "Get specific stop by ID", 
               description = "Retrieves a specific trip stop by ID. Users can only access stops for trips they are authorized to view.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Stop retrieved successfully"),
        @ApiResponse(responseCode = "403", description = "Not authorized to view this stop"),
        @ApiResponse(responseCode = "404", description = "Stop not found")
    })
    public ResponseEntity<TripStopResponse> getStop(
            @PathVariable Long tripId,
            @PathVariable Long stopId,
            Authentication authentication) {
        
        log.debug("Getting stop {} for trip {} by user {}", stopId, tripId, getUserId(authentication));
        
        User user = getUserFromAuthentication(authentication);
        TripStop stop = tripStopService.getStopWithAuthorization(stopId, user);
        
        TripStopResponse response = tripMapper.toTripStopResponse(stop);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{stopId}")
    @Operation(summary = "Update a trip stop", 
               description = "Updates stop details (location, price, etc.). Only the assigned driver or admin can update stops.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Stop updated successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid stop data"),
        @ApiResponse(responseCode = "403", description = "Not authorized to update this stop"),
        @ApiResponse(responseCode = "404", description = "Stop not found"),
        @ApiResponse(responseCode = "422", description = "Business validation failed")
    })
    public ResponseEntity<TripStopResponse> updateStop(
            @PathVariable Long tripId,
            @PathVariable Long stopId,
            @Valid @RequestBody UpdateTripStopRequest request,
            Authentication authentication) {
        
        log.debug("Updating stop {} for trip {} by user {}", stopId, tripId, getUserId(authentication));
        
        User user = getUserFromAuthentication(authentication);
        
        TripStop stop = tripStopService.updateStop(
                stopId,
                request.getLocationName(),
                request.getPickupLatitude(),
                request.getPickupLongitude(),
                request.getAgreedPrice(),
                null, // notes - not in current DTO
                user
        );
        
        TripStopResponse response = tripMapper.toTripStopResponse(stop);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{stopId}/status")
    @Operation(summary = "Update stop status", 
               description = "Updates stop status (PENDING → ARRIVED → LOADED → COMPLETED). Only the assigned driver can update stop status.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Stop status updated successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid status transition"),
        @ApiResponse(responseCode = "403", description = "Not authorized to update stop status"),
        @ApiResponse(responseCode = "404", description = "Stop not found"),
        @ApiResponse(responseCode = "422", description = "Status transition not allowed")
    })
    public ResponseEntity<TripStopResponse> updateStopStatus(
            @PathVariable Long tripId,
            @PathVariable Long stopId,
            @Valid @RequestBody UpdateTripStopStatusRequest request,
            Authentication authentication) {
        
        log.debug("Updating stop {} status to {} for trip {} by user {}", 
                 stopId, request.getStopStatus(), tripId, getUserId(authentication));
        
        User user = getUserFromAuthentication(authentication);
        
        try {
            TripStop.StopStatus targetStatus = TripStop.StopStatus.valueOf(request.getStopStatus().toUpperCase());
            TripStop stop = tripStopService.updateStopStatus(stopId, targetStatus, user);
            
            TripStopResponse response = tripMapper.toTripStopResponse(stop);
            return ResponseEntity.ok(response);
            
        } catch (IllegalArgumentException e) {
            log.warn("Invalid stop status: {}", request.getStopStatus());
            return ResponseEntity.badRequest().build();
        }
    }

    @DeleteMapping("/{stopId}")
    @Operation(summary = "Remove a stop from trip", 
               description = "Removes a stop from a multi-stop trip and reorders remaining stops. Only the assigned driver or admin can remove stops.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "204", description = "Stop removed successfully"),
        @ApiResponse(responseCode = "403", description = "Not authorized to remove this stop"),
        @ApiResponse(responseCode = "404", description = "Stop not found"),
        @ApiResponse(responseCode = "422", description = "Stop cannot be removed in current state")
    })
    public ResponseEntity<Void> removeStop(
            @PathVariable Long tripId,
            @PathVariable Long stopId,
            Authentication authentication) {
        
        log.debug("Removing stop {} from trip {} by user {}", stopId, tripId, getUserId(authentication));
        
        User user = getUserFromAuthentication(authentication);
        tripStopService.removeStop(stopId, user);
        
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{stopId}/arrive")
    @Operation(summary = "Mark arrival at stop", 
               description = "Records driver arrival at a stop. Sets status to ARRIVED and timestamps. Only the assigned driver can mark arrival.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Arrival recorded successfully"),
        @ApiResponse(responseCode = "403", description = "Not authorized to mark arrival for this stop"),
        @ApiResponse(responseCode = "404", description = "Stop not found"),
        @ApiResponse(responseCode = "422", description = "Cannot mark arrival in current stop status")
    })
    public ResponseEntity<TripStopResponse> arriveAtStop(
            @PathVariable Long tripId,
            @PathVariable Long stopId,
            Authentication authentication) {
        
        log.debug("Driver arriving at stop {} for trip {} by user {}", stopId, tripId, getUserId(authentication));
        
        User user = getUserFromAuthentication(authentication);
        TripStop stop = tripStopService.arriveAtStop(stopId, user);
        
        TripStopResponse response = tripMapper.toTripStopResponse(stop);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{stopId}/load")
    @Operation(summary = "Mark cargo loading at stop", 
               description = "Records cargo loading at a stop. Sets status to LOADED. Only the assigned driver can mark loading.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Loading recorded successfully"),
        @ApiResponse(responseCode = "403", description = "Not authorized to mark loading for this stop"),
        @ApiResponse(responseCode = "404", description = "Stop not found"),
        @ApiResponse(responseCode = "422", description = "Cannot mark loading in current stop status")
    })
    public ResponseEntity<TripStopResponse> loadAtStop(
            @PathVariable Long tripId,
            @PathVariable Long stopId,
            Authentication authentication) {
        
        log.debug("Driver loading cargo at stop {} for trip {} by user {}", stopId, tripId, getUserId(authentication));
        
        User user = getUserFromAuthentication(authentication);
        TripStop stop = tripStopService.loadAtStop(stopId, user);
        
        TripStopResponse response = tripMapper.toTripStopResponse(stop);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{stopId}/complete")
    @Operation(summary = "Complete a stop", 
               description = "Marks a stop as completed. Sets status to COMPLETED and timestamps. Only the assigned driver can complete stops.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Stop completed successfully"),
        @ApiResponse(responseCode = "403", description = "Not authorized to complete this stop"),
        @ApiResponse(responseCode = "404", description = "Stop not found"),
        @ApiResponse(responseCode = "422", description = "Cannot complete stop in current status")
    })
    public ResponseEntity<TripStopResponse> completeStop(
            @PathVariable Long tripId,
            @PathVariable Long stopId,
            Authentication authentication) {
        
        log.debug("Driver completing stop {} for trip {} by user {}", stopId, tripId, getUserId(authentication));
        
        User user = getUserFromAuthentication(authentication);
        TripStop stop = tripStopService.completeStop(stopId, user);
        
        TripStopResponse response = tripMapper.toTripStopResponse(stop);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/reorder")
    @Operation(summary = "Reorder stops in trip", 
               description = "Updates the order of stops in a multi-stop trip. Accepts new ordering and reorders atomically. Only the assigned driver or admin can reorder stops.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Stops reordered successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid stop ordering"),
        @ApiResponse(responseCode = "403", description = "Not authorized to reorder stops for this trip"),
        @ApiResponse(responseCode = "404", description = "Trip not found"),
        @ApiResponse(responseCode = "422", description = "Reordering not allowed in current trip state")
    })
    public ResponseEntity<List<TripStopResponse>> reorderStops(
            @PathVariable Long tripId,
            @Valid @RequestBody ReorderStopsRequest request,
            Authentication authentication) {
        
        log.debug("Reordering stops for trip {} by user {} - new order: {}", 
                 tripId, getUserId(authentication), request.getStopIds());
        
        User user = getUserFromAuthentication(authentication);
        List<TripStop> reorderedStops = tripStopService.reorderStops(tripId, request.getStopIds(), user);
        
        List<TripStopResponse> response = reorderedStops.stream()
                .map(tripMapper::toTripStopResponse)
                .toList();
        
        return ResponseEntity.ok(response);
    }

    @GetMapping("/summary")
    @Operation(summary = "Get trip stops summary", 
               description = "Retrieves summary information for all stops including total amount, completion status, etc.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Stops summary retrieved successfully"),
        @ApiResponse(responseCode = "403", description = "Not authorized to view summary for this trip"),
        @ApiResponse(responseCode = "404", description = "Trip not found")
    })
    public ResponseEntity<TripStopsSummaryResponse> getStopsSummary(
            @PathVariable Long tripId,
            Authentication authentication) {
        
        log.debug("Getting stops summary for trip {} by user {}", tripId, getUserId(authentication));
        
        User user = getUserFromAuthentication(authentication);
        List<TripStop> stops = tripStopService.getTripStops(tripId, user);
        BigDecimal totalAmount = tripStopService.calculateTotalStopAmount(tripId);
        
        // Calculate summary metrics
        long totalStops = stops.size();
        long completedStops = stops.stream().mapToLong(stop -> 
                stop.getStopStatus() == TripStop.StopStatus.COMPLETED ? 1 : 0).sum();
        long pendingStops = stops.stream().mapToLong(stop -> 
                stop.getStopStatus() == TripStop.StopStatus.PENDING ? 1 : 0).sum();
        long inProgressStops = totalStops - completedStops - pendingStops;
        
        TripStopsSummaryResponse response = TripStopsSummaryResponse.builder()
                .tripId(tripId)
                .totalStops((int) totalStops)
                .completedStops((int) completedStops)
                .pendingStops((int) pendingStops)
                .inProgressStops((int) inProgressStops)
                .totalAmount(totalAmount)
                .completionPercentage(totalStops > 0 ? (double) completedStops / totalStops * 100 : 0.0)
                .build();
        
        return ResponseEntity.ok(response);
    }

    @GetMapping("/attention")
    @Operation(summary = "Get stops requiring attention", 
               description = "Retrieves stops that require attention (stuck, overdue, etc.). Useful for monitoring and alerts.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Attention stops retrieved successfully"),
        @ApiResponse(responseCode = "403", description = "Not authorized to view attention stops for this trip"),
        @ApiResponse(responseCode = "404", description = "Trip not found")
    })
    public ResponseEntity<List<TripStopResponse>> getStopsRequiringAttention(
            @PathVariable Long tripId,
            Authentication authentication) {
        
        log.debug("Getting attention stops for trip {} by user {}", tripId, getUserId(authentication));
        
        User user = getUserFromAuthentication(authentication);
        
        // First verify user has access to this trip by trying to get stops
        tripStopService.getTripStops(tripId, user);
        
        List<TripStop> attentionStops = tripStopService.getStopsRequiringAttention(tripId);
        
        List<TripStopResponse> response = attentionStops.stream()
                .map(tripMapper::toTripStopResponse)
                .toList();
        
        return ResponseEntity.ok(response);
    }

    // Helper methods
    private User getUserFromAuthentication(Authentication authentication) {
        return (User) authentication.getPrincipal();
    }

    private Long getUserId(Authentication authentication) {
        return getUserFromAuthentication(authentication).getId();
    }
}