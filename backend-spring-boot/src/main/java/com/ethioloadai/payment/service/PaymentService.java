package com.ethioloadai.payment.service;

import com.ethioloadai.payment.dto.PaymentResponse;
import com.ethioloadai.payment.dto.ProcessPaymentRequest;
import com.ethioloadai.payment.entity.Payment;
import com.ethioloadai.user.entity.User;

import java.util.List;

/**
 * Domain service for the payment lifecycle.
 *
 * <p>All monetary amounts are computed server-side — no amount, commission, or
 * driverNetAmount value is ever accepted from client input.
 */
public interface PaymentService {

    /**
     * Create a PENDING payment for a DELIVERED booking.
     *
     * <p>The authoritative amount is read from {@code booking.estimatedPrice}.
     * Commission is calculated server-side. Client may supply only:
     * {@code bookingId}, cash as the supported method, and an optional descriptive
     * {@code transactionRef}. The reference is not verification evidence.
     *
     * <p>Idempotent: returns the existing payment if one already exists for the booking.
     *
     * @param request    client-supplied booking ID and payment method
     * @param callerUser authenticated user initiating the payment
     * @return the created (or existing) payment response
     */
    PaymentResponse initiatePayment(ProcessPaymentRequest request, User callerUser);

    /**
     * Manually confirm a PENDING cash payment as an administrator, marking it
     * PAID and the booking COMPLETED.
     *
     * <p>Uses pessimistic locking to prevent concurrent double-completion.
     * This operation does not verify an external provider transaction.
     *
     * @param paymentId  ID of the payment to complete
     * @param callerUser authenticated administrator confirming manual cash receipt
     * @return updated payment response
     */
    PaymentResponse completePayment(Long paymentId, User callerUser);

    /**
     * Record a payment failure (PENDING → FAILED).
     * Only permitted from PENDING; terminal states cannot be changed.
     *
     * @param paymentId  ID of the payment to fail
     * @param callerUser authenticated user or system actor
     * @return updated payment response
     */
    PaymentResponse failPayment(Long paymentId, User callerUser);

    /**
     * Retrieve a payment by its ID with IDOR protection.
     *
     * @param paymentId  ID of the payment
     * @param callerUser authenticated user requesting the payment
     * @return payment response
     */
    PaymentResponse getPaymentById(Long paymentId, User callerUser);

    /**
     * Retrieve the payment for a booking with IDOR protection.
     *
     * @param bookingId  booking ID
     * @param callerUser authenticated user requesting the payment
     * @return payment response
     */
    PaymentResponse getPaymentByBookingId(Long bookingId, User callerUser);

    /**
     * List all payments visible to the caller.
     * Admins see all; drivers see their own; shippers see payments for their freight.
     *
     * @param callerUser authenticated user
     * @return list of payment responses
     */
    List<PaymentResponse> getPaymentsForCaller(User callerUser);

    /**
     * Retrieve the payment for a freight shipment with IDOR protection.
     *
     * @param freightId  freight shipment ID
     * @param callerUser authenticated user requesting the payment
     * @return payment response
     */
    PaymentResponse getPaymentByFreightId(Long freightId, User callerUser);

    /**
     * Admin: record a cash payment directly on an unpaid booking.
     * Creates a payment row with method 'cash', status 'PAID', server-calculated commission,
     * updates the booking status to 'CONFIRMED', and prevents duplicates via pessimistic locking.
     *
     * @param bookingId ID of the unpaid booking
     * @param adminUser authenticated administrator
     * @return created payment response
     */
    PaymentResponse settleCashPayment(Long bookingId, User adminUser);
}
