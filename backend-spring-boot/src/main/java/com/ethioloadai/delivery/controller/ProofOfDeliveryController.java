package com.ethioloadai.delivery.controller;

import com.ethioloadai.delivery.dto.ProofOfDeliveryResponse;
import com.ethioloadai.delivery.dto.VerifyDeliveryRequest;
import com.ethioloadai.delivery.service.ProofOfDeliveryService;
import com.ethioloadai.document.storage.DocumentStorage;
import com.ethioloadai.user.entity.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * Phase 7 — Proof of Delivery REST endpoints.
 *
 * <p>Base path: {@code /api/proof-of-delivery}
 *
 * <ul>
 *   <li>{@code POST /trips/{tripId}/proof} — driver submits proof</li>
 *   <li>{@code GET /trips/{tripId}/proof} — get proof for a trip</li>
 *   <li>{@code GET /{proofId}} — get proof by ID</li>
 *   <li>{@code GET /{proofId}/download} — download proof file</li>
 *   <li>{@code POST /{proofId}/verify} — shipper verifies or rejects</li>
 * </ul>
 */
@RestController
@RequestMapping("/proof-of-delivery")
@RequiredArgsConstructor
@Tag(name = "Proof of Delivery", description = "Phase 7 — delivery proof submission and verification")
public class ProofOfDeliveryController {

    private static final Logger log = LoggerFactory.getLogger(ProofOfDeliveryController.class);

    private final ProofOfDeliveryService proofService;

    // ── Driver: submit proof ──────────────────────────────────────────────────

    @PostMapping("/trips/{tripId}/proof")
    @PreAuthorize("hasRole('DRIVER')")
    @Operation(summary = "Submit proof of delivery",
               description = "Driver uploads proof after arriving at destination. " +
                             "Trip must be in ARRIVED status.")
    public ResponseEntity<ProofOfDeliveryResponse> submitProof(
            @PathVariable Long tripId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "recipientName", required = false) String recipientName,
            @RequestParam(value = "note", required = false) String note,
            Authentication authentication) {

        User driver = getUser(authentication);
        log.info("submitProof: tripId={} driverId={}", tripId, driver.getId());
        ProofOfDeliveryResponse response = proofService.submitProof(tripId, file, recipientName, note, driver);
        return ResponseEntity.ok(response);
    }

    // ── Get proof metadata ────────────────────────────────────────────────────

    @GetMapping("/trips/{tripId}/proof")
    @Operation(summary = "Get proof of delivery for a trip")
    public ResponseEntity<ProofOfDeliveryResponse> getProofForTrip(
            @PathVariable Long tripId,
            Authentication authentication) {
        User caller = getUser(authentication);
        return ResponseEntity.ok(proofService.getProofForTrip(tripId, caller));
    }

    @GetMapping("/{proofId}")
    @Operation(summary = "Get proof of delivery by ID")
    public ResponseEntity<ProofOfDeliveryResponse> getProofById(
            @PathVariable Long proofId,
            Authentication authentication) {
        User caller = getUser(authentication);
        return ResponseEntity.ok(proofService.getProofById(proofId, caller));
    }

    // ── Download proof file ───────────────────────────────────────────────────

    @GetMapping("/{proofId}/download")
    @Operation(summary = "Download proof file",
               description = "Authorization verified before streaming file content. " +
                             "Storage key is never exposed.")
    public ResponseEntity<Resource> downloadProof(
            @PathVariable Long proofId,
            Authentication authentication) {

        User caller = getUser(authentication);
        DocumentStorage.StoredDocument doc = proofService.downloadProof(proofId, caller);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"" + doc.filename() + "\"")
                .contentType(MediaType.parseMediaType(doc.contentType()))
                .body(doc.resource());
    }

    // ── Shipper: verify / reject ──────────────────────────────────────────────

    @PostMapping("/{proofId}/verify")
    @Operation(summary = "Verify or reject proof of delivery",
               description = "Shipper (or admin) verifies or rejects proof. " +
                             "action=VERIFY triggers delivery completion + settlement. " +
                             "action=REJECT reverts trip to ARRIVED for resubmission.")
    public ResponseEntity<ProofOfDeliveryResponse> verifyOrRejectProof(
            @PathVariable Long proofId,
            @Valid @RequestBody VerifyDeliveryRequest request,
            Authentication authentication) {

        User caller = getUser(authentication);
        log.info("verifyOrRejectProof: proofId={} action={} callerId={}", proofId, request.action(), caller.getId());
        return ResponseEntity.ok(proofService.verifyOrRejectProof(proofId, request, caller));
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private User getUser(Authentication auth) {
        return (User) auth.getPrincipal();
    }
}
