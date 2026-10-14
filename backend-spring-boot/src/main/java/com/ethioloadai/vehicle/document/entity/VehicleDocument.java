package com.ethioloadai.vehicle.document.entity;

import com.ethioloadai.user.entity.User;
import com.ethioloadai.vehicle.document.converter.VehicleDocumentStatusConverter;
import com.ethioloadai.vehicle.document.converter.VehicleDocumentTypeConverter;
import com.ethioloadai.vehicle.entity.Vehicle;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(
    name = "vehicle_documents",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_vehicle_document_type",
        columnNames = {"vehicle_id", "document_type"}
    )
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VehicleDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vehicle_id", nullable = false)
    private Vehicle vehicle;

    @Convert(converter = VehicleDocumentTypeConverter.class)
    @Column(name = "document_type", nullable = false, length = 50)
    private DocumentType documentType;

    @Column(name = "document_label", nullable = false, length = 100)
    private String documentLabel;

    @Column(name = "file_path", nullable = false, length = 500)
    private String filePath;

    @Column(name = "original_name", nullable = false, length = 255)
    private String originalName;

    @Column(name = "mime_type", nullable = false, length = 100)
    private String mimeType;

    @Column(name = "file_size", nullable = false)
    private Long fileSize;

    @Convert(converter = VehicleDocumentStatusConverter.class)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private DocumentStatus status = DocumentStatus.PENDING;

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

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

    public enum DocumentType {
        REGISTRATION_CERTIFICATE,
        INSURANCE_CERTIFICATE,
        ROADWORTHINESS_CERTIFICATE,
        OWNERSHIP_DOCUMENT;

        public String getLabel() {
            return switch (this) {
                case REGISTRATION_CERTIFICATE -> "Registration Certificate";
                case INSURANCE_CERTIFICATE -> "Insurance Certificate";
                case ROADWORTHINESS_CERTIFICATE -> "Roadworthiness Certificate";
                case OWNERSHIP_DOCUMENT -> "Ownership Document";
            };
        }
    }

    public enum DocumentStatus {
        PENDING,
        APPROVED,
        REJECTED
    }
}
