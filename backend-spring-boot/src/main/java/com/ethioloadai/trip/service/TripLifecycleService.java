package com.ethioloadai.trip.service;

import com.ethioloadai.trip.entity.Trip;
import com.ethioloadai.user.entity.User;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * Service for managing Trip lifecycle operations and business rules.
 * Handles the progression of trips through their states with proper
 * validation and related entity updates.
 */
public interface TripLifecycleService {

    /**
     * Start a trip by setting it to ONGOING status.
     * Validates prerequisites and updates related entities.
     *
     * @param tripId trip to start
     * @param driver the driver starting the trip
     * @return updated trip
     */
    Trip startTrip(Long tripId, User driver);

    /**
     * Phase 7 — Driver marks arrival at the destination.
     * Transitions: ONGOING → ARRIVED.
     * Idempotent.
     *
     * @param tripId trip ID
     * @param driver the assigned driver
     * @return updated trip
     */
    Trip markArrived(Long tripId, User driver);

    /**
     * Completes an arrived trip after confirmation by its freight's shipper.
     */
    Trip confirmDeliveryByShipper(Long tripId, User shipper);

    /**
     * Phase 7 — Called by proof-of-delivery service after proof is submitted.
     * Transitions: ARRIVED → DELIVERY_PENDING_VERIFICATION.
     *
     * @param tripId trip ID
     * @return updated trip
     */
    Trip markDeliveryPendingVerification(Long tripId);

    /**
     * Phase 7 — Called when shipper rejects proof.
     * Transitions: DELIVERY_PENDING_VERIFICATION → ARRIVED (driver may resubmit).
     *
     * @param tripId trip ID
     * @return updated trip
     */
    Trip revertToArrived(Long tripId);

    /**
     * Phase 7 — Called by delivery-verification service after shipper verifies proof.
     * Updates Trip, Booking, Freight, Driver, Vehicle statuses transactionally.
     * Does NOT require driver auth (shipper-driven).
     * Idempotent.
     *
     * @param tripId trip ID
     * @return completed trip
     */
    Trip completeTripAfterVerification(Long tripId);

    /**
     * Complete a trip and handle all completion logic (legacy/direct driver path).
     * Updates booking status, freight status, and calculates final amounts.
     *
     * @param tripId trip to complete
     * @param driver the driver completing the trip
     * @return completed trip
     */
    Trip completeTrip(Long tripId, User driver);

    /**
     * Complete a trip through the explicit administrator override path.
     * Preserves the override's ability to bypass stop/proof eligibility checks.
     */
    Trip forceCompleteTrip(Long tripId, User admin);

    /**
     * Get trip progress information including duration, stops completion, etc.
     *
     * @param trip the trip to analyze
     * @return trip progress summary
     */
    TripProgress getTripProgress(Trip trip);

    /**
     * Validate if a trip can be started.
     * Checks prerequisites like driver readiness, vehicle availability, etc.
     *
     * @param trip the trip to validate
     * @param driver the driver attempting to start
     * @throws TripValidationException if trip cannot be started
     */
    void validateTripStartEligibility(Trip trip, User driver);

    /**
     * Validate if a trip can be completed.
     * Checks all stops are completed, no pending issues, etc.
     *
     * @param trip the trip to validate
     * @param driver the driver attempting to complete
     * @throws TripValidationException if trip cannot be completed
     */
    void validateTripCompletionEligibility(Trip trip, User driver);

    /**
     * Get all trips that are overdue or require attention.
     *
     * @return list of trips requiring attention
     */
    List<Trip> getTripsRequiringAttention();

    /**
     * Calculate trip duration from start to current time or completion.
     *
     * @param trip the trip to calculate duration for
     * @return duration of the trip
     */
    Duration calculateTripDuration(Trip trip);

    /**
     * Check if a trip is overdue based on business rules.
     *
     * @param trip the trip to check
     * @return true if trip is overdue
     */
    boolean isTripOverdue(Trip trip);

    /**
     * Handle trip timeout scenarios (trips ongoing too long).
     *
     * @param trip the trip that may have timed out
     * @return true if action was taken
     */
    boolean handleTripTimeout(Trip trip);

    /**
     * Trip progress information container.
     */
    class TripProgress {
        private final Trip.TripStatus status;
        private final Duration duration;
        private final int completedStops;
        private final int totalStops;
        private final double progressPercentage;
        private final boolean isOverdue;

        public TripProgress(Trip.TripStatus status, Duration duration, 
                           int completedStops, int totalStops, 
                           double progressPercentage, boolean isOverdue) {
            this.status = status;
            this.duration = duration;
            this.completedStops = completedStops;
            this.totalStops = totalStops;
            this.progressPercentage = progressPercentage;
            this.isOverdue = isOverdue;
        }

        // Getters
        public Trip.TripStatus getStatus() { return status; }
        public Duration getDuration() { return duration; }
        public int getCompletedStops() { return completedStops; }
        public int getTotalStops() { return totalStops; }
        public double getProgressPercentage() { return progressPercentage; }
        public boolean isOverdue() { return isOverdue; }
        public boolean isCompleted() { return completedStops >= totalStops; }
    }
}