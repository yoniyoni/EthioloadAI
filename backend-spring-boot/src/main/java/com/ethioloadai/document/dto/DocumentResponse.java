package com.ethioloadai.document.dto;

import com.ethioloadai.document.entity.DriverDocument;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Read-only document response DTO.
 *
 * <p>SECURITY: file_path is NOT exposed. Clients use the secure fileUrl endpoint
 * to view or download the actual document file.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentResponse {

    private Long          id;
    private Long          userId;
    private String        driverName;
    private String        driverPhone;
    private String        documentType;
    private String        originalName;
    private String        mimeType;
    private Long          fileSize;
    private String        status;
    private String        rejectionReason;
    private Long          reviewedByUserId;
    private LocalDateTime reviewedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String        fileUrl;

    // ── Compatibility aliases for frontend interoperability ───────────────────

    @JsonProperty("user_id")
    public Long getUserIdSnake() {
        return userId;
    }

    @JsonProperty("driver_name")
    public String getDriverNameSnake() {
        return driverName;
    }

    @JsonProperty("driver_phone")
    public String getDriverPhoneSnake() {
        return driverPhone;
    }

    @JsonProperty("document_type")
    public String getDocumentTypeSnake() {
        return documentType != null ? documentType.toLowerCase() : null;
    }

    @JsonProperty("original_name")
    public String getOriginalNameSnake() {
        return originalName;
    }

    @JsonProperty("rejection_reason")
    public String getRejectionReasonSnake() {
        return rejectionReason;
    }

    @JsonProperty("file_url")
    public String getFileUrlSnake() {
        return fileUrl;
    }

    // ── Factory ───────────────────────────────────────────────────────────────

    public static DocumentResponse from(DriverDocument d) {
        String driverName = null;
        String driverPhone = null;
        Long userId = null;

        if (d.getUser() != null) {
            userId = d.getUser().getId();
            driverName = d.getUser().getFullName() != null && !d.getUser().getFullName().isBlank()
                ? d.getUser().getFullName()
                : (d.getUser().getEmail() != null ? d.getUser().getEmail() : d.getUser().getPhone());
            driverPhone = d.getUser().getPhone();
        }

        return DocumentResponse.builder()
            .id(d.getId())
            .userId(userId)
            .driverName(driverName)
            .driverPhone(driverPhone)
            .documentType(d.getDocumentType() != null ? d.getDocumentType().name() : null)
            .originalName(d.getOriginalName())
            .mimeType(d.getMimeType())
            .fileSize(d.getFileSize())
            .status(d.getStatus() != null ? d.getStatus().name() : null)
            .rejectionReason(d.getRejectionReason())
            .reviewedByUserId(d.getReviewedBy() != null ? d.getReviewedBy().getId() : null)
            .reviewedAt(d.getReviewedAt())
            .createdAt(d.getCreatedAt())
            .updatedAt(d.getUpdatedAt())
            .fileUrl("/api/driver/documents/" + d.getId() + "/file")
            .build();
    }
}
