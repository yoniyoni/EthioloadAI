package com.ethioloadai.payment.service;

import com.ethioloadai.bid.service.CommissionService;
import com.ethioloadai.booking.entity.Booking;
import com.ethioloadai.booking.repository.BookingRepository;
import com.ethioloadai.exception.AuthorizationException;
import com.ethioloadai.payment.dto.PaymentResponse;
import com.ethioloadai.payment.dto.ProcessPaymentRequest;
import com.ethioloadai.payment.entity.Payment;
import com.ethioloadai.payment.entity.Payment.PaymentStatus;
import com.ethioloadai.payment.exception.PaymentException;
import com.ethioloadai.payment.repository.PaymentRepository;
import com.ethioloadai.user.entity.User;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Transaction-safe implementation of the payment lifecycle.
 *
 * <h2>Security invariants</h2>
 * <ul>
 *   <li>The authoritative payable amount is always read from {@code booking.estimatedPrice}.</li>
 *   <li>Commission is always computed server-side from {@link CommissionService}.</li>
 *   <li>No client-supplied amount, commission, or status field is ever trusted.</li>
 *   <li>Every payment endpoint verifies the caller has a domain relationship to the booking.</li>
 * </ul>
 *
 * <h2>Idempotency</h2>
 * <ul>
 *   <li>A DB UNIQUE constraint on {@code booking_id} prevents duplicate rows even
 *       under concurrent requests.</li>
 *   <li>The service-layer {@link #initiatePayment} check means a second call with
 *       the same bookingId returns the existing record without error.</li>
 *   <li>{@link #completePayment} uses a pessimistic write lock to prevent concurrent
 *       double-completion of the same payment.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentServiceImpl.class);

    private final PaymentRepository    paymentRepository;
    private final BookingRepository    bookingRepository;
    private final CommissionService    commissionService;
    private final EntityManager        entityManager;

    // ── Initiate ──────────────────────────────────────────────────────────────

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public PaymentResponse initiatePayment(ProcessPaymentRequest request, User callerUser) {
        log.info("initiatePayment: bookingId={}, callerUserId={}", request.getBookingId(), callerUser.getId());

        // 1. Load and authorize the booking
        Booking booking = loadBookingWithAuthorization(request.getBookingId(), callerUser);

        // 2. Booking must be DELIVERED to allow payment initiation
        if (booking.getBookingStatus() != Booking.BookingStatus.DELIVERED) {
            throw new PaymentException.BookingNotDeliveredException(
                booking.getId(), booking.getBookingStatus().name());
        }

        // 3. Idempotency: return existing payment without error
        if (paymentRepository.existsByBooking_Id(booking.getId())) {
            log.info("initiatePayment: duplicate request for bookingId={}, returning existing payment",
                booking.getId());
            Payment existing = paymentRepository.findByBooking_Id(booking.getId())
                .orElseThrow(() -> new PaymentException.PaymentNotFoundException(
                    "Payment for booking " + booking.getId() + " disappeared unexpectedly"));
            return PaymentResponse.from(existing);
        }

        // 4. Compute amounts — server-authoritative, never from client
        BigDecimal amount = resolveAmount(booking);
        boolean isFleet   = isFleetBooking(booking);
        BigDecimal commissionAmount = commissionService.calculateCommission(amount, isFleet);
        BigDecimal driverNetAmount  = amount.subtract(commissionAmount).setScale(2, RoundingMode.HALF_UP);

        log.info("initiatePayment: bookingId={}, amount={}, commission={}, driverNet={}, fleet={}",
            booking.getId(), amount, commissionAmount, driverNetAmount, isFleet);

        // 5. Build payment record
        Payment payment = Payment.builder()
            .booking(booking)
            .amount(amount)
            .commissionAmount(commissionAmount)
            .driverNetAmount(driverNetAmount)
            .paidBy(callerUser)
            .paymentMethod(validatePaymentMethod(request.getPaymentMethod()))
            .paymentStatus(PaymentStatus.PENDING)
            .transactionRef(request.getTransactionRef())
            .build();

        // 6. Persist — DataIntegrityViolationException is the last-resort concurrency guard
        try {
            payment = paymentRepository.save(payment);
        } catch (DataIntegrityViolationException ex) {
            log.warn("initiatePayment: concurrent duplicate for bookingId={}, returning existing",
                booking.getId());
            return PaymentResponse.from(
                paymentRepository.findByBooking_Id(booking.getId())
                    .orElseThrow(() -> new PaymentException.DuplicatePaymentException(booking.getId())));
        }

        log.info("initiatePayment: created paymentId={} for bookingId={}", payment.getId(), booking.getId());
        return PaymentResponse.from(payment);
    }

    // ── Complete ──────────────────────────────────────────────────────────────

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public PaymentResponse completePayment(Long paymentId, User callerUser) {
        log.info("completePayment: paymentId={}, callerUserId={}", paymentId, callerUser.getId());

        // A client assertion or transaction reference is not settlement evidence.
        if (callerUser.getRole() != User.Role.ADMIN) {
            throw new PaymentException.PaymentAuthorizationException(
                "Only administrators can confirm manual cash payments");
        }

        // 1. Pessimistic lock on the payment row — prevents concurrent double-completion
        Payment payment = entityManager.find(Payment.class, paymentId, LockModeType.PESSIMISTIC_WRITE);
        if (payment == null) {
            throw new PaymentException.PaymentNotFoundException(paymentId);
        }

        // 2. No gateway is active; only a manual cash settlement can be confirmed here.
        if (!"cash".equals(payment.getPaymentMethod())) {
            throw new PaymentException.PaymentMethodUnavailableException(
                "Only manual cash payments can be confirmed until provider verification is implemented");
        }

        // 3. Validate state machine transition PENDING → PAID
        PaymentStateMachine.assertTransitionAllowed(payment.getPaymentStatus(), PaymentStatus.PAID);

        // 4. Re-verify server-authoritative amounts (guard against booking modification after payment creation)
        Booking booking = payment.getBooking();
        if (booking.getBookingStatus() == Booking.BookingStatus.CANCELLED) {
            throw new PaymentException.InvalidStatusTransitionException(
                PaymentStatus.PENDING, PaymentStatus.PAID);
        }
        BigDecimal expectedAmount = resolveAmount(booking);
        if (payment.getAmount().compareTo(expectedAmount) != 0) {
            log.error("completePayment: amount mismatch paymentId={}, stored={}, authoritative={}",
                paymentId, payment.getAmount(), expectedAmount);
            throw new PaymentException.InvalidPaymentAmountException(
                String.format("Payment amount %s does not match authoritative booking amount %s",
                    payment.getAmount(), expectedAmount));
        }

        // 5. Transition payment → PAID
        payment.setPaymentStatus(PaymentStatus.PAID);
        payment.setPaidBy(callerUser);
        paymentRepository.save(payment);

        // 6. Transition booking → COMPLETED (same transaction)
        booking.setBookingStatus(Booking.BookingStatus.COMPLETED);
        bookingRepository.save(booking);

        log.info("completePayment: paymentId={} PAID, bookingId={} COMPLETED", paymentId, booking.getId());
        return PaymentResponse.from(payment);
    }

    // ── Fail ─────────────────────────────────────────────────────────────────

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public PaymentResponse failPayment(Long paymentId, User callerUser) {
        log.info("failPayment: paymentId={}, callerUserId={}", paymentId, callerUser.getId());

        // Admin-only operation — drivers/shippers cannot force a FAILED state
        if (callerUser.getRole() != User.Role.ADMIN) {
            throw new PaymentException.PaymentAuthorizationException(
                "Only administrators can record payment failures");
        }

        Payment payment = entityManager.find(Payment.class, paymentId, LockModeType.PESSIMISTIC_WRITE);
        if (payment == null) {
            throw new PaymentException.PaymentNotFoundException(paymentId);
        }

        PaymentStateMachine.assertTransitionAllowed(payment.getPaymentStatus(), PaymentStatus.FAILED);

        payment.setPaymentStatus(PaymentStatus.FAILED);
        paymentRepository.save(payment);

        log.info("failPayment: paymentId={} marked FAILED", paymentId);
        return PaymentResponse.from(payment);
    }

    // ── Queries ───────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public PaymentResponse getPaymentById(Long paymentId, User callerUser) {
        Payment payment = paymentRepository.findById(paymentId)
            .orElseThrow(() -> new PaymentException.PaymentNotFoundException(paymentId));
        authorizePaymentAccess(payment, callerUser, "view");
        return PaymentResponse.from(payment);
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentResponse getPaymentByBookingId(Long bookingId, User callerUser) {
        // Load booking first for authorization
        Booking booking = bookingRepository.findById(bookingId)
            .orElseThrow(() -> new PaymentException.PaymentNotFoundException(
                "No booking found with id " + bookingId));
        authorizeBookingAccess(booking, callerUser, "view payment for");

        Payment payment = paymentRepository.findByBooking_Id(bookingId)
            .orElseThrow(() -> new PaymentException.PaymentNotFoundException(
                "No payment found for booking " + bookingId));
        return PaymentResponse.from(payment);
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentResponse getPaymentByFreightId(Long freightId, User callerUser) {
        if (freightId == null) {
            throw new PaymentException.PaymentNotFoundException("Freight ID must not be null");
        }
        List<Payment> payments = paymentRepository.findByFreightId(freightId);
        if (payments.isEmpty()) {
            throw new PaymentException.PaymentNotFoundException(
                "No payment found for freight " + freightId);
        }
        Payment payment = payments.get(0);
        authorizePaymentAccess(payment, callerUser, "view");
        return PaymentResponse.from(payment);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PaymentResponse> getPaymentsForCaller(User callerUser) {
        return switch (callerUser.getRole()) {
            case ADMIN -> paymentRepository.findAll()
                .stream().map(PaymentResponse::from).collect(Collectors.toList());

            case DRIVER -> paymentRepository.findByDriver(callerUser.getId())
                .stream().map(PaymentResponse::from).collect(Collectors.toList());

            case SHIPPER -> paymentRepository.findByShipper(callerUser.getId())
                .stream().map(PaymentResponse::from).collect(Collectors.toList());

            case FLEET_OWNER -> paymentRepository.findByFleetOwner(callerUser.getId())
                .stream().map(PaymentResponse::from).collect(Collectors.toList());
        };
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public PaymentResponse settleCashPayment(Long bookingId, User adminUser) {
        log.info("settleCashPayment: bookingId={}, adminUserId={}", bookingId, adminUser.getId());

        // 1. Authorization: admin only
        if (adminUser.getRole() != User.Role.ADMIN) {
            throw new PaymentException.PaymentAuthorizationException(
                "Only administrators can settle cash payments");
        }

        // 2. Pessimistic write lock on the booking row — prevents concurrent settlements
        Booking booking = entityManager.find(Booking.class, bookingId, LockModeType.PESSIMISTIC_WRITE);
        if (booking == null) {
            throw new PaymentException.PaymentNotFoundException("Booking not found: " + bookingId);
        }

        // 3. Reject if payment already exists (duplicate prevention)
        if (paymentRepository.existsByBooking_Id(bookingId)) {
            throw new PaymentException.DuplicatePaymentException(bookingId);
        }

        // 4. Reject if booking is cancelled
        if (booking.getBookingStatus() == Booking.BookingStatus.CANCELLED) {
            throw new PaymentException.InvalidStatusTransitionException(
                PaymentStatus.PENDING, PaymentStatus.PAID);
        }

        // 5. Authoritative amount from booking.estimatedPrice
        BigDecimal amount = resolveAmount(booking);
        boolean isFleet = isFleetBooking(booking);
        BigDecimal commissionAmount = commissionService.calculateCommission(amount, isFleet);
        BigDecimal driverNetAmount = amount.subtract(commissionAmount).setScale(2, RoundingMode.HALF_UP);

        // 6. Build and save Payment row
        Payment payment = Payment.builder()
            .booking(booking)
            .amount(amount)
            .commissionAmount(commissionAmount)
            .driverNetAmount(driverNetAmount)
            .paidBy(adminUser)
            .paymentMethod("cash")
            .paymentStatus(PaymentStatus.PAID)
            .transactionRef("CASH-SETTLED-" + booking.getId())
            .build();

        try {
            payment = paymentRepository.save(payment);
        } catch (DataIntegrityViolationException ex) {
            log.warn("settleCashPayment: concurrent duplicate detected for bookingId={}", bookingId);
            throw new PaymentException.DuplicatePaymentException(bookingId);
        }

        // 7. Update booking status to CONFIRMED (matching legacy Laravel behavior)
        booking.setBookingStatus(Booking.BookingStatus.CONFIRMED);
        bookingRepository.save(booking);

        log.info("settleCashPayment: paymentId={} recorded as PAID, bookingId={} confirmed",
            payment.getId(), booking.getId());

        return PaymentResponse.from(payment);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Reads the authoritative amount from booking.estimatedPrice.
     * Rejects null or non-positive amounts.
     */
    private BigDecimal resolveAmount(Booking booking) {
        BigDecimal amount = booking.getEstimatedPrice();
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new PaymentException.InvalidPaymentAmountException(
                "Booking " + booking.getId() + " has no valid estimated price: " + amount);
        }
        return amount.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Fleet booking: driver belongs to a fleet (driver.fleetOwnerId != null).
     * Consistent with how BookingServiceImpl.acceptCounterOffer() evaluates it.
     */
    private boolean isFleetBooking(Booking booking) {
        return booking.getDriver() != null
            && booking.getDriver().getFleetOwnerId() != null;
    }

    /**
     * Until a provider verification path exists, only manual cash requests are supported.
     */
    private String validatePaymentMethod(String raw) {
        if (raw == null || raw.isBlank()) return "cash";
        if ("cash".equals(raw.trim().toLowerCase(java.util.Locale.ROOT))) return "cash";
        throw new PaymentException.PaymentMethodUnavailableException(
            "Only manual cash payments are currently supported; external payment methods are not integrated");
    }

    /** Load booking and verify caller is authorized. */
    private Booking loadBookingWithAuthorization(Long bookingId, User callerUser) {
        Booking booking = bookingRepository.findById(bookingId)
            .orElseThrow(() -> new PaymentException.PaymentNotFoundException(
                "Booking not found: " + bookingId));
        authorizeBookingAccess(booking, callerUser, "initiate payment for");
        return booking;
    }

    /** Domain authorization: shipper, driver, or admin can access a booking's payment. */
    private void authorizeBookingAccess(Booking booking, User callerUser, String action) {
        if (callerUser.getRole() == User.Role.ADMIN) return;

        boolean isDriver  = booking.getDriver() != null
            && booking.getDriver().getId().equals(callerUser.getId());
        boolean isShipper = booking.getCargoRequest() != null
            && booking.getCargoRequest().getShipper() != null
            && booking.getCargoRequest().getShipper().getId().equals(callerUser.getId());
        boolean isFleetOwner = callerUser.getRole() == User.Role.FLEET_OWNER
            && booking.getDriver() != null
            && callerUser.getId().equals(booking.getDriver().getFleetOwnerId());

        if (!isDriver && !isShipper && !isFleetOwner) {
            throw new PaymentException.PaymentAuthorizationException(
                "You are not authorized to " + action + " booking " + booking.getId());
        }
    }

    /** Authorization against an existing Payment entity. */
    private void authorizePaymentAccess(Payment payment, User callerUser, String action) {
        authorizeBookingAccess(payment.getBooking(), callerUser, action + " payment for booking");
    }
}
