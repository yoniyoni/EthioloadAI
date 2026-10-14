package com.ethioloadai.carrier.company.controller;

import com.ethioloadai.carrier.company.dto.*;
import com.ethioloadai.carrier.company.entity.VerificationStatus;
import com.ethioloadai.carrier.company.service.CarrierCompanyService;
import com.ethioloadai.user.entity.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin/carrier-companies")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin Carrier Companies", description = "Company application review; documents are reviewed as a single application")
public class AdminCarrierCompanyController {
    private final CarrierCompanyService service;

    @GetMapping
    @Operation(summary = "List company applications with an optional verification status filter")
    public Page<CarrierCompanyResponse> list(@RequestParam(required = false) VerificationStatus status,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal User caller) {
        return service.applications(status, page, size, caller);
    }

    @GetMapping("/{id}")
    @Operation(summary = "View a carrier application; documents are available at /carrier-companies/{id}/documents")
    public CarrierCompanyResponse get(@PathVariable Long id, @AuthenticationPrincipal User caller) {
        return service.get(id, caller);
    }

    @PostMapping("/{id}/approve")
    @Operation(summary = "Approve a PENDING company and its submitted documents; self-review is forbidden")
    public CarrierCompanyResponse approve(@PathVariable Long id, @AuthenticationPrincipal User caller) {
        return service.review(id, VerificationStatus.APPROVED, null, caller);
    }

    @PostMapping("/{id}/reject")
    @Operation(summary = "Reject a PENDING company with a required message; rejection is final in Phase 1")
    public CarrierCompanyResponse reject(@PathVariable Long id, @Valid @RequestBody CarrierCompanyReviewRequest request,
                                        @AuthenticationPrincipal User caller) {
        return service.review(id, VerificationStatus.REJECTED, request.getMessage(), caller);
    }

    @PostMapping("/{id}/request-information")
    @Operation(summary = "Request corrections with a required message, allowing owner edits and resubmission")
    public CarrierCompanyResponse requestInformation(@PathVariable Long id,
            @Valid @RequestBody CarrierCompanyReviewRequest request, @AuthenticationPrincipal User caller) {
        return service.review(id, VerificationStatus.MORE_INFORMATION_REQUIRED, request.getMessage(), caller);
    }
}

