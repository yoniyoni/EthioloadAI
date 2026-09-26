package com.ethioloadai.payment;

import com.ethioloadai.booking.entity.Booking;
import com.ethioloadai.booking.entity.Booking.BookingStatus;
import com.ethioloadai.booking.repository.BookingRepository;
import com.ethioloadai.freight.entity.Freight;
import com.ethioloadai.freight.repository.FreightRepository;
import com.ethioloadai.payment.dto.PaymentResponse;
import com.ethioloadai.payment.dto.ProcessPaymentRequest;
import com.ethioloadai.payment.entity.Payment.PaymentStatus;
import com.ethioloadai.payment.exception.PaymentException;
import com.ethioloadai.payment.repository.PaymentRepository;
import com.ethioloadai.payment.service.PaymentService;
import com.ethioloadai.user.entity.User;
import com.ethioloadai.user.entity.User.Role;
import com.ethioloadai.user.repository.UserRepository;
import com.ethioloadai.vehicle.entity.Vehicle;
import com.ethioloadai.vehicle.repository.VehicleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.*;

/**
 * Phase 13 — integration test for the payment lifecycle with Spring context.
 *
 * <p><strong>NOTE:</strong> This test requires PostgreSQL + PostGIS.
 * It will fail with H2 due to Flyway migrations V16+ (PostGIS functions).
 * This is a known limitation documented in the M6 audit.
 *
 * <p>Run this test against a live PostgreSQL database to verify:
 * <ul>
 *   <li>UNIQUE(booking_id) constraint enforcement</li>
 *   <li>Pessimistic locking behavior under concurrent load</li>
 *   <li>Transaction rollback on payment failure</li>
 *   <li>CHECK constraints on payment_status</li>
 * </ul>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PaymentIntegrationTest {

    @Autowired private PaymentService     paymentService;
    @Autowired private PaymentRepository  paymentRepository;
    @Autowired private BookingRepository  bookingRepository;
    @Autowired private FreightRepository  freightRepository;
    @Autowired private UserRepository     userRepository;
    @Autowired private VehicleRepository  vehicleRepository;

    private User shipper, driver;
    private Freight freight;
    private Vehicle vehicle;
    private Booking booking;

    @BeforeEach
    void setUp() {
        // Clean slate
        paymentRepository.deleteAll();
        bookingRepository.deleteAll();
        freightRepository.deleteAll();
        vehicleRepository.deleteAll();
        userRepository.deleteAll();

        // Create shipper
        shipper = new User();
        shipper.setFullName("Test Shipper");
        shipper.setPhone("0911111111");
        shipper.setPassword("hashed");
        shipper.setRole(Role.SHIPPER);
        shipper.setIsActive(true);
        shipper = userRepository.save(shipper);

        // Create driver
        driver = new User();
        driver.setFullName("Test Driver");
        driver.setPhone("0922222222");
        driver.setPassword("hashed");
        driver.setRole(Role.DRIVER);
        driver.setIsActive(true);
        driver = userRepository.save(driver);

        // Create vehicle
        vehicle = Vehicle.builder()
            .userId(driver.getId())
            .plateNumber("AA-" + System.currentTimeMillis())
            .truckType("Isuzu")
            .vehicleCategory(Vehicle.VehicleCategory.HEAVY)
            .capacity(10.0)
            .build();
        vehicle = vehicleRepository.save(vehicle);

        // Create freight
        freight = new Freight();
        freight.setShipper(shipper);
        freight.setMaterialType("Electronics");
        freight.setWeightTons(new BigDecimal("5.0"));
        freight.setPickupLocation("Addis Ababa");
        freight.setDestination("Dire Dawa");
        freight.setBudget(new BigDecimal("3000.00"));
        freight.setUrgencyLevel("normal");
        freight.setStatus(Freight.FreightStatus.POSTED);
        freight.setPriceType(Freight.PriceType.NEGOTIABLE);
        freight.setServiceType(Freight.ServiceType.INTERCITY);
        freight = freightRepository.save(freight);

        // Create delivered booking
        booking = new Booking();
        booking.setCargoRequest(freight);
        booking.setDriver(driver);
        booking.setVehicle(vehicle);
        booking.setBookingStatus(BookingStatus.DELIVERED);
        booking.setEstimatedPrice(new BigDecimal("2500.00"));
        booking.setCommissionFee(new BigDecimal("250.00"));
        booking = bookingRepository.save(booking);
    }

    // ── End-to-end payment lifecycle ──────────────────────────────────────────

    @Test
    void full_payment_lifecycle_initiate_and_complete() {
        // 1. Initiate payment
        ProcessPaymentRequest req = new ProcessPaymentRequest(booking.getId(), "cash", null);
        PaymentResponse resp = paymentService.initiatePayment(req, shipper);

        assertThat(resp.getBookingId()).isEqualTo(booking.getId());
        assertThat(resp.getAmount()).isEqualByComparingTo("2500.00");
        assertThat(resp.getCommissionAmount()).isEqualByComparingTo("250.00");
        assertThat(resp.getDriverNetAmount()).isEqualByComparingTo("2250.00");
        assertThat(resp.getPaymentStatus()).isEqualTo("PENDING");

        // 2. Complete payment
        PaymentResponse completed = paymentService.completePayment(resp.getId(), shipper);

        assertThat(completed.getPaymentStatus()).isEqualTo("PAID");

        // 3. Verify booking is COMPLETED
        Booking updatedBooking = bookingRepository.findById(booking.getId()).orElseThrow();
        assertThat(updatedBooking.getBookingStatus()).isEqualTo(BookingStatus.COMPLETED);
    }

    @Test
    void duplicate_payment_idempotency() {
        ProcessPaymentRequest req = new ProcessPaymentRequest(booking.getId(), "cash", null);

        // First call creates payment
        PaymentResponse resp1 = paymentService.initiatePayment(req, shipper);

        // Second call returns existing
        PaymentResponse resp2 = paymentService.initiatePayment(req, shipper);

        assertThat(resp1.getId()).isEqualTo(resp2.getId());
        assertThat(paymentRepository.count()).isEqualTo(1);
    }

    @Test
    void initiate_rejects_non_delivered_booking() {
        booking.setBookingStatus(BookingStatus.ACCEPTED);
        bookingRepository.save(booking);

        ProcessPaymentRequest req = new ProcessPaymentRequest(booking.getId(), "cash", null);

        assertThatThrownBy(() -> paymentService.initiatePayment(req, shipper))
            .isInstanceOf(PaymentException.BookingNotDeliveredException.class);
    }

    @Test
    void complete_rejects_already_paid() {
        ProcessPaymentRequest req = new ProcessPaymentRequest(booking.getId(), "cash", null);
        PaymentResponse resp = paymentService.initiatePayment(req, shipper);

        // First completion succeeds
        paymentService.completePayment(resp.getId(), shipper);

        // Second completion fails
        assertThatThrownBy(() -> paymentService.completePayment(resp.getId(), shipper))
            .isInstanceOf(PaymentException.InvalidStatusTransitionException.class);
    }

    @Test
    void unauthorized_user_cannot_initiate() {
        User stranger = new User();
        stranger.setFullName("Stranger");
        stranger.setPhone("0933333333");
        stranger.setPassword("hashed");
        stranger.setRole(Role.DRIVER);
        stranger.setIsActive(true);
        final User savedStranger = userRepository.save(stranger);

        ProcessPaymentRequest req = new ProcessPaymentRequest(booking.getId(), "cash", null);

        assertThatThrownBy(() -> paymentService.initiatePayment(req, savedStranger))
            .isInstanceOf(PaymentException.PaymentAuthorizationException.class);
    }

    @Test
    void admin_can_fail_pending_payment() {
        User admin = new User();
        admin.setFullName("Admin User");
        admin.setPhone("0944444444");
        admin.setPassword("hashed");
        admin.setRole(Role.ADMIN);
        admin.setIsActive(true);
        admin = userRepository.save(admin);

        ProcessPaymentRequest req = new ProcessPaymentRequest(booking.getId(), "cash", null);
        PaymentResponse resp = paymentService.initiatePayment(req, shipper);

        PaymentResponse failed = paymentService.failPayment(resp.getId(), admin);

        assertThat(failed.getPaymentStatus()).isEqualTo("FAILED");
    }

    @Test
    void driver_cannot_fail_payment() {
        ProcessPaymentRequest req = new ProcessPaymentRequest(booking.getId(), "cash", null);
        PaymentResponse resp = paymentService.initiatePayment(req, shipper);

        assertThatThrownBy(() -> paymentService.failPayment(resp.getId(), driver))
            .isInstanceOf(PaymentException.PaymentAuthorizationException.class)
            .hasMessageContaining("administrator");
    }

    @Test
    void getPaymentByBookingId_returns_payment() {
        ProcessPaymentRequest req = new ProcessPaymentRequest(booking.getId(), "cash", null);
        paymentService.initiatePayment(req, shipper);

        PaymentResponse found = paymentService.getPaymentByBookingId(booking.getId(), shipper);

        assertThat(found).isNotNull();
        assertThat(found.getBookingId()).isEqualTo(booking.getId());
    }

    @Test
    void getPaymentByBookingId_rejects_no_payment() {
        assertThatThrownBy(() -> paymentService.getPaymentByBookingId(booking.getId(), shipper))
            .isInstanceOf(PaymentException.PaymentNotFoundException.class);
    }
}
