package com.ethioloadai.settlement.controller;

import com.ethioloadai.settlement.dto.CarrierSettlementResponse;
import com.ethioloadai.settlement.service.CarrierSettlementService;
import com.ethioloadai.user.entity.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Phase 7 — Carrier Settlement REST endpoints.
 *
 * <p>Base path: {@code /api/settlements}
 */
@RestController
@RequestMapping("/settlements")
@RequiredArgsConstructor
@Tag(name = "Carrier Settlements", description = "Phase 7 — carrier commercial settlement lifecycle")
public class CarrierSettlementController {

    private final CarrierSettlementService settlementService;

    // ── Query endpoints ───────────────────────────────────────────────────────

    @GetMapping("/{settlementId}")
    @Operation(summary = "Get settlement by ID")
    public ResponseEntity<CarrierSettlementResponse> getSettlement(
            @PathVariable Long settlementId,
            Authentication authentication) {
        return ResponseEntity.ok(settlementService.getSettlement(settlementId, getUser(authentication)));
    }

    @GetMapping("/booking/{bookingId}")
    @Operation(summary = "Get settlement by booking ID")
    public ResponseEntity<CarrierSettlementResponse> getByBookingId(
            @PathVariable Long bookingId,
            Authentication authentication) {
        return ResponseEntity.ok(settlementService.getSettlementByBookingId(bookingId, getUser(authentication)));
    }

    @GetMapping("/carrier/{carrierCompanyId}")
    @Operation(summary = "Get all settlements for a carrier company",
               description = "Carrier members see their own financial history.")
    public ResponseEntity<List<CarrierSettlementResponse>> getCarrierSettlements(
            @PathVariable Long carrierCompanyId,
            Authentication authentication) {
        return ResponseEntity.ok(
                settlementService.getCarrierSettlements(carrierCompanyId, getUser(authentication)));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Admin: list all settlements")
    public ResponseEntity<List<CarrierSettlementResponse>> getAllSettlements(
            Authentication authentication) {
        return ResponseEntity.ok(settlementService.getAllSettlements(getUser(authentication)));
    }

    // ── Admin lifecycle transitions ────────────────────────────────────────────

    @PostMapping("/{settlementId}/process")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Admin: mark settlement as PROCESSING")
    public ResponseEntity<CarrierSettlementResponse> markProcessing(
            @PathVariable Long settlementId,
            Authentication authentication) {
        return ResponseEntity.ok(settlementService.markProcessing(settlementId, getUser(authentication)));
    }

    @PostMapping("/{settlementId}/pay")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Admin: mark settlement as PAID")
    public ResponseEntity<CarrierSettlementResponse> markPaid(
            @PathVariable Long settlementId,
            @RequestBody(required = false) Map<String, String> body,
            Authentication authentication) {
        String txId = body != null ? body.get("externalTxId") : null;
        String ref = body != null ? body.get("paymentReference") : null;
        return ResponseEntity.ok(
                settlementService.markPaid(settlementId, txId, ref, getUser(authentication)));
    }

    @PostMapping("/{settlementId}/fail")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Admin: mark settlement as FAILED")
    public ResponseEntity<CarrierSettlementResponse> markFailed(
            @PathVariable Long settlementId,
            @RequestBody(required = false) Map<String, String> body,
            Authentication authentication) {
        String reason = body != null ? body.getOrDefault("reason", "Payment processing failed") : "Payment processing failed";
        return ResponseEntity.ok(settlementService.markFailed(settlementId, reason, getUser(authentication)));
    }

    @PostMapping("/{settlementId}/retry")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Admin: retry a FAILED settlement")
    public ResponseEntity<CarrierSettlementResponse> retry(
            @PathVariable Long settlementId,
            Authentication authentication) {
        return ResponseEntity.ok(settlementService.retrySettlement(settlementId, getUser(authentication)));
    }

    @PostMapping("/{settlementId}/block")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Admin: block settlement (e.g. due to dispute)")
    public ResponseEntity<CarrierSettlementResponse> block(
            @PathVariable Long settlementId,
            @RequestBody(required = false) Map<String, String> body,
            Authentication authentication) {
        String reason = body != null ? body.getOrDefault("reason", "Blocked pending dispute resolution") : "Blocked";
        return ResponseEntity.ok(settlementService.blockSettlement(settlementId, reason, getUser(authentication)));
    }

    @PostMapping("/{settlementId}/unblock")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Admin: unblock settlement after dispute resolution")
    public ResponseEntity<CarrierSettlementResponse> unblock(
            @PathVariable Long settlementId,
            Authentication authentication) {
        return ResponseEntity.ok(settlementService.unblockSettlement(settlementId, getUser(authentication)));
    }

    private User getUser(Authentication auth) {
        return (User) auth.getPrincipal();
    }
}
