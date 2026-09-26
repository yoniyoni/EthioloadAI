package com.ethioloadai.tracking.controller;

import com.ethioloadai.tracking.dto.GpsValidationResult;
import com.ethioloadai.tracking.dto.LocationUpdateRequest;
import com.ethioloadai.tracking.service.GeofencingService;
import com.ethioloadai.tracking.service.GpsValidationService;
import com.ethioloadai.tracking.service.LocationUpdateFilterService;
import com.ethioloadai.tracking.service.VehicleTripConsistencyService;
import com.ethioloadai.trip.entity.Trip;
import com.ethioloadai.trip.service.TripProgressService;
import com.ethioloadai.trip.service.TripService;
import com.ethioloadai.user.entity.User;
import com.ethioloadai.websocket.broadcasting.LocationBroadcastService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * REST Controller for real-time GPS tracking operations.
 * 
 * Provides endpoints for submitting GPS location updates with vehicle
 * consistency validation and comprehensive data quality checks.
 */
@RestController
@RequestMapping("/v1/tracking")
@RequiredArgsConstructor
@Tag(name = "GPS Tracking", description = "Real-time vehicle GPS tracking operations")
public class GpsTrackingController {
    private static final Logger log = LoggerFactory.getLogger(GpsTrackingController.class);
    
    private final TripService tripService;
    private final GpsValidationService gpsValidationService;
    private final VehicleTripConsistencyService vehicleConsistencyService;
    private final LocationBroadcastService broadcastService;
    private final LocationUpdateFilterService locationFilterService;
    private final GeofencingService geofencingService;
    private final TripProgressService tripProgressService;
    
    /**
     * Submit GPS location update for a trip with vehicle consistency validation.
     * 
     * @param tripId the trip receiving the GPS update
     * @param locationRequest validated GPS location data with vehicleId
     * @param user authenticated user (driver/fleet owner)
     * @return updated trip information
     */
    @PostMapping("/trips/{tripId}/location")
    @Operation(summary = "Update trip location", 
               description = "Submit GPS location update with vehicle consistency validation")
    public ResponseEntity<TripLocationResponse> updateTripLocation(
            @PathVariable Long tripId,
            @Valid @RequestBody LocationUpdateRequest locationRequest,
            @AuthenticationPrincipal User user) {
        
        log.info("GPS update for trip {} from vehicle {} by user {}", 
                tripId, locationRequest.getVehicleId(), user.getId());
        
        // Validate GPS data quality first
        GpsValidationResult validation = 
            gpsValidationService.validateLocationUpdate(locationRequest);
        
        if (!validation.isValid()) {
            log.warn("GPS validation failed for trip {}: {}", tripId, validation.getSummaryMessage());
            return ResponseEntity.badRequest()
                .body(TripLocationResponse.validationFailed(validation.getSummaryMessage()));
        }

        // Phase 12: domain-level filtering (duplicates, teleportation, bounding box)
        LocationUpdateFilterService.FilterResult filterResult =
            locationFilterService.filter(tripId, locationRequest);

        if (!filterResult.isAccepted()) {
            log.warn("GPS fix discarded for trip {} [{}]: {}", tripId, filterResult.filterCode(), filterResult.reason());
            return ResponseEntity.badRequest()
                .body(TripLocationResponse.validationFailed(filterResult.reason()));
        }
        if (filterResult.action() == LocationUpdateFilterService.FilterAction.WARN_AND_ACCEPT) {
            log.warn("GPS fix accepted with warning for trip {} [{}]: {}",
                     tripId, filterResult.filterCode(), filterResult.reason());
        }
        
        // Update location with vehicle consistency validation
        Trip updatedTrip = tripService.updateLocationWithVehicleValidation(tripId, locationRequest, user);

        // ── Phase 11: real-time broadcast ────────────────────────────────────
        LocationBroadcastService.LocationUpdate broadcast = new LocationBroadcastService.LocationUpdate(
            tripId,
            locationRequest.getVehicleId(),
            user.getId(),
            locationRequest.getLatitude(),
            locationRequest.getLongitude(),
            null,                               // altitude – not in request DTO
            locationRequest.getSpeed(),
            locationRequest.getHeading(),
            locationRequest.getAccuracy(),
            locationRequest.getTimestamp(),
            "DRIVER_APP"
        );
        broadcastService.broadcastLocationUpdate(broadcast);

        // ── Phase 17: geofence check ──────────────────────────────────────────
        geofencingService.checkGeofences(
            tripId, locationRequest.getVehicleId(),
            locationRequest.getLatitude(), locationRequest.getLongitude(),
            locationRequest.getTimestamp()
        );

        // ── Phase 16: trip progress broadcast ────────────────────────────────
        tripProgressService.calculateAndBroadcast(tripId);
        // ─────────────────────────────────────────────────────────────────────
        // ─────────────────────────────────────────────────────────────────────

        log.debug("Successfully processed GPS update for trip {} from vehicle {}", 
                 tripId, locationRequest.getVehicleId());
        
        return ResponseEntity.ok(TripLocationResponse.success(updatedTrip, validation));
    }
    
