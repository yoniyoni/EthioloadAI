package com.ethioloadai.rating.controller;

import com.ethioloadai.rating.dto.RatingResponse;
import com.ethioloadai.rating.dto.SubmitRatingRequest;
import com.ethioloadai.rating.service.RatingService;
import com.ethioloadai.user.entity.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Phase 7 — Rating REST endpoints.
 * Base path: {@code /api/ratings}
 */
@RestController
@RequestMapping("/ratings")
@RequiredArgsConstructor
@Tag(name = "Ratings", description = "Phase 7 — post-trip ratings")
public class RatingController {

    private final RatingService ratingService;

    @PostMapping
    @Operation(summary = "Submit a rating for a completed booking")
    public ResponseEntity<RatingResponse> submitRating(
            @Valid @RequestBody SubmitRatingRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(ratingService.submitRating(request, getUser(authentication)));
    }

    @GetMapping("/booking/{bookingId}")
    @Operation(summary = "Get ratings for a booking")
    public ResponseEntity<List<RatingResponse>> getByBooking(
            @PathVariable Long bookingId,
            Authentication authentication) {
        return ResponseEntity.ok(ratingService.getRatingsForBooking(bookingId, getUser(authentication)));
    }

    @GetMapping("/driver/{driverId}")
    @Operation(summary = "Get ratings for a driver")
    public ResponseEntity<List<RatingResponse>> getByDriver(
            @PathVariable Long driverId,
            Authentication authentication) {
        return ResponseEntity.ok(ratingService.getRatingsForDriver(driverId, getUser(authentication)));
    }

    private User getUser(Authentication auth) { return (User) auth.getPrincipal(); }
}
