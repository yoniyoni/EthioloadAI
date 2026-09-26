package com.ethioloadai.rating.service;

import com.ethioloadai.rating.dto.RatingResponse;
import com.ethioloadai.rating.dto.SubmitRatingRequest;
import com.ethioloadai.user.entity.User;

import java.util.List;

/**
 * Phase 7 — Post-trip rating service.
 *
 * <h2>Business rules</h2>
 * <ul>
 *   <li>Rating only allowed after booking reaches DELIVERED or COMPLETED status.</li>
 *   <li>Each party (shipper, driver) may rate once per booking.</li>
 *   <li>Unrelated users cannot rate.</li>
 * </ul>
 */
public interface RatingService {

    /**
     * Submit a rating for a completed booking.
     *
     * @param request bookingId, rating (1–5), optional feedback
     * @param caller  must be the booking's shipper or driver
     */
    RatingResponse submitRating(SubmitRatingRequest request, User caller);

    /**
     * Get all ratings for a booking.
     */
    List<RatingResponse> getRatingsForBooking(Long bookingId, User caller);

    /**
     * Get all ratings for a driver (their profile).
     */
    List<RatingResponse> getRatingsForDriver(Long driverId, User caller);
}
