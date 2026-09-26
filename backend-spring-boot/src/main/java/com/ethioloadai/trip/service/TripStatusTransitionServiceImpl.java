package com.ethioloadai.trip.service;

import com.ethioloadai.booking.entity.Booking;
import com.ethioloadai.freight.entity.Freight;
import com.ethioloadai.trip.entity.Trip;
import com.ethioloadai.trip.entity.TripStop;
import com.ethioloadai.trip.exception.TripStatusException;
import com.ethioloadai.trip.exception.TripValidationException;
import com.ethioloadai.trip.repository.TripRepository;
import com.ethioloadai.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TripStatusTransitionServiceImpl implements TripStatusTransitionService {
    private static final Logger log = LoggerFactory.getLogger(TripStatusTransitionServiceImpl.class);

    private final TripRepository tripRepository;
    private final TripStateMachine tripStateMachine;
    private final TripInvariantService tripInvariantService;
    private final TripLifecycleService tripLifecycleService;

    @Override
    @Transactional
    public Trip executeStatusTransition(Long tripId, Trip.TripStatus targetStatus, User user) {
        log.info("Executing status transition for trip {} to {} by user {}", 
                tripId, targetStatus, user.getId());

        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> TripValidationException.bookingNotFound(tripId));

        // Validate authorization
        tripInvariantService.enforceTripOwnership(trip, user);

        // Validate transition
        TransitionValidationResult validation = validateTransition(trip, targetStatus, user);
        if (!validation.isValid()) {
            throw TripStatusException.invalidTransition(trip.getTripStatus(), targetStatus);
        }

        // Log warnings if any
        if (validation.hasWarnings()) {
            log.warn("Status transition has warnings: {}", validation.getWarnings());
        }

        // Execute transition with business logic
        Trip updatedTrip = performStatusTransition(trip, targetStatus, user, false, null);

        log.info("Status transition completed: trip {} from {} to {}", 
                tripId, trip.getTripStatus(), targetStatus);

        return updatedTrip;
    }

    @Override
    public TransitionValidationResult validateTransition(Trip trip, Trip.TripStatus targetStatus, User user) {
        List<String> violations = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        Trip.TripStatus currentStatus = trip.getTripStatus();

        // Basic state machine validation
        if (!tripStateMachine.isValidTransition(currentStatus, targetStatus)) {
            violations.add(String.format("Invalid transition from %s to %s", currentStatus, targetStatus));
        }

        // Authorization validation
        if (!canUserTransitionTrip(trip, targetStatus, user)) {
            violations.add("User not authorized for this transition");
        }

        // Business rule validation based on target status
        validateTargetStatusRequirements(trip, targetStatus, violations, warnings);

        boolean valid = violations.isEmpty();
        
        log.debug("Transition validation for trip {} from {} to {}: valid={}, violations={}, warnings={}", 
                trip.getId(), currentStatus, targetStatus, valid, violations.size(), warnings.size());

        return new TransitionValidationResult(valid, violations, warnings, currentStatus, targetStatus);
    }

    @Override
    public List<StatusOption> getAvailableTransitions(Trip trip, User user) {
        List<StatusOption> options = new ArrayList<>();
        Trip.TripStatus currentStatus = trip.getTripStatus();

        // Get all possible next statuses from state machine
        for (Trip.TripStatus nextStatus : tripStateMachine.getValidNextStatuses(currentStatus)) {
            if (canUserTransitionTrip(trip, nextStatus, user)) {
                StatusOption option = createStatusOption(trip, nextStatus);
                options.add(option);
            }
        }

        // Add current status as option (no-op transition)
        if (canUserTransitionTrip(trip, currentStatus, user)) {
            options.add(new StatusOption(
                currentStatus,
                "Keep current status: " + tripStateMachine.getStatusDescription(currentStatus),
                false,
                List.of()
            ));
        }

        return options;
    }

    @Override
    @Transactional
    public Trip forceStatusTransition(Long tripId, Trip.TripStatus targetStatus, User admin, String reason) {
        log.warn("FORCE transition for trip {} to {} by admin {} - reason: {}", 
                tripId, targetStatus, admin.getId(), reason);

        if (!isAdmin(admin)) {
            throw TripValidationException.bookingNotEligible("only admin users can force status transitions");
        }

        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> TripValidationException.bookingNotFound(tripId));

        // Perform forced transition
        Trip updatedTrip = performStatusTransition(trip, targetStatus, admin, true, reason);

        log.warn("FORCE transition completed: trip {} forced to {} by admin {}", 
                tripId, targetStatus, admin.getId());

        return updatedTrip;
    }

    @Override
    @Transactional
    public List<Trip> processAutomaticTransitions() {
        log.debug("Processing automatic status transitions");

        List<Trip> transitionedTrips = new ArrayList<>();

        // Find trips that should auto-complete
        List<Trip> candidateTrips = tripRepository.findByTripStatus(Trip.TripStatus.ONGOING);
        
        for (Trip trip : candidateTrips) {
            if (shouldAutoComplete(trip)) {
                try {
                    // Use system user for automatic transitions
                    Trip updatedTrip = performStatusTransition(
                        trip, Trip.TripStatus.COMPLETED, null, false, "Automatic completion");
                    transitionedTrips.add(updatedTrip);
                    
                    log.info("Auto-completed trip {} - all stops finished", trip.getId());
                } catch (Exception e) {
                    log.error("Failed to auto-complete trip {}: {}", trip.getId(), e.getMessage());
                }
            }
        }

        log.debug("Automatic transitions processed: {} trips transitioned", transitionedTrips.size());
        return transitionedTrips;
    }

    @Override
    public List<TransitionEvent> getTransitionHistory(Long tripId, User user) {
        // In a full implementation, this would query an audit table
        // For now, return empty list as audit logging is not implemented
        log.debug("Transition history requested for trip {} by user {}", tripId, user.getId());
        
        // Verify user can access this trip
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> TripValidationException.bookingNotFound(tripId));
        tripInvariantService.enforceTripOwnership(trip, user);

        return List.of(); // TODO: Implement audit logging
    }

    @Override
    @Transactional
    public Trip rollbackTransition(Long tripId, User admin, String reason) {
        log.warn("Rollback transition requested for trip {} by admin {} - reason: {}", 
                tripId, admin.getId(), reason);

        if (!isAdmin(admin)) {
            throw TripValidationException.bookingNotEligible("only admin users can rollback transitions");
        }

        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> TripValidationException.bookingNotFound(tripId));

        // For the current simple state machine, rollback is limited
        if (trip.getTripStatus() == Trip.TripStatus.COMPLETED) {
            // Only allow rollback to ONGOING if trip was recently completed
            if (trip.getEndTime() != null && 
                trip.getEndTime().isAfter(LocalDateTime.now().minusHours(24))) {
                
                Trip rolledBackTrip = performStatusTransition(
                    trip, Trip.TripStatus.ONGOING, admin, true, "Rollback: " + reason);
                
                log.warn("Rollback completed: trip {} rolled back to ONGOING by admin {}", 
                        tripId, admin.getId());
                return rolledBackTrip;
            } else {
                throw TripValidationException.bookingNotEligible(
                    "Cannot rollback trip completed more than 24 hours ago");
            }
        } else {
            throw TripValidationException.bookingNotEligible(
                "Cannot rollback trip in " + trip.getTripStatus() + " status");
        }
    }

    // ── Private Helper Methods ───────────────────────────────────────────────

    private Trip performStatusTransition(Trip trip, Trip.TripStatus targetStatus, User user, 
                                        boolean forced, String reason) {
        Trip.TripStatus originalStatus = trip.getTripStatus();
        
        // Set new status
        trip.setTripStatus(targetStatus);

        // Handle status-specific logic
        handleStatusSpecificLogic(trip, targetStatus, originalStatus);

        // Save trip
        Trip savedTrip = tripRepository.save(trip);

        // Validate consistency after transition
        if (!forced) {
            tripInvariantService.enforceTripStateConsistency(savedTrip);
        }

        // Log transition event (in production, this would be persisted)
        logTransitionEvent(trip.getId(), originalStatus, targetStatus, user, forced, reason);

        return savedTrip;
    }

    private void handleStatusSpecificLogic(Trip trip, Trip.TripStatus newStatus, Trip.TripStatus oldStatus) {
        switch (newStatus) {
            case ONGOING:
                if (oldStatus != Trip.TripStatus.ONGOING) {
                    // Starting or restarting trip
                    if (trip.getStartTime() == null) {
                        trip.setStartTime(LocalDateTime.now());
                    }
                    // Clear end time if rolling back from completion
                    trip.setEndTime(null);
                }
                break;
                
            case COMPLETED:
                // Set completion timestamp
                trip.setEndTime(LocalDateTime.now());
                
                // Update related entities
                updateRelatedEntitiesOnCompletion(trip);
                break;
        }
    }

    private void updateRelatedEntitiesOnCompletion(Trip trip) {
        // Update booking status
        Booking booking = trip.getBooking();
        if (booking != null) {
            booking.setBookingStatus(Booking.BookingStatus.DELIVERED);
        }

        // Update freight status
        if (booking != null && booking.getCargoRequest() != null) {
            Freight freight = booking.getCargoRequest();
            freight.setStatus(Freight.FreightStatus.COMPLETED);
        }
    }

    private void validateTargetStatusRequirements(Trip trip, Trip.TripStatus targetStatus, 
                                                 List<String> violations, List<String> warnings) {
        switch (targetStatus) {
            case ONGOING:
                // Validate trip can be started/restarted
                if (trip.getBooking() == null || 
                    trip.getBooking().getBookingStatus() != Booking.BookingStatus.ACCEPTED) {
                    violations.add("Booking must be in ACCEPTED status");
                }
                break;
                
            case COMPLETED:
                // Validate trip can be completed
                if (trip.getStartTime() == null) {
                    violations.add("Trip must be started before completion");
                }
                
                // Check if all stops are completed for multi-stop trips
                if (trip.isMultiStop()) {
                    validateMultiStopCompletion(trip, violations, warnings);
                }
                
                // Warn about very short trips
                if (trip.getStartTime() != null) {
                    long minutes = java.time.Duration.between(trip.getStartTime(), LocalDateTime.now()).toMinutes();
                    if (minutes < 30) {
                        warnings.add("Trip duration is unusually short (" + minutes + " minutes)");
                    }
                }
                break;
        }
    }

    private void validateMultiStopCompletion(Trip trip, List<String> violations, List<String> warnings) {
        if (trip.getCompletedStops() < trip.getTotalStops()) {
            violations.add(String.format("Not all stops completed (%d/%d)", 
                         trip.getCompletedStops(), trip.getTotalStops()));
        }

        // Check actual stop statuses
        long actualCompleted = trip.getTripStops().stream()
                .mapToLong(stop -> stop.getStopStatus() == TripStop.StopStatus.COMPLETED ? 1 : 0)
                .sum();
        
        if (actualCompleted != trip.getCompletedStops()) {
            warnings.add("Stop counter mismatch - will be corrected");
        }
    }

    private boolean canUserTransitionTrip(Trip trip, Trip.TripStatus targetStatus, User user) {
        if (user == null) {
            return false; // System transitions
        }

        // Admin can perform any transition
        if (isAdmin(user)) {
            return true;
        }

        // Check if user can access the trip
        if (!tripInvariantService.canUserAccessTrip(trip, user)) {
            return false;
        }

        // Only drivers can change trip status (not shippers)
        if (trip.getBooking() != null && trip.getBooking().getDriver() != null) {
            return trip.getBooking().getDriver().getId().equals(user.getId());
        }

        return false;
    }

    private StatusOption createStatusOption(Trip trip, Trip.TripStatus status) {
        String description = tripStateMachine.getStatusDescription(status);
        boolean requiresConfirmation = (status == Trip.TripStatus.COMPLETED);
        List<String> prerequisites = getPrerequisitesForStatus(trip, status);

        return new StatusOption(status, description, requiresConfirmation, prerequisites);
    }

    private List<String> getPrerequisitesForStatus(Trip trip, Trip.TripStatus status) {
        List<String> prerequisites = new ArrayList<>();

        switch (status) {
            case COMPLETED:
                if (trip.isMultiStop() && trip.getCompletedStops() < trip.getTotalStops()) {
                    prerequisites.add("Complete all trip stops");
                }
                break;
        }

        return prerequisites;
    }

    private boolean shouldAutoComplete(Trip trip) {
        // Auto-complete multi-stop trips when all stops are finished
        if (trip.isMultiStop()) {
            long completedStops = trip.getTripStops().stream()
                    .mapToLong(stop -> stop.getStopStatus() == TripStop.StopStatus.COMPLETED ? 1 : 0)
                    .sum();
            return completedStops >= trip.getTotalStops();
        }

        // No auto-completion for single-stop trips (driver must manually complete)
        return false;
    }

    private void logTransitionEvent(Long tripId, Trip.TripStatus fromStatus, Trip.TripStatus toStatus,
                                   User user, boolean forced, String reason) {
        // In production, this would persist to an audit table
        log.info("Trip {} status transition: {} -> {} by user {} (forced: {}, reason: {})",
                tripId, fromStatus, toStatus, 
                user != null ? user.getId() : "SYSTEM", 
                forced, reason != null ? reason : "Normal transition");
    }

    private boolean isAdmin(User user) {
        return user != null && user.getRole() == User.Role.ADMIN;
    }
}