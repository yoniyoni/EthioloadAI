package com.ethioloadai.rating.dto;

import com.ethioloadai.rating.entity.Rating;

import java.time.LocalDateTime;

public record RatingResponse(
        Long id,
        Long bookingId,
        Long shipperId,
        Long driverId,
        Long raterId,
        Integer rating,
        String feedback,
        LocalDateTime createdAt
) {
    public static RatingResponse from(Rating r) {
        return new RatingResponse(
                r.getId(),
                r.getBooking().getId(),
                r.getShipper().getId(),
                r.getDriver().getId(),
                r.getRater() != null ? r.getRater().getId() : null,
                r.getRating(),
                r.getFeedback(),
                r.getCreatedAt()
        );
    }
}
