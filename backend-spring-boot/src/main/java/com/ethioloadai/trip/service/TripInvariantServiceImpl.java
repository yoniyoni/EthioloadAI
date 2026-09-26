package com.ethioloadai.trip.service;

import com.ethioloadai.booking.entity.Booking;
import com.ethioloadai.booking.repository.BookingRepository;
import com.ethioloadai.trip.entity.Trip;
import com.ethioloadai.trip.exception.TripValidationException;
import com.ethioloadai.trip.repository.TripRepository;
import com.ethioloadai.user.entity.User;
import com.ethioloadai.vehicle.entity.Vehicle;
import com.ethioloadai.vehicle.repository.VehicleRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class TripInvariantServiceImpl implements TripInvariantService {
    private static final Logger log = LoggerFactory.getLogger(TripInvariantServiceImpl.class);

    private final TripRepository tripRepository;
    private final BookingRepository bookingRepository;
    private final VehicleRepository vehicleRepository;

    @Override
    @Transactional(readOnly = true)
    public void enforceOneToOneTripBooking(Booking booking) {
        log.debug("Enforcing one-to-one Trip/Booking invariant for booking {}", booking.getId());

        // Use pessimistic locking to prevent race conditions
        Optional<Trip> existingTrip = tripRepository.findByBookingIdWithLock(booking.getId());
        
        if (existingTrip.isPresent()) {
            throw TripValidationException.tripAlreadyExists(booking.getId());
        }

        // Verify booking is not in an invalid state for trip creation
        if (booking.getId() == null) {
            throw TripValidationException.bookingNotEligible("booking must be persisted before trip creation");
        }

        // Additional constraint: Check if driver already has maximum concurrent trips
        // NOTE: Only count ONGOING trips — completed trips must not count against the limit.
        long driverTripCount = tripRepository.countOngoingByDriverId(booking.getDriver().getId());
        if (driverTripCount >= getMaxConcurrentTripsPerDriver()) {
            throw TripValidationException.bookingNotEligible(
                String.format("driver has reached maximum concurrent trips (%d)", 
                            getMaxConcurrentTripsPerDriver()));
        }
        
        log.debug("One-to-one invariant validated for booking {}", booking.getId());
    }

    @Override
    public void enforceTripOwnership(Trip trip, User user) {
        log.debug("Enforcing trip ownership for trip {} and user {}", trip.getId(), user.getId());

        if (!canUserAccessTrip(trip, user)) {
            throw TripValidationException.unauthorizedAccess(trip.getId(), user.getId());
        }

        // Additional ownership validations
        Booking booking = trip.getBooking();
        if (booking == null) {
            throw TripValidationException.bookingNotEligible("trip has no associated booking");
        }

        // Verify booking-trip relationship integrity
        if (!booking.getId().equals(trip.getBooking().getId())) {
            throw TripValidationException.bookingNotEligible("trip booking relationship is corrupted");
        }

        log.debug("Trip ownership validated for trip {} and user {}", trip.getId(), user.getId());
    }

    @Override
    @Transactional(readOnly = true)
    public void enforceVehicleConsistency(Booking booking) {
        log.debug("Enforcing vehicle consistency for booking {}", booking.getId());

        Vehicle vehicle = booking.getVehicle();
        User driver = booking.getDriver();

        if (vehicle == null) {
            throw TripValidationException.bookingNotEligible("no vehicle assigned to booking");
        }

        if (driver == null) {
            throw TripValidationException.bookingNotEligible("no driver assigned to booking");
        }

        // Verify vehicle ownership by driver
        validateVehicleOwnership(vehicle, driver);

        // Check for conflicting vehicle usage
        validateVehicleAvailability(vehicle, booking);

        log.debug("Vehicle consistency validated for booking {} with vehicle {}", 
                 booking.getId(), vehicle.getId());
    }

    @Override
    public void enforceBookingEligibility(Booking booking) {
        log.debug("Enforcing booking eligibility for trip creation: {}", booking.getId());

        // Validate booking status
        if (!isEligibleBookingStatus(booking.getBookingStatus())) {
            throw TripValidationException.invalidBookingStatus(booking.getBookingStatus().name());
        }

        // Validate required relationships
        validateRequiredBookingRelationships(booking);

        // Ensure no existing trip
        enforceOneToOneTripBooking(booking);

        // Validate vehicle consistency
        enforceVehicleConsistency(booking);

        log.debug("Booking eligibility validated for booking {}", booking.getId());
    }

    @Override
    public void enforceTripStateConsistency(Trip trip) {
        log.debug("Enforcing trip state consistency for trip {}", trip.getId());

        // Validate trip has required fields
        if (trip.getBooking() == null) {
            throw TripValidationException.bookingNotEligible("trip has no associated booking");
        }

        if (trip.getTripStatus() == null) {
            throw TripValidationException.bookingNotEligible("trip has no status");
        }

        // Validate state relationships
        Booking booking = trip.getBooking();
        validateTripBookingStateConsistency(trip, booking);

        log.debug("Trip state consistency validated for trip {}", trip.getId());
    }

    @Override
    public boolean canUserAccessTrip(Trip trip, User user) {
        if (user == null || trip == null) {
            return false;
        }

        // Admin can access all trips
        if (isAdmin(user)) {
            return true;
        }

        Booking booking = trip.getBooking();
        if (booking == null) {
            return false;
        }

        // Driver can access their own trips
        if (booking.getDriver() != null && booking.getDriver().getId().equals(user.getId())) {
            return true;
        }

        // Shipper can access trips for their freight
        if (booking.getCargoRequest() != null && 
            booking.getCargoRequest().getShipper() != null &&
            booking.getCargoRequest().getShipper().getId().equals(user.getId())) {
            return true;
        }

        return false;
    }

    @Override
    @Transactional(readOnly = true)
    public void validateTripCreationEligibility(Booking booking, User driver) {
        log.debug("Validating trip creation eligibility for booking {} and driver {}", 
                 booking.getId(), driver.getId());

        // Comprehensive validation combining all invariants
        enforceBookingEligibility(booking);
        
        // Verify driver owns the booking
        if (!booking.getDriver().getId().equals(driver.getId())) {
            throw TripValidationException.unauthorizedAccess(booking.getId(), driver.getId());
        }

        // Additional driver-specific validations
        validateDriverEligibility(driver);

        log.info("Trip creation eligibility validated for booking {} and driver {}", 
                booking.getId(), driver.getId());
    }

    // ── Private Helper Methods ───────────────────────────────────────────────

    private void validateVehicleOwnership(Vehicle vehicle, User driver) {
        // Legacy path: driver personally owns the vehicle
        List<Vehicle> driverVehicles = vehicleRepository.findByUserId(driver.getId());
        List<Vehicle> fleetVehicles = driver.getFleetOwnerId() != null ?
            vehicleRepository.findByFleetOwnerId(driver.getFleetOwnerId()) : List.of();

        boolean hasVehicleAccess = driverVehicles.stream().anyMatch(v -> v.getId().equals(vehicle.getId())) ||
                                  fleetVehicles.stream().anyMatch(v -> v.getId().equals(vehicle.getId()));

        // Phase 7 fix: carrier dispatch path — the vehicle belongs to the carrier company,
        // not to the driver personally.  Accept it if the vehicle has a carrier_company_id set.
        // The DispatchService already enforced that driver + vehicle belong to the same company
        // before the booking was created, so this is safe to allow here.
        boolean isCarrierCompanyVehicle = vehicle.getCarrierCompanyId() != null;

        if (!hasVehicleAccess && !isCarrierCompanyVehicle) {
            throw TripValidationException.vehicleNotOwnedByDriver(vehicle.getId(), driver.getId());
        }
    }

    private void validateVehicleAvailability(Vehicle vehicle, Booking currentBooking) {
        // Check if vehicle is being used in other active bookings
        List<Booking> activeBookings = bookingRepository.findByCargoRequest_Id(currentBooking.getCargoRequest().getId());
        
        for (Booking booking : activeBookings) {
            if (!booking.getId().equals(currentBooking.getId()) && 
                booking.getVehicle() != null && 
                booking.getVehicle().getId().equals(vehicle.getId()) &&
                isActiveBookingStatus(booking.getBookingStatus())) {
                
                throw TripValidationException.bookingNotEligible(
                    String.format("vehicle %d is already in use by booking %d", 
                                vehicle.getId(), booking.getId()));
            }
        }

        // Check if vehicle has ongoing trips that would conflict
        List<Trip> ongoingTrips = tripRepository.findByDriverIdAndStatus(
            currentBooking.getDriver().getId(), Trip.TripStatus.ONGOING);
        
        for (Trip trip : ongoingTrips) {
            if (trip.getBooking().getVehicle() != null && 
                trip.getBooking().getVehicle().getId().equals(vehicle.getId())) {
                
                throw TripValidationException.bookingNotEligible(
                    String.format("vehicle %d is already in use by ongoing trip %d", 
                                vehicle.getId(), trip.getId()));
            }
        }
    }

    private void validateRequiredBookingRelationships(Booking booking) {
        if (booking.getCargoRequest() == null) {
            throw TripValidationException.bookingNotEligible("missing cargo request");
        }

        if (booking.getDriver() == null) {
            throw TripValidationException.bookingNotEligible("missing driver assignment");
        }

        if (booking.getVehicle() == null) {
            throw TripValidationException.bookingNotEligible("missing vehicle assignment");
        }
    }

    private void validateTripBookingStateConsistency(Trip trip, Booking booking) {
        // Ensure trip status is consistent with booking status
        if (trip.getTripStatus() == Trip.TripStatus.COMPLETED &&
            booking.getBookingStatus() != Booking.BookingStatus.DELIVERED &&
            booking.getBookingStatus() != Booking.BookingStatus.COMPLETED &&
            booking.getBookingStatus() != Booking.BookingStatus.CONFIRMED) {

            log.warn("validateTripBookingStateConsistency: trip {} COMPLETED but booking {} is {}",
                    trip.getId(), booking.getId(), booking.getBookingStatus());
            // Log but don't throw — allows async consistency
        }

        // Ensure active trips (ONGOING/ARRIVED/DELIVERY_PENDING_VERIFICATION) have
        // an appropriate booking status (ACCEPTED or DELIVERED)
        boolean tripIsActive = trip.getTripStatus() == Trip.TripStatus.ONGOING
                || trip.getTripStatus() == Trip.TripStatus.ARRIVED
                || trip.getTripStatus() == Trip.TripStatus.DELIVERY_PENDING_VERIFICATION;

        if (tripIsActive &&
            booking.getBookingStatus() != Booking.BookingStatus.ACCEPTED &&
            booking.getBookingStatus() != Booking.BookingStatus.DELIVERED) {

            log.warn("validateTripBookingStateConsistency: active trip {} with unexpected booking status {}",
                    trip.getId(), booking.getBookingStatus());
            // Log warning but don't throw — race condition window between status updates
        }
    }

    private void validateDriverEligibility(User driver) {
        if (driver.getRole() != User.Role.DRIVER && driver.getRole() != User.Role.FLEET_OWNER) {
            throw TripValidationException.bookingNotEligible("user is not a driver or fleet owner");
        }

        if (!Boolean.TRUE.equals(driver.getIsActive())) {
            throw TripValidationException.bookingNotEligible("driver account is not active");
        }

        // Additional driver validation could include:
        // - KYC verification status (if implemented)
        // - License validity (if implemented)
        // - Insurance status (if implemented)
    }

    private boolean isEligibleBookingStatus(Booking.BookingStatus status) {
        // Only accepted bookings can create trips
        return status == Booking.BookingStatus.ACCEPTED;
    }

    private boolean isActiveBookingStatus(Booking.BookingStatus status) {
        return status == Booking.BookingStatus.ACCEPTED || 
               status == Booking.BookingStatus.PENDING;
    }

    private boolean isAdmin(User user) {
        return user.getRole() == User.Role.ADMIN;
    }

    private int getMaxConcurrentTripsPerDriver() {
        // Business rule: Maximum concurrent trips per driver
        // This could be made configurable via application properties
        return 3;
    }
}