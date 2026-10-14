package com.ethioloadai.tracking.controller;

import com.ethioloadai.tracking.service.RouteBoundaryProtectionService;
import com.ethioloadai.trip.entity.Trip;
import com.ethioloadai.trip.service.TripService;
import com.ethioloadai.user.entity.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST Controller for route data boundary management and optimization.
 * 
 * Provides endpoints for monitoring route data health, applying optimization
 * strategies, and configuring boundary protection settings.
 */
@RestController
@RequestMapping("/v1/tracking/route-boundaries")
@RequiredArgsConstructor
@Tag(name = "Route Boundary Management", description = "Route data optimization and boundary protection")
public class RouteBoundaryController {
    private static final Logger log = LoggerFactory.getLogger(RouteBoundaryController.class);
    
    private final RouteBoundaryProtectionService boundaryProtectionService;
    private final TripService tripService;
    
    /**
     * Check route data boundaries for a trip.
     * 
     * @param tripId trip to check
     * @param user authenticated user
     * @return boundary check results
     */
    @GetMapping("/trips/{tripId}/check")
    @Operation(summary = "Check route boundaries", 
               description = "Check current route data usage against configured limits")
    public ResponseEntity<RouteBoundaryProtectionService.BoundaryCheckResult> checkBoundaries(
            @PathVariable Long tripId,
            @AuthenticationPrincipal User user) {
        
        log.debug("Checking route boundaries for trip {} by user {}", tripId, user.getId());
        
        Trip trip = tripService.getTripWithAuthorization(tripId, user);
        RouteBoundaryProtectionService.BoundaryCheckResult result = 
            boundaryProtectionService.checkRouteBoundaries(trip);
        
        return ResponseEntity.ok(result);
    }
    
    /**
     * Get route health metrics for a trip.
     * 
     * @param tripId trip to analyze
     * @param user authenticated user
     * @return comprehensive health metrics
     */
    @GetMapping("/trips/{tripId}/health")
    @Operation(summary = "Get route health metrics", 
               description = "Get comprehensive health assessment of route data")
    public ResponseEntity<RouteBoundaryProtectionService.RouteHealthMetrics> getHealthMetrics(
            @PathVariable Long tripId,
            @AuthenticationPrincipal User user) {
        
        log.debug("Getting route health metrics for trip {} by user {}", tripId, user.getId());
        
        Trip trip = tripService.getTripWithAuthorization(tripId, user);
        RouteBoundaryProtectionService.RouteHealthMetrics metrics = 
            boundaryProtectionService.getRouteHealthMetrics(trip);
        
        return ResponseEntity.ok(metrics);
    }
    
    /**
     * Get optimization recommendations for a trip.
     * 
     * @param tripId trip to analyze
     * @param user authenticated user
     * @return AI-driven optimization recommendations
     */
    @GetMapping("/trips/{tripId}/recommendations")
    @Operation(summary = "Get optimization recommendations", 
               description = "Get AI-driven suggestions for route data optimization")
    public ResponseEntity<List<RouteBoundaryProtectionService.OptimizationRecommendation>> getRecommendations(
            @PathVariable Long tripId,
            @AuthenticationPrincipal User user) {
        
        log.debug("Getting optimization recommendations for trip {} by user {}", tripId, user.getId());
        
        Trip trip = tripService.getTripWithAuthorization(tripId, user);
        List<RouteBoundaryProtectionService.OptimizationRecommendation> recommendations = 
            boundaryProtectionService.getOptimizationRecommendations(trip);
        
        return ResponseEntity.ok(recommendations);
    }
    
