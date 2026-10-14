package com.ethioloadai.booking;

import com.ethioloadai.bid.service.CommissionService;
import com.ethioloadai.booking.dto.AdminUnpaidBookingResponse;
import com.ethioloadai.booking.dto.AdminUnpaidBookingsListResponse;
import com.ethioloadai.booking.entity.Booking;
import com.ethioloadai.booking.entity.Booking.BookingStatus;
import com.ethioloadai.booking.repository.BookingRepository;
import com.ethioloadai.booking.service.BookingServiceImpl;
import com.ethioloadai.exception.AuthorizationException;
import com.ethioloadai.freight.entity.Freight;
import com.ethioloadai.payment.dto.PaymentResponse;
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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AdminBookingServiceTest {

    @Mock private PaymentRepository paymentRepository;
    @Mock private BookingRepository bookingRepository;
    @Mock private CommissionService commissionService;
    @Mock private EntityManager entityManager;

    private BookingServiceImpl bookingService;
    private PaymentServiceImpl paymentService;

    private User admin;
    private User regularUser;
    private User driver;
    private User shipper;
    private Freight freight;
    private Booking booking;

    @BeforeEach
    void setUp() {
        bookingService = new BookingServiceImpl(null, bookingRepository, null, commissionService);
        paymentService = new PaymentServiceImpl(paymentRepository, bookingRepository, commissionService, entityManager);

        admin = User.builder().id(99L).fullName("System Admin").role(Role.ADMIN).phone("+251911000000").build();
        regularUser = User.builder().id(2L).fullName("Driver User").role(Role.DRIVER).phone("+251922000000").build();
        shipper = User.builder().id(3L).fullName("Shipper One").role(Role.SHIPPER).phone("+251933000000").build();
        driver = User.builder().id(4L).fullName("Driver One").role(Role.DRIVER).phone("+251944000000").build();

        freight = Freight.builder()
                .id(10L)
                .shipper(shipper)
                .pickupLocation("Addis Ababa")
                .destination("Hawassa")
                .build();

        booking = Booking.builder()
                .id(100L)
                .cargoRequest(freight)
                .driver(driver)
                .bookingStatus(BookingStatus.ACCEPTED)
                .estimatedPrice(new BigDecimal("5000.00"))
                .commissionFee(new BigDecimal("500.00"))
                .createdAt(LocalDateTime.now())
                .build();
    }

    @Nested
    @DisplayName("getUnpaidBookings tests")
    class GetUnpaidBookingsTests {

        @Test
        @DisplayName("Non-admin caller is rejected")
        void nonAdmin_rejected() {
            assertThatThrownBy(() -> bookingService.getUnpaidBookings(regularUser, PageRequest.of(0, 20)))
                    .isInstanceOf(AuthorizationException.class)
                    .hasMessageContaining("Only administrators");
        }

        @Test
        @DisplayName("Admin caller retrieves mapped unpaid bookings with route and parties")
        void adminCaller_success() {
            PageRequest pageable = PageRequest.of(1, 10);
            when(bookingRepository.findUnpaidBookings(BookingStatus.CANCELLED, pageable))
                    .thenReturn(new PageImpl<>(List.of(booking), pageable, 25));

            AdminUnpaidBookingsListResponse response = bookingService.getUnpaidBookings(admin, pageable);

            assertThat(response.getBookings()).hasSize(1);
            assertThat(response.getTotal()).isEqualTo(25);
            assertThat(response.getPage()).isEqualTo(1);
            assertThat(response.getPageSize()).isEqualTo(10);
            assertThat(response.isHasNext()).isTrue();
            AdminUnpaidBookingResponse item = response.getBookings().get(0);
            assertThat(item.getId()).isEqualTo(100L);
            assertThat(item.getRoute()).isEqualTo("Addis Ababa → Hawassa");
            assertThat(item.getEstimatedPrice()).isEqualByComparingTo("5000.00");
            assertThat(item.getCommissionFee()).isEqualByComparingTo("500.00");
            assertThat(item.getBookingStatus()).isEqualTo("accepted");
            assertThat(item.getShipper().getName()).isEqualTo("Shipper One");
            assertThat(item.getShipper().getPhone()).isEqualTo("+251933000000");
            assertThat(item.getDriver().getName()).isEqualTo("Driver One");
            assertThat(item.getDriver().getPhone()).isEqualTo("+251944000000");
        }

        @Test
        @DisplayName("Handles null cargoRequest and parties gracefully")
        void nullParties_gracefulFallback() {
            Booking bareBooking = Booking.builder()
                    .id(200L)
                    .bookingStatus(BookingStatus.PENDING)
                    .estimatedPrice(new BigDecimal("3000.00"))
                    .commissionFee(BigDecimal.ZERO)
                    .createdAt(LocalDateTime.now())
                    .build();

            PageRequest pageable = PageRequest.of(0, 20);
            when(bookingRepository.findUnpaidBookings(BookingStatus.CANCELLED, pageable))
                    .thenReturn(new PageImpl<>(List.of(bareBooking), pageable, 1));

            AdminUnpaidBookingsListResponse response = bookingService.getUnpaidBookings(admin, pageable);

            assertThat(response.getBookings()).hasSize(1);
            AdminUnpaidBookingResponse item = response.getBookings().get(0);
            assertThat(item.getId()).isEqualTo(200L);
            assertThat(item.getRoute()).isEqualTo("Booking #200");
            assertThat(item.getShipper().getName()).isEqualTo("Unknown");
            assertThat(item.getDriver().getName()).isEqualTo("Unknown");
        }
    }

    @Nested
    @DisplayName("settleCashPayment tests")
    class SettleCashPaymentTests {

        @Test
        @DisplayName("Non-admin caller is rejected")
        void nonAdmin_rejected() {
            assertThatThrownBy(() -> paymentService.settleCashPayment(100L, regularUser))
                    .isInstanceOf(PaymentException.PaymentAuthorizationException.class)
                    .hasMessageContaining("Only administrators");
        }

        @Test
        @DisplayName("Missing booking throws PaymentNotFoundException")
        void missingBooking_throwsNotFound() {
            when(entityManager.find(eq(Booking.class), eq(999L), any(LockModeType.class))).thenReturn(null);

            assertThatThrownBy(() -> paymentService.settleCashPayment(999L, admin))
                    .isInstanceOf(PaymentException.PaymentNotFoundException.class)
                    .hasMessageContaining("Booking not found");
        }

        @Test
        @DisplayName("Duplicate payment throws DuplicatePaymentException")
        void duplicatePayment_throwsDuplicate() {
            when(entityManager.find(eq(Booking.class), eq(100L), any(LockModeType.class))).thenReturn(booking);
            when(paymentRepository.existsByBooking_Id(100L)).thenReturn(true);

            assertThatThrownBy(() -> paymentService.settleCashPayment(100L, admin))
                    .isInstanceOf(PaymentException.DuplicatePaymentException.class)
                    .hasMessageContaining("Payment already exists");
        }

        @Test
        @DisplayName("Cancelled booking throws InvalidStatusTransitionException")
        void cancelledBooking_rejected() {
            booking.setBookingStatus(BookingStatus.CANCELLED);
            when(entityManager.find(eq(Booking.class), eq(100L), any(LockModeType.class))).thenReturn(booking);
            when(paymentRepository.existsByBooking_Id(100L)).thenReturn(false);

            assertThatThrownBy(() -> paymentService.settleCashPayment(100L, admin))
                    .isInstanceOf(PaymentException.InvalidStatusTransitionException.class);
        }

        @Test
        @DisplayName("Successful cash settlement creates payment and confirms booking")
        void successfulCashSettlement() {
            when(entityManager.find(eq(Booking.class), eq(100L), any(LockModeType.class))).thenReturn(booking);
            when(paymentRepository.existsByBooking_Id(100L)).thenReturn(false);
            when(commissionService.calculateCommission(new BigDecimal("5000.00"), false))
                    .thenReturn(new BigDecimal("500.00"));

            when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> {
                Payment p = inv.getArgument(0);
                p.setId(501L);
                return p;
            });

            PaymentResponse response = paymentService.settleCashPayment(100L, admin);

            assertThat(response).isNotNull();
            assertThat(response.getId()).isEqualTo(501L);
            assertThat(response.getAmount()).isEqualByComparingTo("5000.00");
            assertThat(response.getCommissionAmount()).isEqualByComparingTo("500.00");
            assertThat(response.getDriverNetAmount()).isEqualByComparingTo("4500.00");
            assertThat(response.getPaymentMethod()).isEqualTo("cash");
            assertThat(response.getPaymentStatus()).isEqualTo("PAID");

            // Verify payment row attributes
            ArgumentCaptor<Payment> paymentCaptor = ArgumentCaptor.forClass(Payment.class);
            verify(paymentRepository).save(paymentCaptor.capture());
            Payment savedPayment = paymentCaptor.getValue();
            assertThat(savedPayment.getPaymentMethod()).isEqualTo("cash");
            assertThat(savedPayment.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
            assertThat(savedPayment.getPaidBy()).isEqualTo(admin);
            assertThat(savedPayment.getTransactionRef()).isEqualTo("CASH-SETTLED-100");

            // Verify booking updated to CONFIRMED
            ArgumentCaptor<Booking> bookingCaptor = ArgumentCaptor.forClass(Booking.class);
            verify(bookingRepository).save(bookingCaptor.capture());
            Booking savedBooking = bookingCaptor.getValue();
            assertThat(savedBooking.getBookingStatus()).isEqualTo(BookingStatus.CONFIRMED);
        }
    }
}
