package com.ethioloadai.trip.service;

import com.ethioloadai.booking.entity.Booking;
import com.ethioloadai.trip.entity.Trip;
import com.ethioloadai.user.entity.User;
import com.ethioloadai.vehicle.entity.Vehicle;

/**
 * Service to enforce critical business invariants for Trip/Booking relationships.
 * These invariants must never be violated to maintain data integrity.
 */
public interface TripInvariantService {

    /**
     * Enforce the one-to-one Trip/Booking invariant.
     * Validates that:
     * - Only one Trip can exist per Booking
     * - Trip creation is atomic and race-condition safe
     * - No orphaned relationships exist
     *
     * @param booking the booking to validate
     * @throws TripValidationException if invariant is violated
     */
    void enforceOneToOneTripBooking(Booking booking);

    /**
     * Enforce Trip ownership invariants.
     * Validates that:
     * - Trip belongs to the correct booking
     * - Booking belongs to the correct driver
     * - No unauthorized access patterns exist
     *
     * @param trip the trip to validate
     * @param user the user requesting access
     * @throws TripValidationException if ownership is invalid
     */
    void enforceTripOwnership(Trip trip, User user);

    /**
     * Enforce vehicle consistency invariants.
     * Validates that:
     * - Vehicle is owned by the booking's driver
     * - Vehicle is not being used by multiple concurrent bookings
     * - Vehicle availability status is consistent
     *
     * @param booking the booking with vehicle assignment
     * @throws TripValidationException if vehicle consistency is violated
     */
    void enforceVehicleConsistency(Booking booking);

    /**
     * Enforce booking eligibility invariants.
     * Validates that:
     * - Booking status allows trip creation
     * - All required relationships are present
     * - No conflicting trips exist
     *
     * @param booking the booking to validate for trip creation
     * @throws TripValidationException if booking is not eligible
     */
    void enforceBookingEligibility(Booking booking);

    /**
     * Enforce trip state consistency.
     * Validates that:
     * - Trip status is consistent with booking status
     * - No conflicting state transitions exist
     * - Related entities are in valid states
     *
     * @param trip the trip to validate
     * @throws TripValidationException if state is inconsistent
     */
    void enforceTripStateConsistency(Trip trip);

    /**
     * Validate that a user can access a specific trip.
     * Implements comprehensive authorization rules:
     * - Driver can access their own trips
     * - Shipper can access trips for their freight
     * - Admin can access all trips
     * - No other access is allowed
     *
     * @param trip the trip being accessed
     * @param user the user requesting access
     * @return true if access is allowed
     */
    boolean canUserAccessTrip(Trip trip, User user);

    /**
     * Validate that a driver can create a trip from a booking.
     * Checks all creation prerequisites:
     * - Driver owns the booking
     * - Vehicle belongs to driver
     * - No existing trip for booking
     * - All relationships are valid
     *
     * @param booking the booking for trip creation
     * @param driver the driver creating the trip
     * @throws TripValidationException if creation is not allowed
     */
    void validateTripCreationEligibility(Booking booking, User driver);
}