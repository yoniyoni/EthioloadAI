package com.ethioloadai.document.service;

import com.ethioloadai.document.dto.*;
import com.ethioloadai.document.entity.DriverDocument;
import com.ethioloadai.user.entity.User;

import org.springframework.core.io.Resource;

import java.util.List;
import org.springframework.web.multipart.MultipartFile;

/**
 * Domain service for the driver KYC document verification workflow.
 */
public interface DriverDocumentService {

    /**
     * Represents a securely retrieved document file for streaming/download.
     */
    record DocumentFile(Resource resource, String filename, String contentType) {}

    /**
     * Submit a new document or replace an existing one for the same type.
     *
     * <p>Creates a PENDING record. If a document of the same type already exists
     * for this driver, it is replaced (upsert behavior via UNIQUE constraint).
     *
     * @param request    document metadata after file upload
     * @param driverUser authenticated driver submitting the document
     * @return created/updated document response
     */
    DocumentResponse submitDocument(DocumentSubmissionRequest request, User driverUser);

    DocumentResponse uploadDocument(DriverDocument.DocumentType documentType, MultipartFile file, User driverUser);

    /**
     * Admin reviews a document: approve or reject.
     *
     * <p>Sets status, reviewer, reviewedAt, and optionally rejectionReason.
     * If this approval completes the driver's KYC (all 5 approved), the driver
     * is activated automatically.
     *
     * @param documentId ID of the document to review
     * @param request    review decision (APPROVED or REJECTED + reason)
     * @param adminUser  authenticated admin performing the review
     * @return updated document response
     */
    DocumentResponse reviewDocument(Long documentId, DocumentReviewRequest request, User adminUser);

    /**
     * Get a single document by ID with IDOR protection.
     *
     * @param documentId document ID
     * @param callerUser authenticated user (driver owner or admin)
     * @return document response
     */
    DocumentResponse getDocumentById(Long documentId, User callerUser);

    /**
     * Get all documents for a driver with IDOR protection.
     *
     * @param driverId   driver user ID
     * @param callerUser authenticated user (driver themselves or admin)
     * @return list of documents
     */
    List<DocumentResponse> getDocumentsForDriver(Long driverId, User callerUser);

    /**
     * Get all pending documents (admin review queue).
     *
     * @param adminUser authenticated admin
     * @return list of pending documents
     */
    List<DocumentResponse> getPendingDocuments(User adminUser);

    /**
     * Get all documents across drivers, optionally filtered by status (admin review queue).
     *
     * @param adminUser authenticated admin
     * @param status    optional status filter (null for all statuses)
     * @return list of documents
     */
    List<DocumentResponse> getAllDocuments(User adminUser, DriverDocument.DocumentStatus status);

    /**
     * Retrieve the document file for secure streaming/viewing.
     *
     * @param documentId document ID
     * @param callerUser authenticated user (driver owner or admin)
     * @return DocumentFile containing resource, filename, and content type
     */
    DocumentFile getDocumentFile(Long documentId, User callerUser);

    /**
     * Get KYC completeness status for a driver.
     *
     * @param driverId   driver user ID
     * @param callerUser authenticated user (driver themselves or admin)
     * @return KYC status summary
     */
    KYCStatusResponse getKYCStatus(Long driverId, User callerUser);

    /**
     * Bulk-approve all pending/uploaded documents for a driver as a single KYC decision.
     *
     * <p>This is the primary admin workflow: the admin reviews the driver's submission
     * as a whole and approves or rejects the driver in one action. All documents that
     * are in PENDING state are set to APPROVED. Documents already APPROVED are left
     * unchanged. If this completes KYC (all 5 approved) the driver is activated.
     *
     * @param driverId  driver user ID
     * @param adminUser authenticated admin
     */
    void approveAllDocuments(Long driverId, User adminUser);

    /**
     * Check if a driver has completed KYC (all 5 documents approved).
     *
     * @param driverId driver user ID
     * @return true if all 5 documents are approved
     */
    boolean isKYCComplete(Long driverId);

    /**
     * Activate a driver if KYC is complete (all 5 documents approved).
     * This is called internally after document approval.
     *
     * @param driverId driver user ID
     */
    void activateDriverIfKYCComplete(Long driverId);
}
