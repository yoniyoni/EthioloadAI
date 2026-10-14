package com.ethioloadai.dashboard.controller;

import com.ethioloadai.dashboard.dto.DashboardSummaryResponse;
import com.ethioloadai.dashboard.dto.DriverDashboardResponse;
import com.ethioloadai.dashboard.service.DashboardService;
import com.ethioloadai.user.entity.User;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/dashboard")
@RequiredArgsConstructor
public class DashboardController {
    private final DashboardService dashboardService;

    @GetMapping("/summary")
    @PreAuthorize("hasAnyRole('SHIPPER', 'ADMIN')")
    @Operation(
            summary = "Get bounded dashboard summary",
            description = "Returns owner-scoped lifetime counts and bounded recent freight, booking, and trip activity.")
    public DashboardSummaryResponse getSummary(@AuthenticationPrincipal User user) {
        return dashboardService.getSummary(user);
    }

    @GetMapping("/driver-summary")
    @PreAuthorize("hasRole('DRIVER')")
    @Operation(
            summary = "Get driver dashboard booking summary",
            description = "Returns principal-scoped lifetime booking counts and up to three recent pending bookings.")
    public DriverDashboardResponse getDriverSummary(@AuthenticationPrincipal User user) {
        return dashboardService.getDriverSummary(user);
    }
}