    /**
     * Get authorized vehicle for a trip.
     * 
     * @param tripId the trip ID
     * @param user authenticated user
     * @return vehicle ID authorized for the trip
     */
    @GetMapping("/trips/{tripId}/vehicle")
    @Operation(summary = "Get authorized vehicle", 
               description = "Get the vehicle ID authorized to submit GPS updates for a trip")
    public ResponseEntity<VehicleAuthorizationResponse> getAuthorizedVehicle(
            @PathVariable Long tripId,
            @AuthenticationPrincipal User user) {
        
        Long vehicleId = vehicleConsistencyService.getAuthorizedVehicleForTrip(tripId, user);
        
        return ResponseEntity.ok(new VehicleAuthorizationResponse(tripId, vehicleId));
    }
    
    /**
     * Check if vehicle has active trip.
     * 
     * @param vehicleId the vehicle ID to check
     * @param user authenticated user
     * @return active trip information if exists
     */
    @GetMapping("/vehicles/{vehicleId}/active-trip")
    @Operation(summary = "Check vehicle active trip", 
               description = "Check if vehicle is currently assigned to an active trip")
    public ResponseEntity<VehicleActiveTripResponse> getVehicleActiveTrip(
            @PathVariable Long vehicleId,
            @AuthenticationPrincipal User user) {
        
        Long activeTripId = vehicleConsistencyService.getActiveTripForVehicle(vehicleId, user);
        
        return ResponseEntity.ok(new VehicleActiveTripResponse(vehicleId, activeTripId));
    }
    
    // Response DTOs
    
    public static class TripLocationResponse {
        private final boolean success;
        private final String message;
        private final Long tripId;
        private final Long vehicleId;
        private final GpsValidationResult validation;
        
        private TripLocationResponse(boolean success, String message, Long tripId, 
                                   Long vehicleId, GpsValidationResult validation) {
            this.success = success;
            this.message = message;
            this.tripId = tripId;
            this.vehicleId = vehicleId;
            this.validation = validation;
        }
        
        public static TripLocationResponse success(Trip trip, GpsValidationResult validation) {
            return new TripLocationResponse(true, "Location updated successfully", 
                                          trip.getId(), 
                                          trip.getBooking().getVehicle().getId(), 
                                          validation);
        }
        
        public static TripLocationResponse validationFailed(String reason) {
            return new TripLocationResponse(false, reason, null, null, null);
        }
        
        // Getters
        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
        public Long getTripId() { return tripId; }
        public Long getVehicleId() { return vehicleId; }
        public GpsValidationResult getValidation() { return validation; }
    }
    
    public static class VehicleAuthorizationResponse {
        private final Long tripId;
        private final Long authorizedVehicleId;
        
        public VehicleAuthorizationResponse(Long tripId, Long authorizedVehicleId) {
            this.tripId = tripId;
            this.authorizedVehicleId = authorizedVehicleId;
        }
        
        // Getters
        public Long getTripId() { return tripId; }
        public Long getAuthorizedVehicleId() { return authorizedVehicleId; }
    }
    
    public static class VehicleActiveTripResponse {
        private final Long vehicleId;
        private final Long activeTripId;
        private final boolean hasActiveTrip;
        
        public VehicleActiveTripResponse(Long vehicleId, Long activeTripId) {
            this.vehicleId = vehicleId;
            this.activeTripId = activeTripId;
            this.hasActiveTrip = activeTripId != null;
        }
        
        // Getters
        public Long getVehicleId() { return vehicleId; }
        public Long getActiveTripId() { return activeTripId; }
        public boolean hasActiveTrip() { return hasActiveTrip; }
    }
}