package com.ethioloadai.vehicle.document.controller;

import com.ethioloadai.user.entity.User;
import com.ethioloadai.vehicle.document.dto.VehicleDocumentResponse;
import com.ethioloadai.vehicle.document.dto.VehicleDocumentReviewRequest;
import com.ethioloadai.vehicle.document.entity.VehicleDocument;
import com.ethioloadai.vehicle.document.service.VehicleDocumentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequiredArgsConstructor
@Tag(name = "Vehicle Documents", description = "Vehicle document lifecycle and admin review")
public class VehicleDocumentController {

    private final VehicleDocumentService vehicleDocumentService;

    @GetMapping("/vehicles/{vehicleId}/documents")
    @PreAuthorize("hasAnyRole('DRIVER', 'FLEET_OWNER', 'ADMIN')")
    @Operation(summary = "List documents for a vehicle")
    public ResponseEntity<List<VehicleDocumentResponse>> listVehicleDocuments(
            @PathVariable Long vehicleId,
            Authentication authentication) {
        User caller = (User) authentication.getPrincipal();
        return ResponseEntity.ok(vehicleDocumentService.getDocumentsForVehicle(vehicleId, caller));
    }

    @PostMapping(value = "/vehicles/{vehicleId}/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('DRIVER', 'FLEET_OWNER', 'ADMIN')")
    @Operation(summary = "Upload or replace a vehicle document")
    public ResponseEntity<VehicleDocumentResponse> uploadVehicleDocument(
            @PathVariable Long vehicleId,
            @RequestParam("documentType") String documentType,
            @RequestPart("file") MultipartFile file,
            Authentication authentication) {
        User caller = (User) authentication.getPrincipal();
        VehicleDocument.DocumentType parsedType = VehicleDocument.DocumentType.valueOf(documentType.trim().toUpperCase().replace('-', '_'));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(vehicleDocumentService.uploadDocument(vehicleId, parsedType, file, caller));
    }

    @GetMapping("/vehicle-documents/{documentId}/file")
    @PreAuthorize("hasAnyRole('DRIVER', 'FLEET_OWNER', 'ADMIN')")
    @Operation(summary = "Serve a vehicle document file privately")
    public ResponseEntity<Resource> getVehicleDocumentFile(
            @PathVariable Long documentId,
            Authentication authentication) {
        User caller = (User) authentication.getPrincipal();
        VehicleDocumentService.DocumentFile docFile = vehicleDocumentService.getDocumentFile(documentId, caller);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(docFile.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline().filename(docFile.filename(), StandardCharsets.UTF_8).build().toString())
                .body(docFile.resource());
    }

    @GetMapping("/admin/vehicle-documents")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "List all vehicle documents for admin review")
    public ResponseEntity<List<VehicleDocumentResponse>> listAllVehicleDocuments(
            @RequestParam(value = "status", required = false) String status,
            Authentication authentication) {
        User admin = (User) authentication.getPrincipal();
        VehicleDocument.DocumentStatus parsedStatus = status == null || status.isBlank()
                ? null
                : VehicleDocument.DocumentStatus.valueOf(status.trim().toUpperCase());
        return ResponseEntity.ok(vehicleDocumentService.getAllDocuments(admin, parsedStatus));
    }

    @PatchMapping("/admin/vehicle-documents/{documentId}/review")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Review a vehicle document")
    public ResponseEntity<VehicleDocumentResponse> reviewVehicleDocument(
            @PathVariable Long documentId,
            @RequestBody VehicleDocumentReviewRequest request,
            Authentication authentication) {
        User admin = (User) authentication.getPrincipal();
        return ResponseEntity.ok(vehicleDocumentService.reviewDocument(documentId, request, admin));
    }
}
