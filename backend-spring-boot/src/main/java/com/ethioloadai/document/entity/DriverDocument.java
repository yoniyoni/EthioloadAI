package com.ethioloadai.document.entity;

import com.ethioloadai.document.converter.DocumentStatusConverter;
import com.ethioloadai.document.converter.DocumentTypeConverter;
import com.ethioloadai.user.entity.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * A KYC document uploaded by a driver for verification.
 *
 * <p>Required document types (all 5 must be approved before a driver is activated):
 * <ul>
 *   <li>LICENSE              — Driver's driving license</li>
 *   <li>NATIONAL_ID          — National ID or Kebele ID</li>
 *   <li>VEHICLE_REGISTRATION — Vehicle registration certificate</li>
 *   <li>INSURANCE            — Insurance certificate</li>
 *   <li>TIN                  — Tax Identification Number</li>
 * </ul>
 *
 * <p>Unique constraint on (user_id, document_type): re-uploading replaces the
 * existing record for that type. The service layer handles the upsert logic.
 *
 * <p>SECURITY: file_path must never be returned directly in API responses.
 * Use a signed URL or a download endpoint with authorization checks.
 */
@Entity
@Table(name = "driver_documents")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DriverDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Convert(converter = DocumentTypeConverter.class)
    @Column(name = "document_type", nullable = false, length = 50)
    private DocumentType documentType;

    /**
     * Human-readable label for the document type — derived from DocumentType.getLabel().
     * This column exists in the database (NOT NULL). The entity sets it automatically
     * via the builder in DriverDocumentServiceImpl so callers never need to set it manually.
     */
    @Column(name = "document_label", nullable = false, length = 100)
    private String documentLabel;

    /**
     * Legacy public file URL column (NOT NULL in DB, retained for schema compatibility).
     * The actual secure download path is constructed at response time in DocumentResponse.
     * Set to the object key during upload; never returned directly to clients.
     */
    @Column(name = "file_url", nullable = false, length = 500)
    private String fileUrl;

    /** Server-side storage path. NEVER expose in API responses. */
    @Column(name = "file_path", nullable = false, length = 500)
    private String filePath;

    /** Original filename provided by the driver (display only). */
    @Column(name = "original_name", nullable = false, length = 255)
    private String originalName;

    @Column(name = "mime_type", nullable = false, length = 100)
    private String mimeType;

    @Column(name = "file_size", nullable = false)
    private Long fileSize;

    @Convert(converter = DocumentStatusConverter.class)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private DocumentStatus status = DocumentStatus.PENDING;

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    /** Admin who reviewed this document. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by")
    private User reviewedBy;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // ── Enums ─────────────────────────────────────────────────────────────────

    public enum DocumentType {
        LICENSE,
        NATIONAL_ID,
        VEHICLE_REGISTRATION,
        INSURANCE,
        TIN;

        /** Human-readable label for display in admin UI. */
        public String getLabel() {
            return switch (this) {
                case LICENSE              -> "Driver's License";
                case NATIONAL_ID          -> "National ID / Kebele ID";
                case VEHICLE_REGISTRATION -> "Vehicle Registration";
                case INSURANCE            -> "Insurance Certificate";
                case TIN                  -> "TIN Certificate";
            };
        }
    }

    public enum DocumentStatus {
        PENDING,
        APPROVED,
        REJECTED
    }
}
