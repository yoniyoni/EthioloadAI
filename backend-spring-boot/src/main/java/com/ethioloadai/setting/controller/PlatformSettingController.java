package com.ethioloadai.setting.controller;

import com.ethioloadai.setting.dto.PricingSettingsResponse;
import com.ethioloadai.setting.dto.UpdatePricingSettingsRequest;
import com.ethioloadai.setting.service.PlatformSettingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for admin platform settings.
 *
 * <h2>Endpoints</h2>
 * <pre>
 *   GET   /api/admin/settings/pricing  — retrieve platform pricing rates (ADMIN only)
 *   PATCH /api/admin/settings/pricing  — update platform pricing rates (ADMIN only)
 * </pre>
 */
@RestController
@RequestMapping("/admin/settings/pricing")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin Settings", description = "Platform configuration and pricing rates management (ADMIN only)")
public class PlatformSettingController {

    private static final Logger log = LoggerFactory.getLogger(PlatformSettingController.class);

    private final PlatformSettingService platformSettingService;

    @GetMapping
    @Operation(summary = "Get platform pricing settings",
               description = "Returns current freight rate boundaries and intra-city reference rates. Requires ADMIN role.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Settings retrieved successfully"),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "403", description = "Forbidden - requires ADMIN role")
    })
    public ResponseEntity<PricingSettingsResponse> getPricingSettings() {
        log.debug("GET /api/admin/settings/pricing called");
        return ResponseEntity.ok(platformSettingService.getPricingSettings());
    }

    @PatchMapping
    @Operation(summary = "Update platform pricing settings",
               description = "Updates rate_min and rate_max pricing boundaries. Requires ADMIN role.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Settings updated successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid request or validation failed"),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "403", description = "Forbidden - requires ADMIN role"),
        @ApiResponse(responseCode = "422", description = "Unprocessable entity - rate_min must be less than rate_max")
    })
    public ResponseEntity<PricingSettingsResponse> updatePricingSettings(
            @Valid @RequestBody UpdatePricingSettingsRequest request) {
        log.info("PATCH /api/admin/settings/pricing called with rate_min={}, rate_max={}",
                request.getRateMin(), request.getRateMax());
        return ResponseEntity.ok(platformSettingService.updatePricingSettings(request));
    }
}