    /**
     * Manually enforce route boundaries for a trip.
     * 
     * @param tripId trip to optimize
     * @param user authenticated user
     * @return boundary enforcement results
     */
    @PostMapping("/trips/{tripId}/enforce")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Enforce route boundaries", 
               description = "Manually trigger route boundary enforcement and optimization")
    public ResponseEntity<RouteBoundaryProtectionService.BoundaryEnforcementResult> enforceBoundaries(
            @PathVariable Long tripId,
            @AuthenticationPrincipal User user) {
        
        log.info("Manual boundary enforcement requested for trip {} by user {}", tripId, user.getId());
        
        Trip trip = tripService.getTripWithAuthorization(tripId, user);
        RouteBoundaryProtectionService.BoundaryEnforcementResult result = 
            boundaryProtectionService.enforceRouteBoundaries(trip);
        
        log.info("Manual boundary enforcement completed for trip {}: {}", 
                tripId, String.join(", ", result.actionsPerformed()));
        
        return ResponseEntity.ok(result);
    }
    
    /**
     * Compress route data for a trip.
     * 
     * @param tripId trip to compress
     * @param compressionLevel compression aggressiveness (1-10)
     * @param user authenticated user
     * @return compression results
     */
    @PostMapping("/trips/{tripId}/compress")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Compress route data", 
               description = "Apply route compression to reduce point count while preserving shape")
    public ResponseEntity<RouteBoundaryProtectionService.RouteCompressionResult> compressRoute(
            @PathVariable Long tripId,
            @RequestParam(defaultValue = "5") int compressionLevel,
            @AuthenticationPrincipal User user) {
        
        log.info("Route compression requested for trip {} with level {} by user {}", 
                tripId, compressionLevel, user.getId());
        
        if (compressionLevel < 1 || compressionLevel > 10) {
            return ResponseEntity.badRequest().build();
        }
        
        Trip trip = tripService.getTripWithAuthorization(tripId, user);
        RouteBoundaryProtectionService.RouteCompressionResult result = 
            boundaryProtectionService.compressRoute(trip, compressionLevel);
        
        log.info("Route compression completed for trip {}: {} → {} points", 
                tripId, result.originalPoints(), result.compressedPoints());
        
        return ResponseEntity.ok(result);
    }
    
    /**
     * Archive old route data for a trip.
     * 
     * @param tripId trip to archive data for
     * @param retentionHours how many hours of data to keep active
     * @param user authenticated user
     * @return archival results
     */
    @PostMapping("/trips/{tripId}/archive")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Archive old route data", 
               description = "Move old route data to archive storage to improve performance")
    public ResponseEntity<RouteBoundaryProtectionService.RouteArchivalResult> archiveOldData(
            @PathVariable Long tripId,
            @RequestParam(defaultValue = "24") int retentionHours,
            @AuthenticationPrincipal User user) {
        
        log.info("Route archival requested for trip {} (retention: {} hours) by user {}", 
                tripId, retentionHours, user.getId());
        
        if (retentionHours < 1 || retentionHours > 168) { // Max 1 week
            return ResponseEntity.badRequest().build();
        }
        
        Trip trip = tripService.getTripWithAuthorization(tripId, user);
        RouteBoundaryProtectionService.RouteArchivalResult result = 
            boundaryProtectionService.archiveOldRouteData(trip, retentionHours);
        
        log.info("Route archival completed for trip {}: {} points archived", 
                tripId, result.pointsArchived());
        
        return ResponseEntity.ok(result);
    }
    
    /**
     * Perform emergency cleanup for a trip with critical data levels.
     * Requires explicit confirmation due to potential data loss.
     * 
     * @param tripId trip requiring emergency cleanup
     * @param confirm confirmation flag (must be true)
     * @param user authenticated user
     * @return emergency cleanup results
     */
    @PostMapping("/trips/{tripId}/emergency-cleanup")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Emergency route cleanup", 
               description = "Aggressive data reduction for trips exceeding critical limits")
    public ResponseEntity<RouteBoundaryProtectionService.EmergencyCleanupResult> performEmergencyCleanup(
            @PathVariable Long tripId,
            @RequestParam(required = true) boolean confirm,
            @AuthenticationPrincipal User user) {
        
        if (!confirm) {
            return ResponseEntity.badRequest()
                .body(new RouteBoundaryProtectionService.EmergencyCleanupResult(
                    false, "CONFIRMATION_REQUIRED", 0, 0, 0.0, List.of(),
                    "Confirmation required for emergency cleanup", false));
        }
        
        log.warn("Emergency cleanup requested for trip {} by user {}", tripId, user.getId());
        
        Trip trip = tripService.getTripWithAuthorization(tripId, user);
        RouteBoundaryProtectionService.EmergencyCleanupResult result = 
            boundaryProtectionService.performEmergencyCleanup(trip, user.getId());
        
        log.warn("Emergency cleanup completed for trip {}: {} points removed ({}% data loss)", 
                tripId, result.pointsRemoved(), String.format("%.1f", result.dataLossPercentage()));
        
        return ResponseEntity.ok(result);
    }
    
    /**
     * Configure boundary settings for a trip type.
     * Admin only operation.
     * 
     * @param tripType trip type to configure
     * @param settings boundary protection settings
     * @param user authenticated user (must be admin)
     * @return success confirmation
     */
    @PutMapping("/settings/{tripType}")
    @Operation(summary = "Configure boundary settings", 
               description = "Configure route boundary protection settings for trip type (admin only)")
    public ResponseEntity<ConfigurationResponse> configureBoundarySettings(
            @PathVariable Trip.TripType tripType,
            @RequestBody RouteBoundaryProtectionService.BoundarySettings settings,
            @AuthenticationPrincipal User user) {
        
        // Check admin authorization
        if (!isAdmin(user)) {
            return ResponseEntity.status(403)
                .body(new ConfigurationResponse(false, "Admin access required"));
        }
        
        log.info("Boundary settings configuration requested for {} by admin {}", tripType, user.getId());
        
        try {
            boundaryProtectionService.configureBoundarySettings(tripType, settings);
            
            log.info("Boundary settings configured for {}: maxPoints={}, archiveThreshold={}", 
                    tripType, settings.maxPoints(), settings.archiveThreshold());
            
            return ResponseEntity.ok(new ConfigurationResponse(true, "Settings configured successfully"));
            
        } catch (Exception e) {
            log.error("Failed to configure boundary settings for {}: {}", tripType, e.getMessage(), e);
            return ResponseEntity.status(500)
                .body(new ConfigurationResponse(false, "Configuration failed: " + e.getMessage()));
        }
    }
    
    // ── Helper Methods ───────────────────────────────────────────────────────
    
    private boolean isAdmin(User user) {
        return User.Role.ADMIN.equals(user.getRole());
    }
    
    // ── Response DTOs ────────────────────────────────────────────────────────
    
    public record ConfigurationResponse(
        boolean success,
        String message
    ) {}
}