package com.ethioloadai.analytics.controller;

import com.ethioloadai.analytics.dto.*;
import com.ethioloadai.analytics.service.AdminAnalyticsService;
import com.ethioloadai.user.entity.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Administrative controller for platform KPIs, escrow overview, analytics, and driver status management.
 *
 * <h2>Endpoint Summary:</h2>
 * <pre>
 *   GET   /api/admin/stats               — Platform high-level overview metrics (users, freight, revenue, escrow)
 *   GET   /api/admin/escrow              — Escrow statistics breakdown (held, inTransit, released, disputed)
 *   GET   /api/admin/analytics/revenue   — Monthly revenue, deliveries, and commission metrics for current year
 *   GET   /api/admin/analytics/routes    — Top 10 freight routes by volume
 *   GET   /api/admin/analytics/cargo     — Cargo material type distribution with percentage breakdown
 *   PATCH /api/drivers/{id}/status       — Update driver verification and active status (Admin only)
 *   PATCH /api/admin/drivers/{id}/status — Approved alias for driver status management
 * </pre>
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin Analytics & Operations", description = "Platform KPIs, escrow, analytics, and driver management")
public class AdminAnalyticsController {

    private final AdminAnalyticsService adminAnalyticsService;

    @GetMapping("/admin/stats")
    @Operation(summary = "Get platform overview stats (Admin only)",
               description = "Returns aggregate counts for users, drivers, active freight, completed deliveries, revenue, and active vehicles.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Platform stats successfully retrieved"),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "403", description = "Forbidden — Admin role required")
    })
    public ResponseEntity<AdminStatsResponse> getPlatformStats(@AuthenticationPrincipal User adminUser) {
        log.info("GET /api/admin/stats requested by admin={}", adminUser != null ? adminUser.getId() : "unknown");
        AdminStatsResponse response = adminAnalyticsService.getPlatformStats(adminUser);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/admin/escrow")
    @Operation(summary = "Get escrow overview (Admin only)",
               description = "Returns escrow metrics broken down into held, in-transit, released, and disputed buckets.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Escrow metrics successfully retrieved"),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "403", description = "Forbidden — Admin role required")
    })
    public ResponseEntity<AdminEscrowResponse> getEscrowOverview(@AuthenticationPrincipal User adminUser) {
        log.info("GET /api/admin/escrow requested by admin={}", adminUser != null ? adminUser.getId() : "unknown");
        AdminEscrowResponse response = adminAnalyticsService.getEscrowOverview(adminUser);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/admin/analytics/revenue")
    @Operation(summary = "Get monthly revenue analytics (Admin only)",
               description = "Returns 12-month series of revenue, commission, and delivery counts for the current year.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Revenue analytics successfully retrieved"),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "403", description = "Forbidden — Admin role required")
    })
    public ResponseEntity<List<MonthlyRevenueAnalyticsResponse>> getRevenueAnalytics(@AuthenticationPrincipal User adminUser) {
        log.info("GET /api/admin/analytics/revenue requested by admin={}", adminUser != null ? adminUser.getId() : "unknown");
        List<MonthlyRevenueAnalyticsResponse> response = adminAnalyticsService.getRevenueAnalytics(adminUser);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/admin/analytics/routes")
    @Operation(summary = "Get top freight routes analytics (Admin only)",
               description = "Returns top 10 origin-destination pairs with cargo load counts.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Route analytics successfully retrieved"),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "403", description = "Forbidden — Admin role required")
    })
    public ResponseEntity<List<RouteAnalyticsResponse>> getRouteAnalytics(@AuthenticationPrincipal User adminUser) {
        log.info("GET /api/admin/analytics/routes requested by admin={}", adminUser != null ? adminUser.getId() : "unknown");
        List<RouteAnalyticsResponse> response = adminAnalyticsService.getRouteAnalytics(adminUser);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/admin/analytics/cargo")
    @Operation(summary = "Get cargo type distribution analytics (Admin only)",
               description = "Returns cargo distribution by material type with volume count and integer percentage.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Cargo analytics successfully retrieved"),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "403", description = "Forbidden — Admin role required")
    })
    public ResponseEntity<List<CargoAnalyticsResponse>> getCargoAnalytics(@AuthenticationPrincipal User adminUser) {
        log.info("GET /api/admin/analytics/cargo requested by admin={}", adminUser != null ? adminUser.getId() : "unknown");
        List<CargoAnalyticsResponse> response = adminAnalyticsService.getCargoAnalytics(adminUser);
        return ResponseEntity.ok(response);
    }

    @PatchMapping({"/drivers/{id}/status", "/admin/drivers/{id}/status"})
    @Operation(summary = "Update driver status (Admin only)",
               description = "Updates driver status (active, suspended, approved, etc.) and synchronizes verification and active flags.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Driver status successfully updated"),
        @ApiResponse(responseCode = "400", description = "Invalid status requested"),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "403", description = "Forbidden — Admin role required"),
        @ApiResponse(responseCode = "404", description = "Driver not found")
    })
    public ResponseEntity<DriverStatusUpdateResponse> updateDriverStatus(
            @PathVariable("id") Long id,
            @Valid @RequestBody UpdateDriverStatusRequest request,
            @AuthenticationPrincipal User adminUser) {
        log.info("PATCH driver status id={} status={} requested by admin={}",
                id, request.getStatus(), adminUser != null ? adminUser.getId() : "unknown");
        DriverStatusUpdateResponse response = adminAnalyticsService.updateDriverStatus(id, request, adminUser);
        return ResponseEntity.ok(response);
    }
}
