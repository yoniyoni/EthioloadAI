package com.ethioloadai.delivery.dto;

import com.ethioloadai.delivery.entity.ProofOfDelivery;

import java.time.LocalDateTime;

/**
 * API response for a ProofOfDelivery.
 *
 * <p>SECURITY: {@code storageKey} is intentionally excluded — only the
 * {@code downloadUrl} (served via a secure endpoint) is exposed.
 */
public record ProofOfDeliveryResponse(
        Long id,
        Long tripId,
        Long bookingId,
        Long freightId,
        Long uploadedById,
        String fileName,
        String contentType,
        Long fileSize,
        String recipientName,
        String note,
        String status,
        Long reviewedById,
        LocalDateTime reviewedAt,
        String rejectionReason,
        LocalDateTime uploadedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        /** Secure download URL — caller must be authorized. */
        String downloadUrl
) {
    public static ProofOfDeliveryResponse from(ProofOfDelivery p) {
        return from(p, null);
    }

    public static ProofOfDeliveryResponse from(ProofOfDelivery p, String downloadUrl) {
        return new ProofOfDeliveryResponse(
                p.getId(),
                p.getTrip().getId(),
                p.getBooking().getId(),
                p.getFreight().getId(),
                p.getUploadedBy().getId(),
                p.getFileName(),
                p.getContentType(),
                p.getFileSize(),
                p.getRecipientName(),
                p.getNote(),
                p.getStatus().name(),
                p.getReviewedBy() != null ? p.getReviewedBy().getId() : null,
                p.getReviewedAt(),
                p.getRejectionReason(),
                p.getUploadedAt(),
                p.getCreatedAt(),
                p.getUpdatedAt(),
                downloadUrl
        );
    }
}
