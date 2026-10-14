package com.ethioloadai.trip.service;

import com.ethioloadai.booking.entity.Booking;
import com.ethioloadai.booking.repository.BookingRepository;
import com.ethioloadai.carrier.fleet.entity.CarrierDriver;
import com.ethioloadai.carrier.fleet.entity.CarrierDriverStatus;
import com.ethioloadai.carrier.fleet.entity.DriverAvailability;
import com.ethioloadai.carrier.fleet.repository.CarrierDriverRepository;
import com.ethioloadai.freight.entity.Freight;
import com.ethioloadai.freight.repository.FreightRepository;
import com.ethioloadai.trip.entity.Trip;
import com.ethioloadai.trip.entity.TripStop;
import com.ethioloadai.trip.exception.TripStatusException;
import com.ethioloadai.trip.exception.TripValidationException;
import com.ethioloadai.trip.repository.TripRepository;
import com.ethioloadai.trip.repository.TripStopRepository;
import com.ethioloadai.user.entity.User;
import com.ethioloadai.vehicle.repository.VehicleRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.AccessDeniedException;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Trip lifecycle operations for Phase 7.
 *
 * <h2>Phase 7 corrections over Phase 6</h2>
 * <ul>
 *   <li>{@link #startTrip} now transitions Freight → IN_TRANSIT and Vehicle availability
 *       → "on_trip", fixing a Phase 6 defect where vehicle stayed "assigned".</li>
 *   <li>{@link #completeTripAfterVerification} is the Phase 7 proof-verified completion
 *       path. The legacy {@link #completeTrip} driver path remains available for ONGOING
 *       trips. Both update driver/vehicle availability back to AVAILABLE.</li>
 *   <li>New {@link #markArrived}: driver signals arrival (ONGOING → ARRIVED).</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class TripLifecycleServiceImpl implements TripLifecycleService {
    private static final Logger log = LoggerFactory.getLogger(TripLifecycleServiceImpl.class);

    private final TripRepository tripRepository;
    private final BookingRepository bookingRepository;
    private final FreightRepository freightRepository;
    private final VehicleRepository vehicleRepository;
    private final CarrierDriverRepository carrierDriverRepository;
    private final TripInvariantService tripInvariantService;
    private final TripStopRepository tripStopRepository;
    private final EntityManager entityManager;

    // Business rule constants
    private static final int MAX_TRIP_DURATION_HOURS = 72; // 3 days max
    private static final int OVERDUE_WARNING_HOURS = 48; // 2 days warning

    // ── Trip Start ────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public Trip startTrip(Long tripId, User driver) {
        log.info("startTrip: tripId={} driverId={}", tripId, driver.getId());

        Trip trip = getTripForUpdate(tripId, driver);
        validateTripStartEligibility(trip, driver);

        // Idempotency: trip already started (startTime already set and still ONGOING)
        if (trip.getStartTime() != null && trip.getTripStatus() == Trip.TripStatus.ONGOING) {
            log.info("startTrip: trip {} already started — returning idempotent response", tripId);
            return trip;
        }

        if (trip.getTripStatus() != Trip.TripStatus.ONGOING) {
            throw TripStatusException.invalidTransition(trip.getTripStatus(), Trip.TripStatus.ONGOING);
        }

        trip.setStartTime(LocalDateTime.now());

        // Phase 7 fix: update Freight status to IN_TRANSIT.
        // Strategy: evict the stale entity (if any) from L1 cache, then do a fresh load,
        // update status, and save. This ensures Hibernate's dirty-tracking writes IN_TRANSIT
        // to the DB on flush — preventing a stale MATCHED entity from overwriting our update.
        Booking booking = trip.getBooking();
        Long freightId = booking.getCargoRequest().getId();
        // Evict any stale cached Freight entity (avoids overwrite on auto-flush)
        Freight staleFreight = entityManager.find(Freight.class, freightId);
        if (staleFreight != null) {
            entityManager.detach(staleFreight);
        }
        // Fresh load and update — Hibernate now tracks IN_TRANSIT as the authoritative state
        freightRepository.findById(freightId).ifPresent(freshFreight -> {
            if (freshFreight.getStatus() == Freight.FreightStatus.MATCHED) {
                freshFreight.setStatus(Freight.FreightStatus.IN_TRANSIT);
                freightRepository.save(freshFreight);
                log.info("startTrip: freight {} → IN_TRANSIT", freightId);
            }
        });

        // Phase 7 fix: update Vehicle availability to on_trip (was missing in Phase 6)
        vehicleRepository.findById(trip.getVehicle().getId()).ifPresent(v -> {
            v.setAvailabilityStatus("on_trip");
            vehicleRepository.saveAndFlush(v);
            log.info("startTrip: vehicle {} → on_trip", v.getId());
        });

        // Driver availability — already set to ON_TRIP in acceptAssignment (Phase 6)
        // But ensure it is correct in case of redispatch edge case
        ensureDriverOnTrip(booking, driver);

        Trip savedTrip = tripRepository.save(trip);
        tripInvariantService.enforceTripStateConsistency(savedTrip);

        log.info("startTrip: trip {} started successfully", tripId);
        return savedTrip;
    }

    // ── Arrival ───────────────────────────────────────────────────────────────

    /**
     * Phase 7 — Driver marks arrival at destination.
     * Transitions: ONGOING → ARRIVED.
     * Idempotent: repeated calls with same trip in ARRIVED state return current state.
     */
    @Override
    @Transactional
    public Trip markArrived(Long tripId, User driver) {
        log.info("markArrived: tripId={} driverId={}", tripId, driver.getId());

        Trip trip = getTripForUpdate(tripId, driver);
        validateMultiStopIntermediateStops(trip, tripId);

        if (trip.getTripStatus() == Trip.TripStatus.ARRIVED) {
            log.info("markArrived: trip {} already ARRIVED — idempotent", tripId);
            return trip;
        }

        if (trip.getTripStatus() != Trip.TripStatus.ONGOING) {
            throw TripStatusException.invalidTransition(trip.getTripStatus(), Trip.TripStatus.ARRIVED);
        }

        if (trip.getStartTime() == null) {
            throw TripValidationException.bookingNotEligible("trip must be started before marking arrival");
        }

        trip.setTripStatus(Trip.TripStatus.ARRIVED);
        trip.setArrivedAt(LocalDateTime.now());

        Trip savedTrip = tripRepository.save(trip);
        log.info("markArrived: trip {} → ARRIVED at {}", tripId, savedTrip.getArrivedAt());
        return savedTrip;
    }

    @Override
    @Transactional
    public Trip confirmDeliveryByShipper(Long tripId, User shipper) {
        if (shipper == null || shipper.getRole() != User.Role.SHIPPER) {
            throw new AccessDeniedException("Only the shipment's shipper may confirm delivery.");
        }

        Trip trip = lockTripAndFreightForUpdate(tripId);
        Booking booking = trip.getBooking();
        Freight freight = booking.getCargoRequest();
        if (freight.getShipper() == null
                || !freight.getShipper().getId().equals(shipper.getId())) {
            throw new AccessDeniedException("Only the shipment's shipper may confirm delivery.");
        }

        if (trip.getTripStatus() == Trip.TripStatus.COMPLETED
                && trip.getShipperDeliveryConfirmedBy() != null
                && trip.getShipperDeliveryConfirmedBy().getId().equals(shipper.getId())) {
            return trip;
        }

        validateMultiStopIntermediateStops(trip, tripId);

        if (trip.getTripStatus() != Trip.TripStatus.ARRIVED
                || trip.getStartTime() == null
                || trip.getArrivedAt() == null) {
            throw TripValidationException.bookingNotEligible(
                    "delivery can only be confirmed after the assigned driver marks arrival");
        }
        if (booking.getBookingStatus() != Booking.BookingStatus.ACCEPTED) {
            throw TripValidationException.bookingNotEligible(
                    "booking must be ACCEPTED before delivery confirmation");
        }
        if (freight.getStatus() != Freight.FreightStatus.IN_TRANSIT) {
            throw TripValidationException.bookingNotEligible(
                    "freight must be IN_TRANSIT before delivery confirmation");
        }

        trip.setShipperDeliveryConfirmedBy(shipper);
        trip.setShipperDeliveryConfirmedAt(LocalDateTime.now());
        return performCompletion(trip);
    }

    private void validateMultiStopIntermediateStops(Trip trip, Long tripId) {
        if (trip.getTripType() != Trip.TripType.MULTI_STOP) {
            return;
        }

        List<TripStop> stops = tripStopRepository.findByTrip_IdOrderByStopOrderAsc(tripId);
        int expectedIntermediateStops = trip.getTotalStops() - 1;
        boolean allIntermediateStopsComplete =
                expectedIntermediateStops > 0
                && stops.size() == expectedIntermediateStops
                && java.util.stream.IntStream.range(0, stops.size()).allMatch(index ->
                        stops.get(index).getStopOrder() == index + 1
                                && stops.get(index).getStopStatus() == TripStop.StopStatus.COMPLETED);
        if (!allIntermediateStopsComplete) {
            throw TripValidationException.bookingNotEligible(
                    "all ordered intermediate stops must be completed before destination delivery confirmation");
        }
    }

    // ── Proof Submitted — set DELIVERY_PENDING_VERIFICATION ──────────────────

    /**
     * Called by ProofOfDeliveryService after a proof is persisted.
     * Transitions: ARRIVED → DELIVERY_PENDING_VERIFICATION.
     */
    @Override
    @Transactional
    public Trip markDeliveryPendingVerification(Long tripId) {
        log.info("markDeliveryPendingVerification: tripId={}", tripId);

        Trip trip = lockTripAndFreightForUpdate(tripId);

        if (trip.getTripStatus() == Trip.TripStatus.DELIVERY_PENDING_VERIFICATION) {
            log.info("markDeliveryPendingVerification: trip {} already PENDING_VERIFICATION — idempotent", tripId);
            return trip;
        }

        if (trip.getTripStatus() != Trip.TripStatus.ARRIVED) {
            throw TripStatusException.invalidTransition(
                    trip.getTripStatus(), Trip.TripStatus.DELIVERY_PENDING_VERIFICATION);
        }

        trip.setTripStatus(Trip.TripStatus.DELIVERY_PENDING_VERIFICATION);
        Trip savedTrip = tripRepository.save(trip);
        log.info("markDeliveryPendingVerification: trip {} → DELIVERY_PENDING_VERIFICATION", tripId);
        return savedTrip;
    }

    // ── Proof Rejected — revert to ARRIVED ───────────────────────────────────

    /**
     * Called when shipper rejects proof. Driver may resubmit.
     * Transitions: DELIVERY_PENDING_VERIFICATION → ARRIVED.
     */
    @Override
    @Transactional
    public Trip revertToArrived(Long tripId) {
        log.info("revertToArrived: tripId={}", tripId);

        Trip trip = lockTripAndFreightForUpdate(tripId);

        if (trip.getTripStatus() != Trip.TripStatus.DELIVERY_PENDING_VERIFICATION) {
            throw TripStatusException.invalidTransition(trip.getTripStatus(), Trip.TripStatus.ARRIVED);
        }

        trip.setTripStatus(Trip.TripStatus.ARRIVED);
        Trip savedTrip = tripRepository.save(trip);
        log.info("revertToArrived: trip {} → ARRIVED (proof rejected, resubmission allowed)", tripId);
        return savedTrip;
    }

    // ── Complete ──────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public Trip completeTrip(Long tripId, User driver) {
        log.info("completeTrip (legacy/direct): tripId={} driverId={}", tripId, driver.getId());

        Trip trip = getTripForUpdate(tripId, driver);
        validateTripCompletionEligibility(trip, driver);

        return performCompletion(trip);
    }

    @Override
    @Transactional
    public Trip forceCompleteTrip(Long tripId, User admin) {
        if (admin == null || admin.getRole() != User.Role.ADMIN) {
            throw TripValidationException.bookingNotEligible(
                    "only admin users can force status transitions");
        }

        Trip trip = lockTripAndFreightForUpdate(tripId);
        boolean alreadyCompleted = trip.getTripStatus() == Trip.TripStatus.COMPLETED;
        LocalDateTime previousEndTime = trip.getEndTime();
        Trip completedTrip = performCompletion(trip, false);
        if (alreadyCompleted && previousEndTime != null) {
            completedTrip.setEndTime(previousEndTime);
            tripRepository.save(completedTrip);
        }
        return completedTrip;
    }

    /**
     * Phase 7 — called by DeliveryVerificationService after shipper verifies proof.
     * Does NOT require driver auth — verification is shipper-driven.
     * Updates Trip, Booking, Freight, Driver availability, Vehicle availability.
     */
    @Override
    @Transactional
    public Trip completeTripAfterVerification(Long tripId) {
        log.info("completeTripAfterVerification: tripId={}", tripId);

        Trip trip = lockTripAndFreightForUpdate(tripId);

        if (trip.getTripStatus() == Trip.TripStatus.COMPLETED) {
            log.info("completeTripAfterVerification: trip {} already COMPLETED — idempotent", tripId);
            return trip;
        }

        if (trip.getTripStatus() != Trip.TripStatus.DELIVERY_PENDING_VERIFICATION) {
            throw TripStatusException.invalidTransition(
                    trip.getTripStatus(), Trip.TripStatus.COMPLETED);
        }

        return performCompletion(trip);
    }

    // ── Progress / monitoring ─────────────────────────────────────────────────

    @Override
    public TripProgress getTripProgress(Trip trip) {
        Duration duration = calculateTripDuration(trip);
        boolean overdue = isTripOverdue(trip);

        int completedStops = trip.getCompletedStops();
        int totalStops = trip.getTotalStops();
        double progressPercentage = totalStops > 0
                ? (double) completedStops / totalStops * 100.0
                : 0.0;

        return new TripProgress(
                trip.getTripStatus(),
                duration,
                completedStops,
                totalStops,
                progressPercentage,
                overdue
        );
    }

    @Override
    public void validateTripStartEligibility(Trip trip, User driver) {
        log.debug("validateTripStartEligibility: tripId={} driverId={}", trip.getId(), driver.getId());

        tripInvariantService.enforceTripOwnership(trip, driver);

        if (trip.getTripStatus() != Trip.TripStatus.ONGOING) {
            throw TripValidationException.bookingNotEligible(
                    "trip must be in ONGOING status to start (current: " + trip.getTripStatus() + ")");
        }

        Booking booking = trip.getBooking();
        if (booking.getBookingStatus() != Booking.BookingStatus.ACCEPTED) {
            throw TripValidationException.invalidBookingStatus(booking.getBookingStatus().name());
        }

        if (!Boolean.TRUE.equals(driver.getIsActive())) {
            throw TripValidationException.bookingNotEligible("driver account is not active");
        }

        log.debug("validateTripStartEligibility: OK for trip {}", trip.getId());
    }

    @Override
    public void validateTripCompletionEligibility(Trip trip, User driver) {
        log.debug("validateTripCompletionEligibility: tripId={} driverId={}", trip.getId(), driver.getId());

        tripInvariantService.enforceTripOwnership(trip, driver);

        if (trip.getTripStatus() == Trip.TripStatus.COMPLETED) {
            throw TripStatusException.cannotModifyCompleted();
        }
        if (driver.getRole() != User.Role.ADMIN) {
            throw TripValidationException.bookingNotEligible(
                    "trip completion requires confirmation by the shipment's shipper");
        }

        if (trip.getStartTime() == null) {
            throw TripValidationException.bookingNotEligible("trip must be started before completion");
        }

        if (trip.isMultiStop()) {
            validateMultiStopCompletionRequirements(trip);
        }

        log.debug("validateTripCompletionEligibility: OK for trip {}", trip.getId());
    }

    @Override
    public List<Trip> getTripsRequiringAttention() {
        List<Trip> activeTrips = tripRepository.findByTripStatus(Trip.TripStatus.ONGOING);
        return activeTrips.stream()
                .filter(this::isTripOverdue)
                .collect(Collectors.toList());
    }

    @Override
    public Duration calculateTripDuration(Trip trip) {
        LocalDateTime startTime = trip.getStartTime();
        if (startTime == null) return Duration.ZERO;

        LocalDateTime endTime = trip.getEndTime() != null
                ? trip.getEndTime()
                : LocalDateTime.now();
        return Duration.between(startTime, endTime);
    }

    @Override
    public boolean isTripOverdue(Trip trip) {
        if (trip.getTripStatus() == Trip.TripStatus.COMPLETED) return false;
        return calculateTripDuration(trip).toHours() > OVERDUE_WARNING_HOURS;
    }

    @Override
    public boolean handleTripTimeout(Trip trip) {
        if (trip.getTripStatus() != Trip.TripStatus.ONGOING) return false;
        if (calculateTripDuration(trip).toHours() > MAX_TRIP_DURATION_HOURS) {
            log.warn("Trip {} has exceeded maximum duration of {} hours", trip.getId(), MAX_TRIP_DURATION_HOURS);
            return true;
        }
        return false;
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private Trip getTripWithAuthorization(Long tripId, User driver) {
        return tripRepository.findByIdAndDriverId(tripId, driver.getId())
                .orElseThrow(() -> TripValidationException.unauthorizedAccess(tripId, driver.getId()));
    }

    private Trip getTripForUpdate(Long tripId, User driver) {
        Trip trip = lockTripAndFreightForUpdate(tripId);
        tripInvariantService.enforceTripOwnership(trip, driver);
        return trip;
    }

    private Trip lockTripAndFreightForUpdate(Long tripId) {
        Long freightId = tripRepository.findFreightIdById(tripId)
                .orElseThrow(() -> TripValidationException.bookingNotFound(tripId));
        Freight freight = freightRepository.findByIdForUpdate(freightId)
                .orElseThrow(() -> TripValidationException.bookingNotFound(tripId));
        if (freight.getStatus() == Freight.FreightStatus.CANCELLED) {
            throw TripValidationException.bookingNotEligible("cancelled freight cannot advance a trip");
        }
        Trip trip = tripRepository.findByIdForUpdate(tripId)
                .orElseThrow(() -> TripValidationException.bookingNotFound(tripId));
        if (!trip.getBooking().getCargoRequest().getId().equals(freightId)) {
            throw TripValidationException.bookingNotEligible("trip freight relationship changed");
        }
        trip.getBooking().setCargoRequest(freight);
        return trip;
    }

    /**
     * Shared completion logic: updates Trip, Booking, Freight, Driver, Vehicle.
     * Called both from legacy completeTrip() and Phase 7 completeTripAfterVerification().
     */
    private Trip performCompletion(Trip trip) {
        return performCompletion(trip, true);
    }

    private Trip performCompletion(Trip trip, boolean validateMultiStopCompletion) {
        trip.setTripStatus(Trip.TripStatus.COMPLETED);
        trip.setEndTime(LocalDateTime.now());

        if (trip.isMultiStop() && validateMultiStopCompletion) {
            handleMultiStopTripCompletion(trip);
        }

        // Booking → DELIVERED (triggers payment initiation eligibility)
        Booking booking = trip.getBooking();
        if (booking.getBookingStatus() != Booking.BookingStatus.DELIVERED
                && booking.getBookingStatus() != Booking.BookingStatus.COMPLETED) {
            booking.setBookingStatus(Booking.BookingStatus.DELIVERED);
            bookingRepository.save(booking);
            log.info("performCompletion: booking {} → DELIVERED", booking.getId());
        }

        // Freight → DELIVERED: evict stale entity, fresh load, update, save
        Long freightId = booking.getCargoRequest().getId();
        Freight staleAtCompletion = entityManager.find(Freight.class, freightId);
        if (staleAtCompletion != null) {
            entityManager.detach(staleAtCompletion);
        }
        freightRepository.findById(freightId).ifPresent(freshFreight -> {
            if (freshFreight.getStatus() == Freight.FreightStatus.IN_TRANSIT
                    || freshFreight.getStatus() == Freight.FreightStatus.MATCHED) {
                freshFreight.setStatus(Freight.FreightStatus.DELIVERED);
                freightRepository.save(freshFreight);
                log.info("performCompletion: freight {} → DELIVERED", freightId);
            }
        });

        // Driver → AVAILABLE
        Long driverUserId = trip.getDriver().getId();
        // Find carrier driver record (may be null for legacy non-carrier trips)
        carrierDriverRepository.findByDriverUserIdAndStatus(driverUserId, CarrierDriverStatus.ACTIVE)
                .stream()
                .filter(cd -> cd.getAvailability() == DriverAvailability.ON_TRIP
                           || cd.getAvailability() == DriverAvailability.ASSIGNED)
                .findFirst()
                .ifPresent(cd -> {
                    cd.setAvailability(DriverAvailability.AVAILABLE);
                    carrierDriverRepository.save(cd);
                    log.info("performCompletion: driver {} → AVAILABLE", driverUserId);
                });

        // Vehicle → available
        vehicleRepository.findById(trip.getVehicle().getId()).ifPresent(v -> {
            if ("on_trip".equalsIgnoreCase(v.getAvailabilityStatus())
                    || "assigned".equalsIgnoreCase(v.getAvailabilityStatus())) {
                v.setAvailabilityStatus("available");
                vehicleRepository.save(v);
                log.info("performCompletion: vehicle {} → available", v.getId());
            }
        });

        Trip savedTrip = tripRepository.save(trip);
        tripInvariantService.enforceTripStateConsistency(savedTrip);

        log.info("performCompletion: trip {} COMPLETED", trip.getId());
        return savedTrip;
    }

    private void ensureDriverOnTrip(Booking booking, User driver) {
        if (booking.getCarrierCompanyId() != null) {
            carrierDriverRepository
                    .findByCarrierCompanyIdAndDriverUserId(booking.getCarrierCompanyId(), driver.getId())
                    .ifPresent(cd -> {
                        if (cd.getStatus() == CarrierDriverStatus.ACTIVE
                                && cd.getAvailability() != DriverAvailability.ON_TRIP) {
                            cd.setAvailability(DriverAvailability.ON_TRIP);
                            carrierDriverRepository.save(cd);
                            log.debug("ensureDriverOnTrip: driver {} → ON_TRIP", driver.getId());
                        }
                    });
        }
    }

    private void handleMultiStopTripCompletion(Trip trip) {
        log.debug("handleMultiStopTripCompletion: tripId={}", trip.getId());

        List<TripStop> incompleteStops = trip.getTripStops().stream()
                .filter(stop -> stop.getStopStatus() != TripStop.StopStatus.COMPLETED)
                .collect(Collectors.toList());

        if (!incompleteStops.isEmpty()) {
            throw TripValidationException.bookingNotEligible(
                    String.format("Cannot complete trip with %d incomplete stops", incompleteStops.size()));
        }

        trip.setCompletedStops(trip.getTotalStops());
    }

    private void validateMultiStopCompletionRequirements(Trip trip) {
        if (trip.getCompletedStops() < trip.getTotalStops()) {
            throw TripValidationException.bookingNotEligible(
                    String.format("Trip has %d uncompleted stops (completed: %d, total: %d)",
                            trip.getTotalStops() - trip.getCompletedStops(),
                            trip.getCompletedStops(),
                            trip.getTotalStops()));
        }

        long actualCompleted = trip.getTripStops().stream()
                .filter(s -> s.getStopStatus() == TripStop.StopStatus.COMPLETED)
                .count();

        if (actualCompleted != trip.getCompletedStops()) {
            trip.setCompletedStops((int) actualCompleted);
            if (actualCompleted < trip.getTotalStops()) {
                throw TripValidationException.bookingNotEligible(
                        String.format("Trip has incomplete stops (actual: %d, total: %d)",
                                actualCompleted, trip.getTotalStops()));
            }
        }
    }
}
