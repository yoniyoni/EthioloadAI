package com.ethioloadai.payment;

import com.ethioloadai.bid.service.CommissionServiceImpl;
import com.ethioloadai.booking.entity.Booking;
import com.ethioloadai.booking.entity.Booking.BookingStatus;
import com.ethioloadai.booking.repository.BookingRepository;
import com.ethioloadai.freight.entity.Freight;
import com.ethioloadai.payment.dto.ProcessPaymentRequest;
import com.ethioloadai.payment.entity.Payment;
import com.ethioloadai.payment.repository.PaymentRepository;
import com.ethioloadai.payment.service.PaymentServiceImpl;
import com.ethioloadai.user.entity.User;
import com.ethioloadai.user.entity.User.Role;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Phase 10 / Phase 13 — financial precision tests.
 *
 * Uses the REAL CommissionServiceImpl (no mock) to validate end-to-end
 * precision, rounding and arithmetic correctness.
 *
 * Invariant: amount = commissionAmount + driverNetAmount (within scale 2).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PaymentAmountPrecisionTest {

    @Mock private PaymentRepository  paymentRepository;
    @Mock private BookingRepository  bookingRepository;
    @Mock private EntityManager      entityManager;

    private PaymentServiceImpl service;
    private CommissionServiceImpl realCommission;

    @BeforeEach
    void setUp() {
        realCommission = new CommissionServiceImpl();
        service = new PaymentServiceImpl(paymentRepository, bookingRepository, realCommission, entityManager);

        when(paymentRepository.existsByBooking_Id(anyLong())).thenReturn(false);
        when(paymentRepository.save(any())).thenAnswer(inv -> {
            Payment p = inv.getArgument(0);
            if (p.getId() == null) {
                return Payment.builder()
                    .id(1L).booking(p.getBooking()).amount(p.getAmount())
                    .commissionAmount(p.getCommissionAmount()).driverNetAmount(p.getDriverNetAmount())
                    .paymentStatus(p.getPaymentStatus()).paymentMethod(p.getPaymentMethod())
                    .paidBy(p.getPaidBy()).build();
            }
            return p;
        });
    }

    @ParameterizedTest(name = "amount={0} fleet={1} expectedCommission={2}")
    @CsvSource({
        "1000.00, false, 100.00",
        "1000.00, true,   50.00",
        "1234.56, false, 123.46",   // 10% of 1234.56 = 123.456 → rounds to 123.46
        "1234.56, true,   61.73",   //  5% of 1234.56 = 61.728  → rounds to 61.73
        "33.33,   false,   3.33",   // 10% of 33.33 = 3.333 → HALF_UP = 3.33
        "33.35,   false,   3.34",   // 10% of 33.35 = 3.335 → HALF_UP = 3.34
        "0.01,    false,   0.00",   // Very small — commision rounds to 0
        "999.99,  false, 100.00",   // 10% = 99.999 → 100.00
    })
    void commission_precision_and_rounding(String amountStr, boolean fleet, String expectedCommStr) {
        BigDecimal amount = new BigDecimal(amountStr);
        Booking booking = buildBooking(amount, fleet);
        when(bookingRepository.findById(10L)).thenReturn(Optional.of(booking));

        User payer = buildShipper();
        var resp = service.initiatePayment(new ProcessPaymentRequest(10L, "cash", null), payer);

        BigDecimal expectedComm = new BigDecimal(expectedCommStr);
        assertThat(resp.getCommissionAmount())
            .as("commission for amount=%s fleet=%s", amountStr, fleet)
            .isEqualByComparingTo(expectedComm);

        // Invariant: amount = commission + driverNet
        BigDecimal reconstructed = resp.getCommissionAmount().add(resp.getDriverNetAmount());
        assertThat(reconstructed.setScale(2, RoundingMode.HALF_UP))
            .as("amount integrity: commission + driverNet must equal amount")
            .isEqualByComparingTo(resp.getAmount().setScale(2, RoundingMode.HALF_UP));
    }

    @Test
    void amount_has_scale_2() {
        Booking booking = buildBooking(new BigDecimal("500.1"), false); // Only 1dp
        when(bookingRepository.findById(10L)).thenReturn(Optional.of(booking));
        User payer = buildShipper();
        var resp = service.initiatePayment(new ProcessPaymentRequest(10L, "cash", null), payer);

        // Service normalises to scale 2
        assertThat(resp.getAmount().scale()).isEqualTo(2);
        assertThat(resp.getCommissionAmount().scale()).isEqualTo(2);
        assertThat(resp.getDriverNetAmount().scale()).isEqualTo(2);
    }

    @Test
    void no_floating_point_in_commission_path() {
        // 1/3 of common rounding-sensitive cases — ensures no float conversion
        BigDecimal amount = new BigDecimal("333.33");
        Booking booking = buildBooking(amount, false);
        when(bookingRepository.findById(10L)).thenReturn(Optional.of(booking));
        User payer = buildShipper();
        var resp = service.initiatePayment(new ProcessPaymentRequest(10L, "cash", null), payer);

        // 333.33 * 0.10 = 33.333 → HALF_UP → 33.33
        assertThat(resp.getCommissionAmount()).isEqualByComparingTo("33.33");
        assertThat(resp.getDriverNetAmount()).isEqualByComparingTo("300.00");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Booking buildBooking(BigDecimal estimatedPrice, boolean isFleet) {
        User driver = new User();
        driver.setId(2L);
        driver.setRole(Role.DRIVER);
        driver.setFleetOwnerId(isFleet ? 99L : null);

        Freight freight = new Freight();
        User sh = buildShipper();
        freight.setShipper(sh);

        Booking b = new Booking();
        b.setId(10L);
        b.setDriver(driver);
        b.setCargoRequest(freight);
        b.setBookingStatus(BookingStatus.DELIVERED);
        b.setEstimatedPrice(estimatedPrice);
        return b;
    }

    private User buildShipper() {
        User u = new User();
        u.setId(1L);
        u.setRole(Role.SHIPPER);
        Freight f = new Freight();
        f.setShipper(u);
        return u;
    }
}
