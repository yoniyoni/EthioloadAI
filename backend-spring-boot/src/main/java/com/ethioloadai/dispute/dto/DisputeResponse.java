package com.ethioloadai.dispute.dto;

import com.ethioloadai.dispute.entity.Dispute;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record DisputeResponse(
        Long id,
        Long freightId,
        Long bookingId,
        Long reportedById,
        String reason,
        String description,
        String status,
        String resolution,
        String adminNotes,
        BigDecimal refundAmount,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime resolvedAt
) {
    public static DisputeResponse from(Dispute dispute) {
        if (dispute == null) {
            return null;
        }
        return new DisputeResponse(
                dispute.getId(),
                dispute.getFreight() != null ? dispute.getFreight().getId() : null,
                dispute.getBooking() != null ? dispute.getBooking().getId() : null,
                dispute.getReportedBy() != null ? dispute.getReportedBy().getId() : null,
                dispute.getReason(),
                dispute.getDescription(),
                dispute.getStatus() != null ? dispute.getStatus().getValue() : null,
                dispute.getResolution(),
                dispute.getAdminNotes(),
                dispute.getRefundAmount(),
                dispute.getCreatedAt(),
                dispute.getUpdatedAt(),
                dispute.getResolvedAt()
        );
    }
}
