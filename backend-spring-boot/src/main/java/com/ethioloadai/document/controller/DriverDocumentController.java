package com.ethioloadai.document.controller;

import com.ethioloadai.document.dto.*;
import com.ethioloadai.document.entity.DriverDocument;
import com.ethioloadai.document.service.DriverDocumentService;
import com.ethioloadai.user.entity.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * REST controller for driver KYC document verification.
 *
 * <h2>Endpoint summary</h2>
 * <pre>
 *   GET    /api/documents               — list all documents (admin)
 *   GET    /api/documents/{id}/file     — view/stream document file (owner/admin)
 *   GET    /api/driver/documents/{id}/file — alias for file access (owner/admin)
 *   POST   /api/documents               — submit/resubmit document (driver/admin)
 *   POST   /api/documents/{id}/review   — approve/reject (admin only)
 *   GET    /api/documents/{id}          — get document by ID
 *   GET    /api/documents/driver/{driverId} — get all documents for a driver
 *   GET    /api/documents/pending       — get pending review queue (admin)
 *   GET    /api/documents/kyc/{driverId} — get KYC status summary
 * </pre>
 */
@RestController
@RequestMapping({"/documents", "/driver/documents", "/admin/driver-documents"})
@RequiredArgsConstructor
@Tag(name = "KYC Documents", description = "Driver KYC document verification workflow")
public class DriverDocumentController {

    private static final Logger log = LoggerFactory.getLogger(DriverDocumentController.class);

    private final DriverDocumentService documentService;

    // ── All Documents (Admin) ──────────────────────────────────────────────────

    /**
     * Admin lists all documents across drivers, optionally filtered by status.
     */
    @GetMapping
    @Operation(summary = "Get all documents (admin)",
               description = "Admin lists all documents across drivers, optionally filtered by status.")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<DocumentResponse>> getAllDocuments(
            @RequestParam(required = false) DriverDocument.DocumentStatus status,
            @AuthenticationPrincipal User callerUser) {

        log.info("GET documents status={} caller={}", status, callerUser.getId());
        return ResponseEntity.ok(documentService.getAllDocuments(callerUser, status));
    }

    // ── File download / view ──────────────────────────────────────────────────

    /**
     * Download or view private document file.
     * Accessible by document owner or admin.
     */
    @GetMapping("/{id}/file")
    @Operation(summary = "View/download document file",
               description = "Serves the private document file. Caller must be document owner or admin.")
    @PreAuthorize("hasAnyRole('DRIVER','ADMIN')")
    public ResponseEntity<Resource> getDocumentFile(
            @PathVariable Long id,
            @AuthenticationPrincipal User callerUser) {

        log.info("GET document file id={} caller={}", id, callerUser.getId());
        DriverDocumentService.DocumentFile docFile = documentService.getDocumentFile(id, callerUser);

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(docFile.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline().filename(docFile.filename(), StandardCharsets.UTF_8).build().toString())
                .body(docFile.resource());
    }

    // ── Submit ────────────────────────────────────────────────────────────────

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload driver KYC document")
    @PreAuthorize("hasRole('DRIVER')")
    public ResponseEntity<DocumentResponse> uploadDocument(
            @RequestParam DriverDocument.DocumentType documentType,
            @RequestPart("file") MultipartFile file,
            @AuthenticationPrincipal User callerUser) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(documentService.uploadDocument(documentType, file, callerUser));
    }

    /**
     * Submit a new document or replace an existing one.
     *
     * <p>Creates a PENDING record. If a document of the same type already exists,
     * it is replaced (re-submission workflow).
     */
    @PostMapping
    @Operation(summary = "Submit/resubmit document",
               description = "Driver submits a KYC document. Creates PENDING record.")
    @PreAuthorize("hasAnyRole('DRIVER','ADMIN')")
    public ResponseEntity<DocumentResponse> submitDocument(
            @Valid @RequestBody DocumentSubmissionRequest request,
            @AuthenticationPrincipal User callerUser) {

        log.info("POST /api/documents type={} caller={}", request.getDocumentType(), callerUser.getId());
        DocumentResponse response = documentService.submitDocument(request, callerUser);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // ── Bulk KYC decision ─────────────────────────────────────────────────────

    /**
     * Admin approves all submitted documents for a driver in one action.
     *
     * <p>This is the primary KYC approval flow. The admin reviews the driver's complete
     * submission and approves the driver as a whole. All PENDING documents are set to
     * APPROVED. If this completes KYC (all 5 approved) the driver is activated.
     */
    @PostMapping("/kyc/{driverId}/approve-all")
    @Operation(summary = "Bulk-approve driver KYC (admin)",
               description = "Approves all submitted documents for a driver in one action. " +
                             "Activates the driver when all 5 required documents are approved.")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> approveAllDocuments(
            @PathVariable Long driverId,
            @AuthenticationPrincipal User callerUser) {

        log.info("POST /api/documents/kyc/{}/approve-all admin={}", driverId, callerUser.getId());
        documentService.approveAllDocuments(driverId, callerUser);
        return ResponseEntity.ok().build();
    }

    // ── Review ────────────────────────────────────────────────────────────────

    /**
     * Admin approves or rejects a document.
     *
     * <p>If this approval completes KYC (all 5 approved), the driver is activated.
     */
    @PostMapping("/{id}/review")
    @Operation(summary = "Review document (admin)",
               description = "Admin approves or rejects a document. " +
                             "Driver activation occurs automatically when all 5 documents are approved.")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<DocumentResponse> reviewDocument(
            @PathVariable Long id,
            @Valid @RequestBody DocumentReviewRequest request,
            @AuthenticationPrincipal User callerUser) {

        log.info("POST /api/documents/{}/review status={} admin={}", id, request.getStatus(), callerUser.getId());
        DocumentResponse response = documentService.reviewDocument(id, request, callerUser);
        return ResponseEntity.ok(response);
    }

    // ── Queries ───────────────────────────────────────────────────────────────

    @GetMapping("/{id}")
    @Operation(summary = "Get document by ID")
    @PreAuthorize("hasAnyRole('DRIVER','ADMIN')")
    public ResponseEntity<DocumentResponse> getDocumentById(
            @PathVariable Long id,
            @AuthenticationPrincipal User callerUser) {

        return ResponseEntity.ok(documentService.getDocumentById(id, callerUser));
    }

    @GetMapping("/driver/{driverId}")
    @Operation(summary = "Get all documents for a driver")
    @PreAuthorize("hasAnyRole('DRIVER','ADMIN')")
    public ResponseEntity<List<DocumentResponse>> getDocumentsForDriver(
            @PathVariable Long driverId,
            @AuthenticationPrincipal User callerUser) {

        return ResponseEntity.ok(documentService.getDocumentsForDriver(driverId, callerUser));
    }

    @GetMapping("/pending")
    @Operation(summary = "Get pending documents (admin review queue)")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<DocumentResponse>> getPendingDocuments(
            @AuthenticationPrincipal User callerUser) {

        return ResponseEntity.ok(documentService.getPendingDocuments(callerUser));
    }

    @GetMapping("/kyc/{driverId}")
    @Operation(summary = "Get KYC status summary")
    @PreAuthorize("hasAnyRole('DRIVER','ADMIN')")
    public ResponseEntity<KYCStatusResponse> getKYCStatus(
            @PathVariable Long driverId,
            @AuthenticationPrincipal User callerUser) {

        return ResponseEntity.ok(documentService.getKYCStatus(driverId, callerUser));
    }
}
