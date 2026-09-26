package com.ethioloadai.booking.exception;

import com.ethioloadai.exception.BaseException;

public class BookingNotFoundException extends BaseException {

    public BookingNotFoundException(Long bookingId) {
        super("Booking not found with ID: " + bookingId, "BOOKING_NOT_FOUND", 404);
    }
}