package com.ethioloadai.dispute.controller;

import com.ethioloadai.dispute.dto.CreateDisputeRequest;
import com.ethioloadai.dispute.dto.DisputeListResponse;
import com.ethioloadai.dispute.dto.DisputeResponse;
import com.ethioloadai.dispute.dto.ResolveDisputeRequest;
import com.ethioloadai.dispute.exception.DisputeException;
import com.ethioloadai.dispute.service.DisputeService;
import com.ethioloadai.user.entity.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/disputes")
@RequiredArgsConstructor
@Validated
@Tag(name = "Disputes", description = "Endpoints for managing freight disputes and arbitration")
public class DisputeController {

    private static final Logger log = LoggerFactory.getLogger(DisputeController.class);

    private final DisputeService disputeService;

    @GetMapping
    @Operation(summary = "Get disputes",
               description = "Retrieves disputes for a specific freight (if freightId parameter supplied) " +
                             "or all platform disputes (if caller is admin).")
    public ResponseEntity<DisputeListResponse> getDisputes(
            @Parameter(description = "Optional freight ID filter")
            @RequestParam(required = false) Long freightId,
            Authentication authentication) {

        User caller = getUserFromAuthentication(authentication);
        if (freightId != null) {
            log.info("GET /api/disputes?freightId={} caller={}", freightId, caller.getId());
            return ResponseEntity.ok(disputeService.getDisputesByFreightId(freightId, caller));
        } else {
            log.info("GET /api/disputes (all) caller={}", caller.getId());
            return ResponseEntity.ok(disputeService.getAllDisputes(caller));
        }
    }

    @PostMapping
    @Operation(summary = "File dispute",
               description = "Files a new dispute for a freight shipment. Caller must be shipper or assigned driver.")
    public ResponseEntity<DisputeResponse> fileDispute(
            @Valid @RequestBody CreateDisputeRequest request,
            Authentication authentication) {

        User caller = getUserFromAuthentication(authentication);
        log.info("POST /api/disputes freightId={} caller={}", request.freightId(), caller.getId());

        DisputeResponse response = disputeService.createDispute(request, caller);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PatchMapping("/{id}/resolve")
    @Operation(summary = "Resolve dispute",
               description = "Adjudicates and resolves a dispute. Administrator only.")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<DisputeResponse> resolveDispute(
            @Parameter(description = "Dispute ID", required = true)
            @PathVariable Long id,
            @Valid @RequestBody ResolveDisputeRequest request,
            Authentication authentication) {

        User caller = getUserFromAuthentication(authentication);
        log.info("PATCH /api/disputes/{}/resolve resolution={} caller={}", id, request.resolution(), caller.getId());

        DisputeResponse response = disputeService.resolveDispute(id, request, caller);
        return ResponseEntity.ok(response);
    }

    private User getUserFromAuthentication(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof User user)) {
            throw new DisputeException.UnauthorizedDisputeAccessException("Unauthenticated access");
        }
        return user;
    }
}
