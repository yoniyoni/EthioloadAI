package com.ethioloadai.analytics;

import com.ethioloadai.booking.entity.Booking;
import com.ethioloadai.booking.repository.BookingRepository;
import com.ethioloadai.freight.entity.Freight;
import com.ethioloadai.freight.repository.FreightRepository;
import com.ethioloadai.payment.entity.Payment;
import com.ethioloadai.payment.repository.PaymentRepository;
import com.ethioloadai.user.entity.User;
import com.ethioloadai.user.repository.UserRepository;
import com.ethioloadai.vehicle.entity.Vehicle;
import com.ethioloadai.vehicle.repository.VehicleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class RevenueAnalyticsAggregationIntegrationTest {

    @Autowired private PaymentRepository paymentRepository;
    @Autowired private BookingRepository bookingRepository;
    @Autowired private FreightRepository freightRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private VehicleRepository vehicleRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    private User shipper;
    private User driver;
    private Freight freight;
    private Vehicle vehicle;

    @BeforeEach
    void setUp() {
        shipper = user(User.Role.SHIPPER);
        driver = user(User.Role.DRIVER);
        freight = freightRepository.saveAndFlush(Freight.builder()
                .shipper(shipper)
                .pickupLocation("Revenue test pickup")
                .destination("Revenue test destination")
                .materialType("General")
                .cargoDescription("Revenue test freight")
                .weightTons(BigDecimal.ONE)
                .budget(BigDecimal.valueOf(1000))
                .urgencyLevel("normal")
                .status(Freight.FreightStatus.POSTED)
                .build());
        vehicle = vehicleRepository.saveAndFlush(Vehicle.builder()
                .userId(driver.getId())
                .truckType("Revenue test truck")
                .plateNumber("REV-" + UUID.randomUUID().toString().substring(0, 8))
                .capacity(10.0)
                .build());
    }

    @Test
    void paymentsAreGroupedByCreationMonthAndFilteredByStatusAndHalfOpenYear() {
        LocalDateTime start = LocalDateTime.of(2025, 1, 1, 0, 0);
        LocalDateTime nextYear = LocalDateTime.of(2026, 1, 1, 0, 0);

        payment(Booking.BookingStatus.DELIVERED, Payment.PaymentStatus.PAID, "10.25",
                LocalDateTime.of(2025, 1, 1, 0, 0));
        payment(Booking.BookingStatus.DELIVERED, Payment.PaymentStatus.PAID, "5.75",
                LocalDateTime.of(2025, 1, 31, 23, 59));
        payment(Booking.BookingStatus.DELIVERED, Payment.PaymentStatus.PENDING, "100.00",
                LocalDateTime.of(2025, 1, 15, 12, 0));
        payment(Booking.BookingStatus.DELIVERED, Payment.PaymentStatus.PAID, "20.00",
                LocalDateTime.of(2025, 2, 5, 12, 0));
        payment(Booking.BookingStatus.DELIVERED, Payment.PaymentStatus.PAID, "30.00",
                start.minusDays(1));
        payment(Booking.BookingStatus.DELIVERED, Payment.PaymentStatus.PAID, "40.00",
                nextYear);

        List<PaymentRepository.MonthlyPaymentAggregate> result =
                paymentRepository.aggregateMonthlyPaidRevenue(Payment.PaymentStatus.PAID, start, nextYear);

        assertThat(result).hasSize(2);
        assertThat(result).extracting(PaymentRepository.MonthlyPaymentAggregate::getMonthNumber)
                .containsExactlyInAnyOrder(1, 2);
        assertThat(aggregateForPaymentMonth(result, 1).getAmount()).isEqualByComparingTo("16.00");
        assertThat(aggregateForPaymentMonth(result, 1).getPaymentCount()).isEqualTo(2L);
        assertThat(aggregateForPaymentMonth(result, 2).getAmount()).isEqualByComparingTo("20.00");
        assertThat(aggregateForPaymentMonth(result, 2).getPaymentCount()).isEqualTo(1L);

    }

    @Test
    void bookingCommissionsAreGroupedByCreationMonthAndOnlyEligibleStatuses() {
        LocalDateTime start = LocalDateTime.of(2025, 1, 1, 0, 0);
        LocalDateTime nextYear = LocalDateTime.of(2026, 1, 1, 0, 0);
        List<Booking.BookingStatus> eligibleStatuses =
                List.of(Booking.BookingStatus.CONFIRMED, Booking.BookingStatus.COMPLETED);

        booking(Booking.BookingStatus.CONFIRMED, "100.25", LocalDateTime.of(2025, 1, 3, 12, 0));
        booking(Booking.BookingStatus.COMPLETED, "50.75", LocalDateTime.of(2025, 1, 20, 12, 0));
        booking(Booking.BookingStatus.COMPLETED, "30.00", LocalDateTime.of(2025, 2, 1, 0, 0));
        booking(Booking.BookingStatus.PENDING, "1000.00", LocalDateTime.of(2025, 1, 15, 12, 0));
        booking(Booking.BookingStatus.CANCELLED, "2000.00", LocalDateTime.of(2025, 1, 15, 12, 0));
        booking(Booking.BookingStatus.ACCEPTED, "3000.00", LocalDateTime.of(2025, 1, 15, 12, 0));
        booking(Booking.BookingStatus.COMPLETED, "4000.00", start.minusDays(1));
        booking(Booking.BookingStatus.CONFIRMED, "5000.00", nextYear);

        List<BookingRepository.MonthlyBookingCommissionAggregate> result =
                bookingRepository.aggregateMonthlyBookingCommissions(eligibleStatuses, start, nextYear);

        assertThat(result).hasSize(2);
        assertThat(result).extracting(BookingRepository.MonthlyBookingCommissionAggregate::getMonthNumber)
                .containsExactlyInAnyOrder(1, 2);
        assertThat(aggregateForBookingMonth(result, 1).getCommissions()).isEqualByComparingTo("151.00");
        assertThat(aggregateForBookingMonth(result, 2).getCommissions()).isEqualByComparingTo("30.00");
    }

    private PaymentRepository.MonthlyPaymentAggregate aggregateForPaymentMonth(
            List<PaymentRepository.MonthlyPaymentAggregate> result, int month) {
        return result.stream().filter(row -> row.getMonthNumber() == month).findFirst().orElseThrow();
    }

    private BookingRepository.MonthlyBookingCommissionAggregate aggregateForBookingMonth(
            List<BookingRepository.MonthlyBookingCommissionAggregate> result, int month) {
        return result.stream().filter(row -> row.getMonthNumber() == month).findFirst().orElseThrow();
    }

    private Payment payment(
            Booking.BookingStatus bookingStatus,
            Payment.PaymentStatus paymentStatus,
            String amount,
            LocalDateTime createdAt) {
        Booking booking = booking(bookingStatus, "0.00", createdAt);
        Payment payment = paymentRepository.saveAndFlush(Payment.builder()
                .booking(booking)
                .amount(new BigDecimal(amount))
                .commissionAmount(BigDecimal.ZERO)
                .driverNetAmount(new BigDecimal(amount))
                .paymentMethod("cash")
                .paymentStatus(paymentStatus)
                .build());
        jdbcTemplate.update("UPDATE payments SET created_at = ? WHERE id = ?",
                Timestamp.valueOf(createdAt), payment.getId());
        return payment;
    }

    private Booking booking(Booking.BookingStatus status, String commission, LocalDateTime createdAt) {
        Booking booking = bookingRepository.saveAndFlush(Booking.builder()
                .cargoRequest(freight)
                .vehicle(vehicle)
                .driver(driver)
                .bookingStatus(status)
                .estimatedPrice(BigDecimal.valueOf(1000))
                .commissionFee(new BigDecimal(commission))
                .build());
        jdbcTemplate.update("UPDATE bookings SET created_at = ? WHERE id = ?",
                Timestamp.valueOf(createdAt), booking.getId());
        return booking;
    }

    private User user(User.Role role) {
        String suffix = UUID.randomUUID().toString();
        return userRepository.saveAndFlush(User.builder()
                .fullName("Revenue test " + role)
                .phone("+" + suffix.substring(0, 12))
                .password("test-password")
                .role(role)
                .verificationStatus(true)
                .isActive(true)
                .build());
    }
}
