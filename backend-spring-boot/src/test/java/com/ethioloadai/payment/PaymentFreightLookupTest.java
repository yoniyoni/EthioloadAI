package com.ethioloadai.payment;

import com.ethioloadai.booking.entity.Booking;
import com.ethioloadai.freight.entity.Freight;
import com.ethioloadai.payment.controller.PaymentController;
import com.ethioloadai.payment.dto.PaymentResponse;
import com.ethioloadai.payment.entity.Payment;
import com.ethioloadai.payment.exception.PaymentException.PaymentAuthorizationException;
import com.ethioloadai.payment.exception.PaymentException.PaymentNotFoundException;
import com.ethioloadai.payment.repository.PaymentRepository;
import com.ethioloadai.payment.service.PaymentService;
import com.ethioloadai.payment.service.PaymentServiceImpl;
import com.ethioloadai.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Payment Freight Lookup")
class PaymentFreightLookupTest {

    @Mock
    private PaymentRepository paymentRepository;

    @InjectMocks
    private PaymentServiceImpl paymentService;

    private User shipper;
    private User driver;
    private User intruder;
    private User admin;
    private Freight freight;
    private Booking booking;
    private Payment payment;

    @BeforeEach
    void setUp() {
        shipper = User.builder()
                .id(1L)
                .fullName("Abebe Shipper")
                .role(User.Role.SHIPPER)
                .build();

        driver = User.builder()
                .id(2L)
                .fullName("Kebede Driver")
                .role(User.Role.DRIVER)
                .build();

        intruder = User.builder()
                .id(99L)
                .fullName("Intruder")
                .role(User.Role.SHIPPER)
                .build();

        admin = User.builder()
                .id(100L)
                .fullName("Admin")
                .role(User.Role.ADMIN)
                .build();

        freight = Freight.builder()
                .id(10L)
                .shipper(shipper)
                .matchedDriver(driver)
                .build();

        booking = Booking.builder()
                .id(20L)
                .cargoRequest(freight)
                .driver(driver)
                .bookingStatus(Booking.BookingStatus.DELIVERED)
                .estimatedPrice(new BigDecimal("28000.00"))
                .build();

        payment = Payment.builder()
                .id(50L)
                .booking(booking)
                .amount(new BigDecimal("28000.00"))
                .commissionAmount(new BigDecimal("2800.00"))
                .driverNetAmount(new BigDecimal("25200.00"))
                .paymentMethod("telebirr")
                .paymentStatus(Payment.PaymentStatus.PAID)
                .createdAt(LocalDateTime.now())
                .build();
    }

    @Nested
    @DisplayName("Service: getPaymentByFreightId")
    class ServiceTests {

        @Test
        @DisplayName("Shipper can retrieve payment for freight")
        void getPaymentByFreightId_byShipper_success() {
            when(paymentRepository.findByFreightId(10L)).thenReturn(List.of(payment));

            PaymentResponse response = paymentService.getPaymentByFreightId(10L, shipper);

            assertThat(response).isNotNull();
            assertThat(response.getId()).isEqualTo(50L);
            assertThat(response.getBookingId()).isEqualTo(20L);
            assertThat(response.getAmount()).isEqualByComparingTo("28000.00");
            assertThat(response.getPaymentStatus()).isEqualTo("PAID");
        }

        @Test
        @DisplayName("Driver can retrieve payment for freight")
        void getPaymentByFreightId_byDriver_success() {
            when(paymentRepository.findByFreightId(10L)).thenReturn(List.of(payment));

            PaymentResponse response = paymentService.getPaymentByFreightId(10L, driver);

            assertThat(response).isNotNull();
            assertThat(response.getId()).isEqualTo(50L);
        }

        @Test
        @DisplayName("Admin can retrieve payment for any freight")
        void getPaymentByFreightId_byAdmin_success() {
            when(paymentRepository.findByFreightId(10L)).thenReturn(List.of(payment));

            PaymentResponse response = paymentService.getPaymentByFreightId(10L, admin);

            assertThat(response).isNotNull();
            assertThat(response.getId()).isEqualTo(50L);
        }

        @Test
        @DisplayName("Third party is rejected with 403 Forbidden")
        void getPaymentByFreightId_byIntruder_throwsForbidden() {
            when(paymentRepository.findByFreightId(10L)).thenReturn(List.of(payment));

            assertThatThrownBy(() -> paymentService.getPaymentByFreightId(10L, intruder))
                    .isInstanceOf(PaymentAuthorizationException.class)
                    .hasMessageContaining("not authorized");
        }

        @Test
        @DisplayName("Freight without payments throws 404 Not Found")
        void getPaymentByFreightId_noPayment_throwsNotFound() {
            when(paymentRepository.findByFreightId(10L)).thenReturn(Collections.emptyList());

            assertThatThrownBy(() -> paymentService.getPaymentByFreightId(10L, shipper))
                    .isInstanceOf(PaymentNotFoundException.class)
                    .hasMessageContaining("No payment found for freight 10");
        }
    }

    @Nested
    @DisplayName("Controller: GET /api/payments/freight/{freightId}")
    class ControllerTests {

        @Mock
        private PaymentService mockPaymentService;

        @Test
        @DisplayName("Controller returns 200 OK with PaymentResponse")
        void controller_getPaymentByFreightId_returns200() {
            PaymentController controller = new PaymentController(mockPaymentService);
            PaymentResponse mockResponse = PaymentResponse.from(payment);
            when(mockPaymentService.getPaymentByFreightId(10L, shipper)).thenReturn(mockResponse);

            ResponseEntity<PaymentResponse> response = controller.getPaymentByFreightId(10L, shipper);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getAmount()).isEqualByComparingTo("28000.00");
            verify(mockPaymentService).getPaymentByFreightId(10L, shipper);
        }
    }
}
