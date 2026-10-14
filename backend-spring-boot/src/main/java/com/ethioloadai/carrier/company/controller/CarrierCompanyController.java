package com.ethioloadai.carrier.company.controller;

import com.ethioloadai.carrier.company.dto.*;
import com.ethioloadai.carrier.company.entity.CompanyDocumentType;
import com.ethioloadai.carrier.company.service.CarrierCompanyService;
import com.ethioloadai.user.entity.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;

@RestController
@RequestMapping("/carrier-companies")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
@Tag(name = "Carrier Companies", description = "Private owner applications and company documents")
public class CarrierCompanyController {
    private final CarrierCompanyService service;

    @PostMapping
    @Operation(summary = "Create a DRAFT company owned by the authenticated user")
    public ResponseEntity<CarrierCompanyResponse> create(@Valid @RequestBody CarrierCompanyRequest request,
                                                        @AuthenticationPrincipal User caller) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request, caller));
    }

    @GetMapping("/me")
    @Operation(summary = "List the authenticated owner's companies")
    public Page<CarrierCompanyResponse> mine(@AuthenticationPrincipal User caller,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return service.mine(caller, page, size);
    }

    @GetMapping("/{id}")
    @Operation(summary = "View a company application (owner, active member, or admin)")
    public CarrierCompanyResponse get(@PathVariable Long id, @AuthenticationPrincipal User caller) {
        return service.get(id, caller);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Replace editable fields in a DRAFT or MORE_INFORMATION_REQUIRED application (owner only)")
    public CarrierCompanyResponse update(@PathVariable Long id, @Valid @RequestBody CarrierCompanyRequest request,
                                         @AuthenticationPrincipal User caller) {
        return service.update(id, request, caller);
    }

    @PostMapping("/{id}/submit")
    @Operation(summary = "Submit a complete application for admin review (owner only)")
    public CarrierCompanyResponse submit(@PathVariable Long id, @AuthenticationPrincipal User caller) {
        return service.submit(id, caller);
    }

    @PostMapping(value = "/{id}/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload or replace a private company document (owner only; PDF/JPEG/PNG, max 5 MB)")
    public ResponseEntity<CarrierCompanyDocumentResponse> upload(@PathVariable Long id,
            @RequestParam CompanyDocumentType documentType, @RequestPart("file") MultipartFile file,
            @AuthenticationPrincipal User caller) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.upload(id, documentType, file, caller));
    }

    @GetMapping("/{id}/documents")
    @Operation(summary = "List company document metadata (owner, active member, or admin)")
    public List<CarrierCompanyDocumentResponse> documents(@PathVariable Long id, @AuthenticationPrincipal User caller) {
        return service.documents(id, caller);
    }

    @GetMapping("/{id}/documents/{documentId}/file")
    @Operation(summary = "Download a private company document (owner, active member, or admin)")
    public ResponseEntity<Resource> download(@PathVariable Long id, @PathVariable Long documentId,
                                            @AuthenticationPrincipal User caller) {
        var file = service.download(id, documentId, caller);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(file.contentType()))
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(file.filename()).build().toString())
            .header(HttpHeaders.CACHE_CONTROL, "no-store")
            .header("X-Content-Type-Options", "nosniff")
            .body(file.resource());
    }
}

