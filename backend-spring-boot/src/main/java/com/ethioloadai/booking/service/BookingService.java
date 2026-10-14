package com.ethioloadai.booking.service;

import com.ethioloadai.bid.entity.Bid;
import com.ethioloadai.booking.entity.Booking;
import com.ethioloadai.booking.dto.AdminUnpaidBookingResponse;
import com.ethioloadai.booking.dto.AdminUnpaidBookingsListResponse;
import com.ethioloadai.booking.dto.BookingResponse;
import com.ethioloadai.booking.dto.BookingTripResponse;
import com.ethioloadai.booking.dto.BookingPageResponse;
import com.ethioloadai.user.entity.User;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface BookingService {

    /**
     * Accept a bid and create a booking transactionally.
     * This is the CRITICAL concurrency-safe operation that fixes Laravel's race condition.
     * 
     * @param bidId ID of the bid to accept
     * @param shipper the shipper accepting the bid (for authorization)
     * @return created booking
     */
    Booking acceptBid(Long bidId, User shipper);

    /**
     * Accept a counter-offer and create a booking transactionally.
     * 
     * @param bidId ID of the bid with counter-offer to accept
     * @param actor the user accepting the counter-offer (shipper or driver)
     * @return created booking
     */
    Booking acceptCounterOffer(Long bidId, User actor);

    /**
     * Get booking by ID with authorization check.
     * 
     * @param bookingId booking ID
     * @param user requesting user
     * @return booking if authorized
     */
    Booking getBookingWithAuthorization(Long bookingId, User user);

    /**
     * Get a booking with authorization and return it as a DTO.
     * Only the booking's driver, freight shipper, or admin can access.
     */
    BookingResponse getBookingResponseWithAuthorization(Long bookingId, User user);

    /** Resolve only the trip associated with the specified authorized booking. */
    BookingTripResponse getBookingTrip(Long bookingId, User user);

    /** Return bookings visible to the authenticated caller. */
    List<BookingResponse> getBookingsForCaller(User user);

    /** Return a bounded page of bookings visible to the caller. */
    BookingPageResponse getBookingsPageForCaller(User user, Pageable pageable, Long adminDriverId);

    /**
     * Check if a freight already has an active booking.
     * 
     * @param freightId freight ID
     * @return true if freight has active booking
     */
    boolean hasActiveBooking(Long freightId);

    /**
     * Admin: retrieve all bookings with no payment record, excluding cancelled bookings.
     *
     * @param adminUser authenticated administrator
     * @return list of unpaid booking responses formatted for admin dashboard
     */
    AdminUnpaidBookingsListResponse getUnpaidBookings(User adminUser, Pageable pageable);
}