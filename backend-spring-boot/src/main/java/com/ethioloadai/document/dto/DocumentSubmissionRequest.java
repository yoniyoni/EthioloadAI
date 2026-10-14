package com.ethioloadai.document.dto;

import com.ethioloadai.document.entity.DriverDocument;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Client-supplied fields for document submission/resubmission.
 *
 * <p>SECURITY: status, reviewedBy, reviewedAt, and rejectionReason are
 * intentionally absent — those are server-assigned during admin review.
 *
 * <p>NOTE: This DTO does NOT include binary file data. In the current
 * architecture, file uploads are handled separately (e.g. multipart/form-data),
 * and this DTO receives only the server-assigned file path after successful upload.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentSubmissionRequest {

    @NotNull(message = "documentType is required")
    private DriverDocument.DocumentType documentType;

    /**
     * Server-assigned file path after upload.
     * The client does NOT control this — it is returned from the file-upload
     * endpoint and then passed here for record creation.
     */
    @NotNull(message = "filePath is required")
    private String filePath;

    /**
     * Original filename provided by the client (display only).
     */
    @NotNull(message = "originalName is required")
    private String originalName;

    private String mimeType;

    private Long fileSize;
}
