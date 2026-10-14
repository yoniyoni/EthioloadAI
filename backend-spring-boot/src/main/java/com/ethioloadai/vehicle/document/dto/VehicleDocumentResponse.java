package com.ethioloadai.vehicle.document.dto;

import com.ethioloadai.vehicle.document.entity.VehicleDocument;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Locale;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VehicleDocumentResponse {
    private Long id;
    private Long vehicleId;
    private Long userId;
    private String vehiclePlateNumber;
    private String documentType;
    private String documentLabel;
    private String originalName;
    private String mimeType;
    private Long fileSize;
    private String status;
    private String rejectionReason;
    private Long reviewedByUserId;
    private LocalDateTime reviewedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String fileUrl;

    @JsonProperty("vehicle_id")
    public Long getVehicleIdSnake() { return vehicleId; }

    @JsonProperty("user_id")
    public Long getUserIdSnake() { return userId; }

    @JsonProperty("vehicle_plate_number")
    public String getVehiclePlateNumberSnake() { return vehiclePlateNumber; }

    @JsonProperty("document_type")
    public String getDocumentTypeSnake() { return documentType; }

    @JsonProperty("document_label")
    public String getDocumentLabelSnake() { return documentLabel; }

    @JsonProperty("original_name")
    public String getOriginalNameSnake() { return originalName; }

    @JsonProperty("rejection_reason")
    public String getRejectionReasonSnake() { return rejectionReason; }

    @JsonProperty("reviewed_by_user_id")
    public Long getReviewedByUserIdSnake() { return reviewedByUserId; }

    @JsonProperty("file_url")
    public String getFileUrlSnake() { return fileUrl; }

    public static VehicleDocumentResponse from(VehicleDocument doc) {
        if (doc == null) {
            return null;
        }
        Long vehicleId = doc.getVehicle() != null ? doc.getVehicle().getId() : null;
        Long userId = doc.getVehicle() != null ? doc.getVehicle().getUserId() : null;
        String plateNumber = doc.getVehicle() != null ? doc.getVehicle().getPlateNumber() : null;
        return VehicleDocumentResponse.builder()
            .id(doc.getId())
            .vehicleId(vehicleId)
            .userId(userId)
            .vehiclePlateNumber(plateNumber)
            .documentType(serialize(doc.getDocumentType()))
            .documentLabel(doc.getDocumentLabel())
            .originalName(doc.getOriginalName())
            .mimeType(doc.getMimeType())
            .fileSize(doc.getFileSize())
            .status(serialize(doc.getStatus()))
            .rejectionReason(doc.getRejectionReason())
            .reviewedByUserId(doc.getReviewedBy() == null ? null : doc.getReviewedBy().getId())
            .reviewedAt(doc.getReviewedAt())
            .createdAt(doc.getCreatedAt())
            .updatedAt(doc.getUpdatedAt())
            .fileUrl("/api/vehicle-documents/" + doc.getId() + "/file")
            .build();
    }

    private static String serialize(Enum<?> value) {
        return value == null ? null : value.name().toLowerCase(Locale.ROOT);
    }
}
