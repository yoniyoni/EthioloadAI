package com.ethioloadai.payment.exception;

import com.ethioloadai.payment.entity.Payment;

/**
 * Domain exceptions for the payment lifecycle.
 * All subclasses are unchecked so callers are not forced to declare them.
 */
public class PaymentException extends RuntimeException {

    public PaymentException(String message) {
        super(message);
    }

    // ── Concrete subtypes ─────────────────────────────────────────────────────

    /** Thrown when an operation requires booking status DELIVERED but the booking is in another state. */
    public static class BookingNotDeliveredException extends PaymentException {
        public BookingNotDeliveredException(Long bookingId, String actualStatus) {
            super(String.format(
                "Booking %d must be in DELIVERED status to process payment, but was: %s",
                bookingId, actualStatus));
        }
    }

    /** Thrown when a payment already exists for a booking (duplicate attempt). */
    public static class DuplicatePaymentException extends PaymentException {
        public DuplicatePaymentException(Long bookingId) {
            super("Payment already exists for booking " + bookingId);
        }
    }

    /** Thrown when a state transition is not permitted by the payment state machine. */
    public static class InvalidStatusTransitionException extends PaymentException {
        public InvalidStatusTransitionException(Payment.PaymentStatus from, Payment.PaymentStatus to) {
            super(String.format(
                "Payment status transition %s → %s is not permitted", from, to));
        }
    }

    /** Thrown when the payment is not found or access is denied. */
    public static class PaymentNotFoundException extends PaymentException {
        public PaymentNotFoundException(Long paymentId) {
            super("Payment not found: " + paymentId);
        }
        public PaymentNotFoundException(String message) {
            super(message);
        }
    }

    /** Thrown when the caller is not authorized to access a payment resource. */
    public static class PaymentAuthorizationException extends PaymentException {
        public PaymentAuthorizationException(String message) {
            super(message);
        }
    }

    /** Thrown when a payment amount is zero, negative, or otherwise invalid. */
    public static class InvalidPaymentAmountException extends PaymentException {
        public InvalidPaymentAmountException(String message) {
            super(message);
        }
    }
}
