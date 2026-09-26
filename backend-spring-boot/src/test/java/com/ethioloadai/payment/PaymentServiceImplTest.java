package com.ethioloadai.payment;

import com.ethioloadai.bid.service.CommissionService;
import com.ethioloadai.booking.entity.Booking;
import com.ethioloadai.booking.entity.Booking.BookingStatus;
import com.ethioloadai.booking.repository.BookingRepository;
import com.ethioloadai.freight.entity.Freight;
import com.ethioloadai.payment.dto.PaymentResponse;
import com.ethioloadai.payment.dto.ProcessPaymentRequest;
import com.ethioloadai.payment.entity.Payment;
import com.ethioloadai.payment.entity.Payment.PaymentStatus;
import com.ethioloadai.payment.exception.PaymentException;
import com.ethioloadai.payment.repository.PaymentRepository;
import com.ethioloadai.payment.service.PaymentServiceImpl;
import com.ethioloadai.user.entity.User;
import com.ethioloadai.user.entity.User.Role;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for PaymentServiceImpl covering:
 * - Amount derivation (server-authoritative)
 * - Commission calculation delegation
 * - Idempotency / duplicate prevention
 * - State machine enforcement
 * - Authorization / IDOR
 * - Booking status prerequisite
 * - Client-controlled field rejection
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PaymentServiceImplTest {

    @Mock private PaymentRepository    paymentRepository;
    @Mock private BookingRepository    bookingRepository;
    @Mock private CommissionService    commissionService;
    @Mock private EntityManager        entityManager;

    private PaymentServiceImpl service;

    // Test data
    private User   shipper, driver, fleetDriver, admin;
    private Booking booking;
    private Payment pendingPayment;

    @BeforeEach
    void setUp() {
        service = new PaymentServiceImpl(paymentRepository, bookingRepository, commissionService, entityManager);

        shipper     = buildUser(1L, Role.SHIPPER,     null);
        driver      = buildUser(2L, Role.DRIVER,      null);
        fleetDriver = buildUser(3L, Role.DRIVER,      99L);   // belongs to fleet owner 99
        admin       = buildUser(4L, Role.ADMIN,       null);

        Freight freight = new Freight();
        freight.setShipper(shipper);

        booking = new Booking();
        booking.setId(10L);
        booking.setDriver(driver);
        booking.setCargoRequest(freight);
        booking.setBookingStatus(BookingStatus.DELIVERED);
        booking.setEstimatedPrice(new BigDecimal("1000.00"));
        booking.setCommissionFee(new BigDecimal("100.00"));

        pendingPayment = Payment.builder()
            .id(100L)
            .booking(booking)
            .amount(new BigDecimal("1000.00"))
            .commissionAmount(new BigDecimal("100.00"))
            .driverNetAmount(new BigDecimal("900.00"))
            .paymentStatus(PaymentStatus.PENDING)
            .paymentMethod("cash")
            .build();

        // Common stubs
        when(bookingRepository.findById(10L)).thenReturn(Optional.of(booking));
        when(commissionService.calculateCommission(new BigDecimal("1000.00"), false))
            .thenReturn(new BigDecimal("100.00"));
        when(paymentRepository.existsByBooking_Id(10L)).thenReturn(false);
        when(paymentRepository.save(any())).thenAnswer(inv -> {
            Payment p = inv.getArgument(0);
            if (p.getId() == null) p = Payment.builder()
                .id(100L).booking(p.getBooking()).amount(p.getAmount())
                .commissionAmount(p.getCommissionAmount()).driverNetAmount(p.getDriverNetAmount())
                .paymentStatus(p.getPaymentStatus()).paymentMethod(p.getPaymentMethod())
                .paidBy(p.getPaidBy()).transactionRef(p.getTransactionRef()).build();
            return p;
        });
    }

    // ── Amount derivation ─────────────────────────────────────────────────────

    @Test
    void initiatePayment_uses_server_authoritative_amount() {
        ProcessPaymentRequest req = new ProcessPaymentRequest(10L, "cash", null);

        PaymentResponse resp = service.initiatePayment(req, shipper);

        // Amount must equal booking.estimatedPrice, not any client value
        assertThat(resp.getAmount()).isEqualByComparingTo("1000.00");
    }

    @Test
    void initiatePayment_commission_is_server_calculated() {
        ProcessPaymentRequest req = new ProcessPaymentRequest(10L, "cash", null);

        service.initiatePayment(req, shipper);

        verify(commissionService).calculateCommission(new BigDecimal("1000.00"), false);
    }

    @Test
    void initiatePayment_driverNet_equals_amount_minus_commission() {
        when(commissionService.calculateCommission(any(), anyBoolean()))
            .thenReturn(new BigDecimal("100.00"));

        ProcessPaymentRequest req = new ProcessPaymentRequest(10L, "cash", null);
        PaymentResponse resp = service.initiatePayment(req, shipper);

        assertThat(resp.getDriverNetAmount()).isEqualByComparingTo("900.00");
    }

    @Test
    void initiatePayment_fleet_commission_is_5pct() {
        booking.setDriver(fleetDriver);
        when(commissionService.calculateCommission(new BigDecimal("1000.00"), true))
            .thenReturn(new BigDecimal("50.00"));

        ProcessPaymentRequest req = new ProcessPaymentRequest(10L, "cash", null);
        service.initiatePayment(req, fleetDriver);

        verify(commissionService).calculateCommission(new BigDecimal("1000.00"), true);
    }

    // ── Booking prerequisite ──────────────────────────────────────────────────

    @Test
    void initiatePayment_rejects_non_delivered_booking() {
        booking.setBookingStatus(BookingStatus.ACCEPTED);

        assertThatThrownBy(() ->
            service.initiatePayment(new ProcessPaymentRequest(10L, "cash", null), shipper))
            .isInstanceOf(PaymentException.BookingNotDeliveredException.class)
            .hasMessageContaining("DELIVERED")
            .hasMessageContaining("ACCEPTED");
    }

    @Test
    void initiatePayment_rejects_completed_booking() {
        booking.setBookingStatus(BookingStatus.COMPLETED);

        assertThatThrownBy(() ->
            service.initiatePayment(new ProcessPaymentRequest(10L, "cash", null), shipper))
            .isInstanceOf(PaymentException.BookingNotDeliveredException.class);
    }

    @Test
    void initiatePayment_rejects_cancelled_booking() {
        booking.setBookingStatus(BookingStatus.CANCELLED);

        assertThatThrownBy(() ->
            service.initiatePayment(new ProcessPaymentRequest(10L, "cash", null), shipper))
            .isInstanceOf(PaymentException.BookingNotDeliveredException.class);
    }

    // ── Amount validation ─────────────────────────────────────────────────────

    @Test
    void initiatePayment_rejects_null_amount() {
        booking.setEstimatedPrice(null);

        assertThatThrownBy(() ->
            service.initiatePayment(new ProcessPaymentRequest(10L, "cash", null), shipper))
            .isInstanceOf(PaymentException.InvalidPaymentAmountException.class);
    }

    @Test
    void initiatePayment_rejects_zero_amount() {
        booking.setEstimatedPrice(BigDecimal.ZERO);

        assertThatThrownBy(() ->
            service.initiatePayment(new ProcessPaymentRequest(10L, "cash", null), shipper))
            .isInstanceOf(PaymentException.InvalidPaymentAmountException.class);
    }

    @Test
    void initiatePayment_rejects_negative_amount() {
        booking.setEstimatedPrice(new BigDecimal("-500.00"));

        assertThatThrownBy(() ->
            service.initiatePayment(new ProcessPaymentRequest(10L, "cash", null), shipper))
            .isInstanceOf(PaymentException.InvalidPaymentAmountException.class);
    }

    // ── Idempotency ───────────────────────────────────────────────────────────

    @Test
    void initiatePayment_returns_existing_on_duplicate() {
        when(paymentRepository.existsByBooking_Id(10L)).thenReturn(true);
        when(paymentRepository.findByBooking_Id(10L)).thenReturn(Optional.of(pendingPayment));

        PaymentResponse resp = service.initiatePayment(new ProcessPaymentRequest(10L, "cash", null), shipper);

        assertThat(resp.getId()).isEqualTo(100L);
        // No new payment should be saved
        verify(paymentRepository, never()).save(any());
    }

    // ── Authorization / IDOR ──────────────────────────────────────────────────

    @Test
    void initiatePayment_rejects_unrelated_user() {
        User stranger = buildUser(99L, Role.SHIPPER, null);

        assertThatThrownBy(() ->
            service.initiatePayment(new ProcessPaymentRequest(10L, "cash", null), stranger))
            .isInstanceOf(PaymentException.PaymentAuthorizationException.class);
    }

    @Test
    void initiatePayment_driver_can_initiate() {
        ProcessPaymentRequest req = new ProcessPaymentRequest(10L, "cash", null);
        assertThatNoException().isThrownBy(() -> service.initiatePayment(req, driver));
    }

    @Test
    void initiatePayment_admin_can_initiate() {
        ProcessPaymentRequest req = new ProcessPaymentRequest(10L, "cash", null);
        assertThatNoException().isThrownBy(() -> service.initiatePayment(req, admin));
    }

    @Test
    void getPaymentById_rejects_unrelated_user() {
        when(paymentRepository.findById(100L)).thenReturn(Optional.of(pendingPayment));
        User stranger = buildUser(99L, Role.SHIPPER, null);

        assertThatThrownBy(() -> service.getPaymentById(100L, stranger))
            .isInstanceOf(PaymentException.PaymentAuthorizationException.class);
    }

    @Test
    void getPaymentById_rejects_unknown_id() {
        when(paymentRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getPaymentById(999L, admin))
            .isInstanceOf(PaymentException.PaymentNotFoundException.class);
    }

    @Test
    void getPaymentByBookingId_rejects_wrong_booking() {
        User stranger = buildUser(55L, Role.SHIPPER, null);

        assertThatThrownBy(() -> service.getPaymentByBookingId(10L, stranger))
            .isInstanceOf(PaymentException.PaymentAuthorizationException.class);
    }

    // ── Complete payment ──────────────────────────────────────────────────────

    @Test
    void completePayment_transitions_pending_to_paid() {
        when(entityManager.find(eq(Payment.class), eq(100L), eq(LockModeType.PESSIMISTIC_WRITE)))
            .thenReturn(pendingPayment);
        when(bookingRepository.save(any())).thenReturn(booking);

        PaymentResponse resp = service.completePayment(100L, shipper);

        assertThat(resp.getPaymentStatus()).isEqualTo("PAID");
        assertThat(booking.getBookingStatus()).isEqualTo(BookingStatus.COMPLETED);
    }

    @Test
    void completePayment_rejects_paid_to_paid() {
        pendingPayment.setPaymentStatus(PaymentStatus.PAID);
        when(entityManager.find(eq(Payment.class), eq(100L), eq(LockModeType.PESSIMISTIC_WRITE)))
            .thenReturn(pendingPayment);

        assertThatThrownBy(() -> service.completePayment(100L, shipper))
            .isInstanceOf(PaymentException.InvalidStatusTransitionException.class);
    }

    @Test
    void completePayment_rejects_failed_to_paid() {
        pendingPayment.setPaymentStatus(PaymentStatus.FAILED);
        when(entityManager.find(eq(Payment.class), eq(100L), eq(LockModeType.PESSIMISTIC_WRITE)))
            .thenReturn(pendingPayment);

        assertThatThrownBy(() -> service.completePayment(100L, admin))
            .isInstanceOf(PaymentException.InvalidStatusTransitionException.class);
    }

    @Test
    void completePayment_rejects_amount_mismatch() {
        // Simulate booking amount changed after payment was created
        booking.setEstimatedPrice(new BigDecimal("2000.00"));
        when(entityManager.find(eq(Payment.class), eq(100L), eq(LockModeType.PESSIMISTIC_WRITE)))
            .thenReturn(pendingPayment);

        assertThatThrownBy(() -> service.completePayment(100L, shipper))
            .isInstanceOf(PaymentException.InvalidPaymentAmountException.class)
            .hasMessageContaining("authoritative");
    }

    @Test
    void completePayment_rejects_unauthorized_user() {
        when(entityManager.find(eq(Payment.class), eq(100L), eq(LockModeType.PESSIMISTIC_WRITE)))
            .thenReturn(pendingPayment);
        User stranger = buildUser(55L, Role.SHIPPER, null);

        assertThatThrownBy(() -> service.completePayment(100L, stranger))
            .isInstanceOf(PaymentException.PaymentAuthorizationException.class);
    }

    @Test
    void completePayment_rejects_unknown_payment() {
        when(entityManager.find(eq(Payment.class), eq(999L), eq(LockModeType.PESSIMISTIC_WRITE))).thenReturn(null);

        assertThatThrownBy(() -> service.completePayment(999L, admin))
            .isInstanceOf(PaymentException.PaymentNotFoundException.class);
    }

    // ── Fail payment ──────────────────────────────────────────────────────────

    @Test
    void failPayment_admin_can_fail_pending() {
        when(entityManager.find(eq(Payment.class), eq(100L), eq(LockModeType.PESSIMISTIC_WRITE)))
            .thenReturn(pendingPayment);

        PaymentResponse resp = service.failPayment(100L, admin);
        assertThat(resp.getPaymentStatus()).isEqualTo("FAILED");
    }

    @Test
    void failPayment_non_admin_rejected() {
        assertThatThrownBy(() -> service.failPayment(100L, shipper))
            .isInstanceOf(PaymentException.PaymentAuthorizationException.class)
            .hasMessageContaining("administrator");
    }

    @Test
    void failPayment_cannot_fail_paid() {
        pendingPayment.setPaymentStatus(PaymentStatus.PAID);
        when(entityManager.find(eq(Payment.class), eq(100L), eq(LockModeType.PESSIMISTIC_WRITE)))
            .thenReturn(pendingPayment);

        assertThatThrownBy(() -> service.failPayment(100L, admin))
            .isInstanceOf(PaymentException.InvalidStatusTransitionException.class);
    }

    // ── Client-controlled field rejection ─────────────────────────────────────

    @Test
    void initiatePayment_ignores_unknown_payment_method_defaults_to_cash() {
        when(commissionService.calculateCommission(any(), anyBoolean()))
            .thenReturn(new BigDecimal("100.00"));
        ProcessPaymentRequest req = new ProcessPaymentRequest(10L, "bitcoin", null);

        PaymentResponse resp = service.initiatePayment(req, shipper);

        assertThat(resp.getPaymentMethod()).isEqualTo("cash");
    }

    // ── Fleet owner authorization ─────────────────────────────────────────────

    @Test
    void initiatePayment_fleet_owner_can_access_fleet_driver_booking() {
        booking.setDriver(fleetDriver);
        User fleetOwner = buildUser(99L, Role.FLEET_OWNER, null);
        when(commissionService.calculateCommission(any(), anyBoolean()))
            .thenReturn(new BigDecimal("50.00"));

        assertThatNoException().isThrownBy(() ->
            service.initiatePayment(new ProcessPaymentRequest(10L, "cash", null), fleetOwner));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private User buildUser(Long id, Role role, Long fleetOwnerId) {
        User u = new User();
        u.setId(id);
        u.setRole(role);
        u.setFullName("User-" + id);
        u.setPhone("090000000" + id);
        u.setIsActive(true);
        if (fleetOwnerId != null) u.setFleetOwnerId(fleetOwnerId);
        return u;
    }
}
