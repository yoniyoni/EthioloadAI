package com.ethioloadai.document;

import com.ethioloadai.document.dto.*;
import com.ethioloadai.document.entity.DriverDocument;
import com.ethioloadai.document.entity.DriverDocument.DocumentStatus;
import com.ethioloadai.document.entity.DriverDocument.DocumentType;
import com.ethioloadai.document.exception.DocumentException;
import com.ethioloadai.document.repository.DriverDocumentRepository;
import com.ethioloadai.document.service.DriverDocumentService;
import com.ethioloadai.document.service.DriverDocumentServiceImpl;
import com.ethioloadai.document.storage.DocumentStorage;
import com.ethioloadai.user.entity.User;
import com.ethioloadai.user.entity.User.Role;
import com.ethioloadai.user.repository.UserRepository;
import com.ethioloadai.websocket.notification.NotificationService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Phase 12 — comprehensive service tests for driver KYC workflow.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DriverDocumentServiceTest {

    @Mock private DriverDocumentRepository documentRepository;
    @Mock private UserRepository userRepository;
    @Mock private EntityManager entityManager;
    @Mock private NotificationService notificationService;
    @Mock private DocumentStorage documentStorage;

    private DriverDocumentServiceImpl service;

    private User driver, admin, shipper;
    private DriverDocument pendingLicense;

    @org.junit.jupiter.api.AfterEach
    void clearTransactionSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @BeforeEach
    void setUp() {
        service = new DriverDocumentServiceImpl(
            documentRepository, userRepository, entityManager, notificationService, documentStorage);

        driver = buildUser(1L, Role.DRIVER, true);
        admin = buildUser(2L, Role.ADMIN, true);
        shipper = buildUser(3L, Role.SHIPPER, true);

        pendingLicense = DriverDocument.builder()
            .id(100L)
            .user(driver)
            .documentType(DocumentType.LICENSE)
            .filePath("driver-documents/1/license/license_123.pdf")
            .originalName("license.pdf")
            .status(DocumentStatus.PENDING)
            .build();

        when(userRepository.findById(driver.getId())).thenReturn(Optional.of(driver));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(documentRepository.save(any())).thenAnswer(inv -> {
            DriverDocument d = inv.getArgument(0);
            if (d.getId() == null) d.setId(100L);
            return d;
        });
    }

    // ── Submit: basic ─────────────────────────────────────────────────────────

    @Test
    void submitDocument_creates_pending_record() {
        when(documentRepository.findByUser_IdAndDocumentType(driver.getId(), DocumentType.LICENSE))
            .thenReturn(Optional.empty());

        DocumentSubmissionRequest req = DocumentSubmissionRequest.builder()
            .documentType(DocumentType.LICENSE)
            .filePath("driver-documents/1/license/license.pdf")
            .originalName("license.pdf")
            .build();

        DocumentResponse resp = service.submitDocument(req, driver);

        assertThat(resp.getStatus()).isEqualTo("PENDING");
        assertThat(resp.getDocumentType()).isEqualTo("LICENSE");
        verify(documentRepository).save(any(DriverDocument.class));
    }

    @Test
    void submitDocument_non_driver_rejected() {
        DocumentSubmissionRequest req = DocumentSubmissionRequest.builder()
            .documentType(DocumentType.LICENSE)
            .filePath("driver-documents/1/license/license.pdf")
            .originalName("license.pdf")
            .build();

        assertThatThrownBy(() -> service.submitDocument(req, shipper))
            .isInstanceOf(DocumentException.DocumentAuthorizationException.class)
            .hasMessageContaining("Only drivers");
    }

    // ── Submit: re-submission ─────────────────────────────────────────────────

    @Test
    void submitDocument_replaces_rejected_document() {
        DriverDocument rejected = DriverDocument.builder()
            .id(99L)
            .user(driver)
            .documentType(DocumentType.LICENSE)
            .status(DocumentStatus.REJECTED)
            .rejectionReason("Expired")
            .build();

        when(documentRepository.findByUser_IdAndDocumentType(driver.getId(), DocumentType.LICENSE))
            .thenReturn(Optional.of(rejected));

        DocumentSubmissionRequest req = DocumentSubmissionRequest.builder()
            .documentType(DocumentType.LICENSE)
            .filePath("driver-documents/1/license/new_license.pdf")
            .originalName("new_license.pdf")
            .build();

        DocumentResponse resp = service.submitDocument(req, driver);

        assertThat(resp.getStatus()).isEqualTo("PENDING");
        assertThat(resp.getId()).isEqualTo(99L); // Same document ID, updated
        verify(documentRepository).save(argThat(d ->
            d.getStatus() == DocumentStatus.PENDING &&
            d.getRejectionReason() == null &&
            d.getReviewedBy() == null
        ));
    }

    @Test
    void uploadReplacementDeletesPreviousObjectOnlyAfterTransactionCommit() throws IOException {
        DriverDocument rejected = DriverDocument.builder()
            .id(99L)
            .user(driver)
            .documentType(DocumentType.LICENSE)
            .filePath("driver-documents/1/license/previous.pdf")
            .status(DocumentStatus.REJECTED)
            .build();
        when(documentRepository.findByUser_IdAndDocumentType(driver.getId(), DocumentType.LICENSE))
            .thenReturn(Optional.of(rejected));
        TransactionSynchronizationManager.initSynchronization();

        service.uploadDocument(DocumentType.LICENSE, validPdf(), driver);

        String newObjectKey = capturedStoredObjectKey();
        verify(documentStorage, never()).delete(anyString());
        List<TransactionSynchronization> synchronizations =
            new ArrayList<>(TransactionSynchronizationManager.getSynchronizations());
        synchronizations.forEach(sync -> sync.afterCompletion(TransactionSynchronization.STATUS_COMMITTED));

        verify(documentStorage).delete("driver-documents/1/license/previous.pdf");
        verify(documentStorage, never()).delete(newObjectKey);
    }

    @Test
    void uploadReplacementRollbackDeletesNewObjectAndPreservesPreviousObject() throws IOException {
        DriverDocument rejected = DriverDocument.builder()
            .id(99L)
            .user(driver)
            .documentType(DocumentType.LICENSE)
            .filePath("driver-documents/1/license/previous.pdf")
            .status(DocumentStatus.REJECTED)
            .build();
        when(documentRepository.findByUser_IdAndDocumentType(driver.getId(), DocumentType.LICENSE))
            .thenReturn(Optional.of(rejected));
        TransactionSynchronizationManager.initSynchronization();

        doThrow(new IllegalStateException("notification unavailable"))
            .when(notificationService).notifyRole(eq("admin"), any());
        assertThatThrownBy(() -> service.uploadDocument(DocumentType.LICENSE, validPdf(), driver))
            .isInstanceOf(DocumentException.DocumentValidationException.class);

        String newObjectKey = capturedStoredObjectKey();
        verify(documentStorage, never()).delete(anyString());
        List<TransactionSynchronization> synchronizations =
            new ArrayList<>(TransactionSynchronizationManager.getSynchronizations());
        synchronizations.forEach(sync -> sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

        verify(documentStorage).delete(newObjectKey);
        verify(documentStorage, never()).delete("driver-documents/1/license/previous.pdf");
    }

    private MockMultipartFile validPdf() {
        return new MockMultipartFile(
            "file", "license.pdf", "application/pdf", "%PDF-1.7 test".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
    }

    private String capturedStoredObjectKey() throws IOException {
        var key = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(documentStorage).put(key.capture(), any());
        return key.getValue();
    }

    @Test
    void submitDocument_replaces_approved_document_via_rejection_first() {
        // Re-submitting an APPROVED document should first transition to REJECTED
        // (admin revokes approval), then to PENDING (new submission).
        // The service should handle this by transitioning APPROVED → REJECTED → PENDING
        // OR simply accepting the re-submission as a business rule override.
        
        // For M8A, we accept that re-submission of APPROVED docs is NOT allowed
        // without explicit rejection first. Document this as a known limitation.
        
        DriverDocument approved = DriverDocument.builder()
            .id(99L)
            .user(driver)
            .documentType(DocumentType.LICENSE)
            .status(DocumentStatus.APPROVED)
            .build();

        when(documentRepository.findByUser_IdAndDocumentType(driver.getId(), DocumentType.LICENSE))
            .thenReturn(Optional.of(approved));

        DocumentSubmissionRequest req = DocumentSubmissionRequest.builder()
            .documentType(DocumentType.LICENSE)
            .filePath("driver-documents/1/license/new_license.pdf")
            .originalName("new_license.pdf")
            .build();

        // Should throw InvalidStatusTransitionException
        assertThatThrownBy(() -> service.submitDocument(req, driver))
            .isInstanceOf(DocumentException.InvalidStatusTransitionException.class)
            .hasMessageContaining("APPROVED")
            .hasMessageContaining("PENDING");
    }

    // ── Review: approval ──────────────────────────────────────────────────────

    @Test
    void reviewDocument_admin_can_approve() {
        when(documentRepository.findById(100L)).thenReturn(Optional.of(pendingLicense));
        when(documentRepository.allDocumentsApproved(driver.getId())).thenReturn(false);

        DocumentReviewRequest req = DocumentReviewRequest.builder()
            .status(DocumentStatus.APPROVED)
            .build();

        DocumentResponse resp = service.reviewDocument(100L, req, admin);

        assertThat(resp.getStatus()).isEqualTo("APPROVED");
        verify(documentRepository).save(argThat(d ->
            d.getStatus() == DocumentStatus.APPROVED &&
            d.getReviewedBy().equals(admin) &&
            d.getReviewedAt() != null
        ));
    }

    @Test
    void reviewDocument_non_admin_rejected() {
        when(documentRepository.findById(100L)).thenReturn(Optional.of(pendingLicense));

        DocumentReviewRequest req = DocumentReviewRequest.builder()
            .status(DocumentStatus.APPROVED)
            .build();

        assertThatThrownBy(() -> service.reviewDocument(100L, req, driver))
            .isInstanceOf(DocumentException.DocumentAuthorizationException.class)
            .hasMessageContaining("administrators");
    }

    @Test
    void reviewDocument_rejection_requires_reason() {
        when(documentRepository.findById(100L)).thenReturn(Optional.of(pendingLicense));

        DocumentReviewRequest req = DocumentReviewRequest.builder()
            .status(DocumentStatus.REJECTED)
            .rejectionReason(null)
            .build();

        assertThatThrownBy(() -> service.reviewDocument(100L, req, admin))
            .isInstanceOf(DocumentException.DocumentValidationException.class)
            .hasMessageContaining("Rejection reason is required");
    }

    @Test
    void reviewDocument_rejection_stores_reason() {
        when(documentRepository.findById(100L)).thenReturn(Optional.of(pendingLicense));
        when(documentRepository.allDocumentsApproved(driver.getId())).thenReturn(false);

        DocumentReviewRequest req = DocumentReviewRequest.builder()
            .status(DocumentStatus.REJECTED)
            .rejectionReason("Document is expired")
            .build();

        DocumentResponse resp = service.reviewDocument(100L, req, admin);

        assertThat(resp.getStatus()).isEqualTo("REJECTED");
        assertThat(resp.getRejectionReason()).isEqualTo("Document is expired");
    }

    // ── Driver activation ─────────────────────────────────────────────────────

    @Test
    void reviewDocument_activates_driver_when_all_5_approved() {
        when(documentRepository.findById(100L)).thenReturn(Optional.of(pendingLicense));
        // This is the 5th document being approved
        when(documentRepository.allDocumentsApproved(driver.getId())).thenReturn(true);

        driver.setIsActive(false); // Driver was inactive

        DocumentReviewRequest req = DocumentReviewRequest.builder()
            .status(DocumentStatus.APPROVED)
            .build();

        service.reviewDocument(100L, req, admin);

        // Driver should be activated
        verify(userRepository).save(argThat(u -> u.getIsActive()));
    }

    @Test
    void reviewDocument_does_not_activate_if_incomplete() {
        when(documentRepository.findById(100L)).thenReturn(Optional.of(pendingLicense));
        // Only 4/5 approved
        when(documentRepository.allDocumentsApproved(driver.getId())).thenReturn(false);

        driver.setIsActive(false);

        DocumentReviewRequest req = DocumentReviewRequest.builder()
            .status(DocumentStatus.APPROVED)
            .build();

        service.reviewDocument(100L, req, admin);

        // Driver should remain inactive
        verify(userRepository, never()).save(argThat(u -> u.getIsActive()));
    }

    @Test
    void reviewDocument_deactivates_driver_on_rejection() {
        DriverDocument approved = DriverDocument.builder()
            .id(100L)
            .user(driver)
            .documentType(DocumentType.LICENSE)
            .status(DocumentStatus.APPROVED)
            .build();

        when(documentRepository.findById(100L)).thenReturn(Optional.of(approved));
        when(documentRepository.allDocumentsApproved(driver.getId())).thenReturn(false);

        driver.setIsActive(true); // Driver was active

        DocumentReviewRequest req = DocumentReviewRequest.builder()
            .status(DocumentStatus.REJECTED)
            .rejectionReason("Fraud detected")
            .build();

        service.reviewDocument(100L, req, admin);

        // Driver should be deactivated
        verify(userRepository).save(argThat(u -> !u.getIsActive()));
    }

    // ── IDOR protection ───────────────────────────────────────────────────────

    @Test
    void getDocumentById_rejects_other_driver() {
        when(documentRepository.findById(100L)).thenReturn(Optional.of(pendingLicense));
        User otherDriver = buildUser(99L, Role.DRIVER, true);

        assertThatThrownBy(() -> service.getDocumentById(100L, otherDriver))
            .isInstanceOf(DocumentException.DocumentAuthorizationException.class);
    }

    @Test
    void getDocumentById_allows_owner() {
        when(documentRepository.findById(100L)).thenReturn(Optional.of(pendingLicense));

        assertThatNoException().isThrownBy(() -> service.getDocumentById(100L, driver));
    }

    @Test
    void getDocumentById_allows_admin() {
        when(documentRepository.findById(100L)).thenReturn(Optional.of(pendingLicense));

        assertThatNoException().isThrownBy(() -> service.getDocumentById(100L, admin));
    }

    @Test
    void getDocumentsForDriver_rejects_other_driver() {
        User otherDriver = buildUser(99L, Role.DRIVER, true);

        assertThatThrownBy(() -> service.getDocumentsForDriver(driver.getId(), otherDriver))
            .isInstanceOf(DocumentException.DocumentAuthorizationException.class);
    }

    @Test
    void getDocumentsForDriver_allows_self() {
        when(documentRepository.findByUser_Id(driver.getId()))
            .thenReturn(List.of(pendingLicense));

        assertThatNoException().isThrownBy(() ->
            service.getDocumentsForDriver(driver.getId(), driver));
    }

    // ── KYC status ────────────────────────────────────────────────────────────

    @Test
    void getKYCStatus_shows_missing_documents() {
        when(documentRepository.findByUser_Id(driver.getId()))
            .thenReturn(List.of(pendingLicense)); // Only LICENSE submitted
        when(documentRepository.countByUser_IdAndStatus(driver.getId(), DocumentStatus.APPROVED))
            .thenReturn(0L);
        when(documentRepository.allDocumentsApproved(driver.getId())).thenReturn(false);

        KYCStatusResponse resp = service.getKYCStatus(driver.getId(), driver);

        assertThat(resp.isKycComplete()).isFalse();
        assertThat(resp.getApprovedCount()).isEqualTo(0);
        assertThat(resp.getRequiredCount()).isEqualTo(5);
        assertThat(resp.getDocumentStatuses()).containsEntry("LICENSE", "PENDING");
        assertThat(resp.getDocumentStatuses()).containsEntry("NATIONAL_ID", "MISSING");
        assertThat(resp.getDocumentStatuses()).containsEntry("VEHICLE_REGISTRATION", "MISSING");
        assertThat(resp.getDocumentStatuses()).containsEntry("INSURANCE", "MISSING");
        assertThat(resp.getDocumentStatuses()).containsEntry("TIN", "MISSING");
    }

    @Test
    void getKYCStatus_shows_complete_when_all_approved() {
        // All 5 documents approved
        List<DriverDocument> allDocs = List.of(
            buildDoc(DocumentType.LICENSE, DocumentStatus.APPROVED),
            buildDoc(DocumentType.NATIONAL_ID, DocumentStatus.APPROVED),
            buildDoc(DocumentType.VEHICLE_REGISTRATION, DocumentStatus.APPROVED),
            buildDoc(DocumentType.INSURANCE, DocumentStatus.APPROVED),
            buildDoc(DocumentType.TIN, DocumentStatus.APPROVED)
        );

        when(documentRepository.findByUser_Id(driver.getId())).thenReturn(allDocs);
        when(documentRepository.countByUser_IdAndStatus(driver.getId(), DocumentStatus.APPROVED))
            .thenReturn(5L);
        when(documentRepository.allDocumentsApproved(driver.getId())).thenReturn(true);

        driver.setIsActive(true);

        KYCStatusResponse resp = service.getKYCStatus(driver.getId(), driver);

        assertThat(resp.isKycComplete()).isTrue();
        assertThat(resp.getApprovedCount()).isEqualTo(5);
        assertThat(resp.isActive()).isTrue();
    }

    // ── Milestone B14: getAllDocuments ────────────────────────────────────────

    @Test
    void getAllDocuments_admin_returns_all_documents() {
        List<DriverDocument> allDocs = List.of(
            buildDoc(DocumentType.LICENSE, DocumentStatus.PENDING),
            buildDoc(DocumentType.NATIONAL_ID, DocumentStatus.APPROVED)
        );
        when(documentRepository.findAllByOrderByCreatedAtDesc()).thenReturn(allDocs);

        List<DocumentResponse> result = service.getAllDocuments(admin, null);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getDriverName()).isEqualTo("User-1");
        assertThat(result.get(0).getFileUrl()).contains("/api/driver/documents/");
        verify(documentRepository).findAllByOrderByCreatedAtDesc();
    }

    @Test
    void getAllDocuments_filtered_by_status() {
        List<DriverDocument> pendingDocs = List.of(
            buildDoc(DocumentType.LICENSE, DocumentStatus.PENDING)
        );
        when(documentRepository.findByStatusOrderByCreatedAtDesc(DocumentStatus.PENDING)).thenReturn(pendingDocs);

        List<DocumentResponse> result = service.getAllDocuments(admin, DocumentStatus.PENDING);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getStatus()).isEqualTo("PENDING");
        verify(documentRepository).findByStatusOrderByCreatedAtDesc(DocumentStatus.PENDING);
    }

    @Test
    void getAllDocuments_non_admin_forbidden() {
        assertThatThrownBy(() -> service.getAllDocuments(driver, null))
            .isInstanceOf(DocumentException.DocumentAuthorizationException.class)
            .hasMessageContaining("Only administrators");
    }

    // ── Milestone B14: getDocumentFile ────────────────────────────────────────

    @Test
    void getDocumentFile_owner_can_access() throws Exception {
        DriverDocument doc = DriverDocument.builder()
            .id(500L)
            .user(driver)
            .documentType(DocumentType.LICENSE)
            .status(DocumentStatus.PENDING)
            .filePath("driver-documents/1/license/license.pdf")
            .originalName("my_license.pdf")
            .build();

        when(documentRepository.findById(500L)).thenReturn(Optional.of(doc));
        when(documentStorage.get(eq("driver-documents/1/license/license.pdf"), eq("my_license.pdf"), anyString()))
            .thenReturn(new DocumentStorage.StoredDocument(
                new ByteArrayResource("%PDF-1.4 test document content".getBytes()),
                "my_license.pdf", "application/pdf"));

        DriverDocumentService.DocumentFile file = service.getDocumentFile(500L, driver);

        assertThat(file).isNotNull();
        assertThat(file.filename()).isEqualTo("my_license.pdf");
        assertThat(file.contentType()).isEqualTo("application/pdf");
        assertThat(file.resource().exists()).isTrue();
    }

    @Test
    void getDocumentFile_admin_can_access_any_document() throws Exception {
        DriverDocument doc = DriverDocument.builder()
            .id(501L)
            .user(driver)
            .documentType(DocumentType.LICENSE)
            .status(DocumentStatus.APPROVED)
            .filePath("driver-documents/1/license/approved-license.pdf")
            .originalName("driver_license.pdf")
            .build();

        when(documentRepository.findById(501L)).thenReturn(Optional.of(doc));
        when(documentStorage.get(eq("driver-documents/1/license/approved-license.pdf"), eq("driver_license.pdf"), anyString()))
            .thenReturn(new DocumentStorage.StoredDocument(
                new ByteArrayResource("%PDF-1.4 test document content".getBytes()),
                "driver_license.pdf", "application/pdf"));

        DriverDocumentService.DocumentFile file = service.getDocumentFile(501L, admin);

        assertThat(file).isNotNull();
        assertThat(file.filename()).isEqualTo("driver_license.pdf");
    }

    @Test
    void getDocumentFile_other_driver_forbidden() {
        User otherDriver = buildUser(99L, Role.DRIVER, true);
        DriverDocument doc = DriverDocument.builder()
            .id(502L)
            .user(driver)
            .documentType(DocumentType.LICENSE)
            .status(DocumentStatus.PENDING)
            .filePath("/some/path.pdf")
            .originalName("license.pdf")
            .build();

        when(documentRepository.findById(502L)).thenReturn(Optional.of(doc));

        assertThatThrownBy(() -> service.getDocumentFile(502L, otherDriver))
            .isInstanceOf(DocumentException.DocumentAuthorizationException.class)
            .hasMessageContaining("not authorized");
    }

    @Test
    void getDocumentFile_path_traversal_rejected() {
        DriverDocument doc = DriverDocument.builder()
            .id(503L)
            .user(driver)
            .documentType(DocumentType.LICENSE)
            .status(DocumentStatus.PENDING)
            .filePath("../../etc/passwd")
            .originalName("passwd")
            .build();

        when(documentRepository.findById(503L)).thenReturn(Optional.of(doc));

        assertThatThrownBy(() -> service.getDocumentFile(503L, admin))
            .isInstanceOf(DocumentException.DocumentValidationException.class)
            .hasMessageContaining("path traversal");
    }

    @Test
    void getDocumentFile_missing_on_disk_throws_not_found() {
        DriverDocument doc = DriverDocument.builder()
            .id(504L)
            .user(driver)
            .documentType(DocumentType.LICENSE)
            .status(DocumentStatus.PENDING)
            .filePath("driver-documents/1/license/non_existent_file_abc123.pdf")
            .originalName("license.pdf")
            .build();

        when(documentRepository.findById(504L)).thenReturn(Optional.of(doc));
        when(documentStorage.get(eq("driver-documents/1/license/non_existent_file_abc123.pdf"), eq("license.pdf"), anyString()))
            .thenThrow(new IllegalStateException("Document object not found"));

        assertThatThrownBy(() -> service.getDocumentFile(504L, admin))
            .isInstanceOf(DocumentException.DocumentNotFoundException.class)
            .hasMessageContaining("not found on storage");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private User buildUser(Long id, Role role, boolean active) {
        User u = new User();
        u.setId(id);
        u.setRole(role);
        u.setFullName("User-" + id);
        u.setPhone("09000000" + id);
        u.setIsActive(active);
        return u;
    }

    private DriverDocument buildDoc(DocumentType type, DocumentStatus status) {
        return DriverDocument.builder()
            .user(driver)
            .documentType(type)
            .status(status)
            .filePath("driver-documents/1/" + type.name().toLowerCase() + "/test.pdf")
            .originalName(type.name() + ".pdf")
            .build();
    }
}
