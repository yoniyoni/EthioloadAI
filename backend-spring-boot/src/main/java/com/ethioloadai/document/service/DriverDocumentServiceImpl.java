package com.ethioloadai.document.service;

import com.ethioloadai.document.dto.*;
import com.ethioloadai.document.entity.DriverDocument;
import com.ethioloadai.document.entity.DriverDocument.DocumentStatus;
import com.ethioloadai.document.entity.DriverDocument.DocumentType;
import com.ethioloadai.document.exception.DocumentException;
import com.ethioloadai.document.repository.DriverDocumentRepository;
import com.ethioloadai.document.storage.DocumentStorage;
import com.ethioloadai.user.entity.User;
import com.ethioloadai.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import com.ethioloadai.websocket.notification.NotificationService;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.time.LocalDateTime;
import java.io.InputStream;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.Locale;
import java.util.UUID;

/**
 * Transaction-safe implementation of the driver KYC document workflow.
 *
 * <h2>Security invariants</h2>
 * <ul>
 *   <li>Document status is always server-assigned (client cannot submit APPROVED).</li>
 *   <li>Reviewer and review timestamp are server-generated.</li>
 *   <li>Only admins can approve/reject documents.</li>
 *   <li>Drivers cannot approve their own documents.</li>
 *   <li>Driver activation depends ONLY on all 5 documents being APPROVED.</li>
 * </ul>
 *
 * <h2>Driver activation</h2>
 * <ul>
 *   <li>A driver remains inactive (is_active = false) until all 5 required
 *       documents are APPROVED.</li>
 *   <li>When the 5th document is approved, the driver is automatically activated.</li>
 *   <li>If an approved document is later REJECTED, the driver is deactivated.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class DriverDocumentServiceImpl implements DriverDocumentService {

    private static final Logger log = LoggerFactory.getLogger(DriverDocumentServiceImpl.class);

    private final DriverDocumentRepository documentRepository;
    private final UserRepository           userRepository;
    private final EntityManager            entityManager;
    private final NotificationService      notificationService;
    private final DocumentStorage          documentStorage;

    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024;
    private static final Map<String, String> ALLOWED_TYPES = Map.of(
        "application/pdf", ".pdf", "image/jpeg", ".jpg", "image/png", ".png");

    @Override
    @Transactional
    public DocumentResponse uploadDocument(DocumentType documentType, MultipartFile file, User driverUser) {
        if (file == null || file.isEmpty()) throw new DocumentException.DocumentValidationException("Document file is required");
        if (file.getSize() > MAX_FILE_SIZE) throw new DocumentException.DocumentValidationException("Document must be 5 MB or smaller");
        String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        String extension = ALLOWED_TYPES.get(contentType);
        if (extension == null) throw new DocumentException.DocumentValidationException("Only PDF, JPG, and PNG documents are accepted");
        try {
            if (!hasExpectedSignature(file, contentType)) throw new DocumentException.DocumentValidationException("File content does not match its declared type");
        } catch (java.io.IOException e) {
            log.warn("Unable to read uploaded document for signature validation", e);
            throw new DocumentException.DocumentValidationException("Unable to read document content");
        }
        if (documentType == null) throw new DocumentException.DocumentValidationException("Document type is required");
        try {
            String objectKey = "driver-documents/" + driverUser.getId() + "/"
                    + documentType.name().toLowerCase(Locale.ROOT) + "/" + UUID.randomUUID() + extension;
            DriverDocument existing = documentRepository
                    .findByUser_IdAndDocumentType(driverUser.getId(), documentType).orElse(null);
            String previousObjectKey = existing == null ? null : existing.getFilePath();
            registerStorageCompensation(objectKey, previousObjectKey);
            documentStorage.put(objectKey, file);
            DocumentResponse response = submitDocument(DocumentSubmissionRequest.builder()
                .documentType(documentType).filePath(objectKey).originalName(file.getOriginalFilename() == null ? objectKey : file.getOriginalFilename())
                .mimeType(contentType).fileSize(file.getSize()).build(), driverUser);
            notificationService.notifyRole("admin", NotificationService.Notification.of(
                "DRIVER_DOCUMENT_SUBMITTED", "New driver verification pending", driverUser.getFullName() + " submitted " + documentType.getLabel(),
                NotificationService.Priority.HIGH, Map.of("driverId", driverUser.getId(), "documentId", response.getId())));
            return response;
        } catch (DocumentException e) { throw e;
        } catch (Exception e) { throw new DocumentException.DocumentValidationException("Document storage failed"); }
    }

    private boolean hasExpectedSignature(MultipartFile file, String contentType) throws java.io.IOException {
        try (InputStream input = file.getInputStream()) {
            byte[] header = input.readNBytes(8);
            if ("application/pdf".equals(contentType)) return header.length >= 5
                    && header[0] == '%' && header[1] == 'P' && header[2] == 'D' && header[3] == 'F' && header[4] == '-';
            if ("image/png".equals(contentType)) return header.length >= 8
                    && (header[0] & 0xff) == 0x89 && header[1] == 0x50 && header[2] == 0x4e && header[3] == 0x47;
            return header.length >= 3 && (header[0] & 0xff) == 0xff && (header[1] & 0xff) == 0xd8 && (header[2] & 0xff) == 0xff;
        }
    }

    private void registerStorageCompensation(String newObjectKey, String previousObjectKey) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException("Document upload requires an active transaction");
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_COMMITTED) {
                    if (previousObjectKey != null && !previousObjectKey.equals(newObjectKey)) {
                        deleteAfterCommit(previousObjectKey);
                    }
                } else if (status == STATUS_ROLLED_BACK) {
                    deleteAfterRollback(newObjectKey);
                } else {
                    log.warn("Document upload transaction outcome unknown; retaining storage objects for reconciliation");
                }
            }
        });
    }

    private void deleteAfterCommit(String objectKey) {
        try {
            documentStorage.delete(objectKey);
        } catch (RuntimeException e) {
            log.error("Committed document replacement left an obsolete storage object; reconciliation required");
        }
    }

    private void deleteAfterRollback(String objectKey) {
        try {
            documentStorage.delete(objectKey);
        } catch (RuntimeException e) {
            log.error("Rolled-back document upload left an orphaned storage object; reconciliation required");
        }
    }

    // ── Submit ────────────────────────────────────────────────────────────────

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public DocumentResponse submitDocument(DocumentSubmissionRequest request, User driverUser) {
        log.info("submitDocument: driverId={}, type={}", driverUser.getId(), request.getDocumentType());

        // Only drivers can submit documents for themselves
        if (driverUser.getRole() != User.Role.DRIVER && driverUser.getRole() != User.Role.ADMIN) {
            throw new DocumentException.DocumentAuthorizationException(
                "Only drivers can submit KYC documents");
        }
        if (driverUser.getRole() == User.Role.DRIVER
                && (request.getFilePath() == null
                || !request.getFilePath().startsWith("driver-documents/" + driverUser.getId() + "/")
                || request.getFilePath().contains("..")
                || request.getFilePath().contains("\\"))) {
            throw new DocumentException.DocumentValidationException("Invalid document object key");
        }

        // Check if a document of this type already exists (upsert behavior)
        DriverDocument existing = documentRepository
            .findByUser_IdAndDocumentType(driverUser.getId(), request.getDocumentType())
            .orElse(null);

        if (existing != null) {
            log.info("submitDocument: replacing existing documentId={} for type={}",
                existing.getId(), request.getDocumentType());
            
            // Replace: REJECTED → PENDING or APPROVED → PENDING (re-submission)
            // Validate transition
            DocumentStateMachine.assertTransitionAllowed(existing.getStatus(), DocumentStatus.PENDING);
            
            // Update existing record
            existing.setFilePath(request.getFilePath());
            existing.setFileUrl(request.getFilePath());   // legacy NOT NULL column
            existing.setDocumentLabel(request.getDocumentType().getLabel());
            existing.setOriginalName(request.getOriginalName());
            existing.setMimeType("application/octet-stream");
            existing.setFileSize(request.getFileSize() == null ? 0L : request.getFileSize());
            if (request.getMimeType() != null) existing.setMimeType(request.getMimeType());
            existing.setStatus(DocumentStatus.PENDING);
            existing.setRejectionReason(null);
            existing.setReviewedBy(null);
            existing.setReviewedAt(null);
            
            DriverDocument saved = documentRepository.save(existing);
            
            // Re-submission resets approval → driver may be deactivated if was active
            deactivateDriverIfKYCIncomplete(driverUser.getId());
            
            return DocumentResponse.from(saved);
        } else {
            // Create new document
            DriverDocument document = DriverDocument.builder()
                .user(driverUser)
                .documentType(request.getDocumentType())
                .documentLabel(request.getDocumentType().getLabel())
                .filePath(request.getFilePath())
                .fileUrl(request.getFilePath())   // legacy NOT NULL column — mirrors file_path
                .originalName(request.getOriginalName())
                .mimeType(request.getMimeType() == null ? "application/octet-stream" : request.getMimeType())
                .fileSize(request.getFileSize() == null ? 0L : request.getFileSize())
                .status(DocumentStatus.PENDING)
                .build();

            document = documentRepository.save(document);
            log.info("submitDocument: created documentId={}", document.getId());
            return DocumentResponse.from(document);
        }
    }

    // ── Bulk KYC approval ─────────────────────────────────────────────────────

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void approveAllDocuments(Long driverId, User adminUser) {
        log.info("approveAllDocuments: driverId={}, adminId={}", driverId, adminUser.getId());

        if (adminUser.getRole() != User.Role.ADMIN) {
            throw new DocumentException.DocumentAuthorizationException(
                "Only administrators can perform bulk KYC approval");
        }

        // Verify driver exists
        userRepository.findById(driverId)
            .orElseThrow(() -> new DocumentException.DocumentNotFoundException("Driver not found: " + driverId));

        List<DriverDocument> docs = documentRepository.findByUser_Id(driverId);
        if (docs.isEmpty()) {
            throw new DocumentException.DocumentValidationException(
                "Driver has not submitted any documents");
        }

        LocalDateTime now = LocalDateTime.now();
        for (DriverDocument doc : docs) {
            // Approve any document that is PENDING. Already-APPROVED docs are untouched.
            // REJECTED docs are re-approved (admin override).
            if (doc.getStatus() != DocumentStatus.APPROVED) {
                doc.setStatus(DocumentStatus.APPROVED);
                doc.setReviewedBy(adminUser);
                doc.setReviewedAt(now);
                doc.setRejectionReason(null);
                documentRepository.save(doc);
                log.info("approveAllDocuments: approved documentId={} type={}", doc.getId(), doc.getDocumentType());
            }
        }

        documentRepository.saveAllAndFlush(docs);

        // Trigger driver activation now that all submitted docs are approved
        activateDriverIfKYCComplete(driverId);
    }

    // ── Review ────────────────────────────────────────────────────────────────

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public DocumentResponse reviewDocument(Long documentId, DocumentReviewRequest request, User adminUser) {
        log.info("reviewDocument: documentId={}, status={}, adminId={}",
            documentId, request.getStatus(), adminUser.getId());

        // Only admins can review documents
        if (adminUser.getRole() != User.Role.ADMIN) {
            throw new DocumentException.DocumentAuthorizationException(
                "Only administrators can review documents");
        }

        // Load document
        DriverDocument document = documentRepository.findById(documentId)
            .orElseThrow(() -> new DocumentException.DocumentNotFoundException(documentId));

        // Validate status transition
        DocumentStateMachine.assertTransitionAllowed(document.getStatus(), request.getStatus());

        // Rejection requires a reason
        if (request.getStatus() == DocumentStatus.REJECTED) {
            if (request.getRejectionReason() == null || request.getRejectionReason().isBlank()) {
                throw new DocumentException.DocumentValidationException(
                    "Rejection reason is required when rejecting a document");
            }
        }

        // Apply review
        document.setStatus(request.getStatus());
        document.setReviewedBy(adminUser);
        document.setReviewedAt(LocalDateTime.now());
        document.setRejectionReason(
            request.getStatus() == DocumentStatus.REJECTED ? request.getRejectionReason() : null);

        document = documentRepository.save(document);
        log.info("reviewDocument: documentId={} status changed to {}", documentId, request.getStatus());

        // Trigger driver activation/deactivation based on new status
        Long driverId = document.getUser().getId();
        if (request.getStatus() == DocumentStatus.APPROVED) {
            activateDriverIfKYCComplete(driverId);
        } else if (request.getStatus() == DocumentStatus.REJECTED) {
            deactivateDriverIfKYCIncomplete(driverId);
        }

        return DocumentResponse.from(document);
    }

    // ── Queries ───────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public DocumentResponse getDocumentById(Long documentId, User callerUser) {
        DriverDocument document = documentRepository.findById(documentId)
            .orElseThrow(() -> new DocumentException.DocumentNotFoundException(documentId));
        
        authorizeDocumentAccess(document, callerUser, "view");
        return DocumentResponse.from(document);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DocumentResponse> getDocumentsForDriver(Long driverId, User callerUser) {
        // Authorization: driver themselves or admin
        if (callerUser.getRole() != User.Role.ADMIN && !callerUser.getId().equals(driverId)) {
            throw new DocumentException.DocumentAuthorizationException(
                "You can only view your own documents");
        }

        return documentRepository.findByUser_Id(driverId)
            .stream()
            .map(DocumentResponse::from)
            .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<DocumentResponse> getPendingDocuments(User adminUser) {
        if (adminUser.getRole() != User.Role.ADMIN) {
            throw new DocumentException.DocumentAuthorizationException(
                "Only administrators can view the review queue");
        }

        return documentRepository.findByStatus(DocumentStatus.PENDING)
            .stream()
            .map(DocumentResponse::from)
            .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<DocumentResponse> getAllDocuments(User adminUser, DocumentStatus status) {
        if (adminUser.getRole() != User.Role.ADMIN) {
            throw new DocumentException.DocumentAuthorizationException(
                "Only administrators can view all documents");
        }

        List<DriverDocument> docs = (status != null)
            ? documentRepository.findByStatusOrderByCreatedAtDesc(status)
            : documentRepository.findAllByOrderByCreatedAtDesc();

        return docs.stream()
            .map(DocumentResponse::from)
            .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public DocumentFile getDocumentFile(Long documentId, User callerUser) {
        DriverDocument document = documentRepository.findById(documentId)
            .orElseThrow(() -> new DocumentException.DocumentNotFoundException(documentId));

        authorizeDocumentAccess(document, callerUser, "download");

        String objectKey = document.getFilePath();
        if (objectKey == null || objectKey.isBlank()) {
            throw new DocumentException.DocumentNotFoundException("No file path recorded for document: " + documentId);
        }

        if (objectKey.contains("..") || objectKey.contains("\\") || objectKey.startsWith("/")) {
            throw new DocumentException.DocumentValidationException("Invalid file path: path traversal forbidden");
        }

        String filename = document.getOriginalName() == null || document.getOriginalName().isBlank()
                ? objectKey.substring(objectKey.lastIndexOf('/') + 1) : document.getOriginalName();
        DocumentStorage.StoredDocument stored;
        try {
            stored = documentStorage.get(objectKey, filename,
                    document.getMimeType() == null ? "application/octet-stream" : document.getMimeType());
        } catch (IllegalStateException e) {
            if (!"Document object not found".equals(e.getMessage())) throw e;
            throw new DocumentException.DocumentNotFoundException("Document not found on storage: " + documentId);
        } catch (NoSuchKeyException e) {
            throw new DocumentException.DocumentNotFoundException("Document not found on storage: " + documentId);
        } catch (S3Exception e) {
            if (e.statusCode() != 404) throw e;
            throw new DocumentException.DocumentNotFoundException("Document not found on storage: " + documentId);
        }
        return new DocumentFile(stored.resource(), stored.filename(), stored.contentType());
    }

    @Override
    @Transactional(readOnly = true)
    public KYCStatusResponse getKYCStatus(Long driverId, User callerUser) {
        // Authorization
        if (callerUser.getRole() != User.Role.ADMIN && !callerUser.getId().equals(driverId)) {
            throw new DocumentException.DocumentAuthorizationException(
                "You can only view your own KYC status");
        }

        // Load driver
        User driver = userRepository.findById(driverId)
            .orElseThrow(() -> new DocumentException.DocumentNotFoundException("Driver not found: " + driverId));

        // Load documents
        List<DriverDocument> documents = documentRepository.findByUser_Id(driverId);
        Map<DocumentType, DriverDocument> docMap = documents.stream()
            .collect(Collectors.toMap(DriverDocument::getDocumentType, d -> d));

        // Build status map
        Map<String, String> statuses = new java.util.HashMap<>();
        for (DocumentType type : DocumentType.values()) {
            DriverDocument doc = docMap.get(type);
            statuses.put(type.name(), doc != null ? doc.getStatus().name() : "MISSING");
        }

        long approvedCount = documentRepository.countByUser_IdAndStatus(driverId, DocumentStatus.APPROVED);
        boolean kycComplete = approvedCount == 5;

        return KYCStatusResponse.builder()
            .driverId(driverId)
            .isActive(driver.getIsActive())
            .kycComplete(kycComplete)
            .approvedCount((int) approvedCount)
            .requiredCount(5)
            .documentStatuses(statuses)
            .build();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isKYCComplete(Long driverId) {
        return documentRepository.allDocumentsApproved(driverId);
    }

    // ── Activation ────────────────────────────────────────────────────────────

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void activateDriverIfKYCComplete(Long driverId) {
        boolean kycComplete = documentRepository.allDocumentsApproved(driverId);
        log.info("activateDriverIfKYCComplete: driverId={}, kycComplete={}", driverId, kycComplete);

        if (kycComplete) {
            User driver = userRepository.findById(driverId)
                .orElseThrow(() -> new DocumentException.DocumentNotFoundException("Driver not found: " + driverId));

            if (!Boolean.TRUE.equals(driver.getIsActive()) || !Boolean.TRUE.equals(driver.getVerificationStatus())) {
                driver.setIsActive(true);
                driver.setVerificationStatus(true);
                userRepository.save(driver);
                log.info("Driver {} activated (KYC complete: isActive=true, verificationStatus=true)", driverId);
            }
        }
    }

    /**
     * Deactivate driver if KYC is no longer complete (document rejected or re-submitted).
     */
    private void deactivateDriverIfKYCIncomplete(Long driverId) {
        boolean kycComplete = documentRepository.allDocumentsApproved(driverId);
        log.info("deactivateDriverIfKYCIncomplete: driverId={}, kycComplete={}", driverId, kycComplete);

        if (!kycComplete) {
            User driver = userRepository.findById(driverId)
                .orElseThrow(() -> new DocumentException.DocumentNotFoundException("Driver not found: " + driverId));

            if (Boolean.TRUE.equals(driver.getIsActive()) || Boolean.TRUE.equals(driver.getVerificationStatus())) {
                driver.setIsActive(false);
                driver.setVerificationStatus(false);
                userRepository.save(driver);
                log.warn("Driver {} deactivated (KYC incomplete: isActive=false, verificationStatus=false)", driverId);
            }
        }
    }

    // ── Authorization ─────────────────────────────────────────────────────────

    private void authorizeDocumentAccess(DriverDocument document, User callerUser, String action) {
        if (callerUser.getRole() == User.Role.ADMIN) return;

        boolean isOwner = document.getUser().getId().equals(callerUser.getId());
        if (!isOwner) {
            throw new DocumentException.DocumentAuthorizationException(
                "You are not authorized to " + action + " this document");
        }
    }
}
