package com.ethioloadai.booking;

import com.ethioloadai.booking.controller.BookingController;
import com.ethioloadai.booking.dto.AdminUnpaidBookingResponse;
import com.ethioloadai.booking.dto.AdminUnpaidBookingsListResponse;
import com.ethioloadai.booking.dto.CashPaymentSettlementResponse;
import com.ethioloadai.booking.service.BookingService;
import com.ethioloadai.payment.dto.PaymentResponse;
import com.ethioloadai.payment.service.PaymentService;
import com.ethioloadai.user.entity.User;
import com.ethioloadai.user.entity.User.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.data.domain.PageRequest;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookingControllerTest {

    @Mock private BookingService bookingService;
    @Mock private PaymentService paymentService;

    @InjectMocks
    private BookingController controller;

    private User admin;

    @BeforeEach
    void setUp() {
        admin = User.builder().id(1L).role(Role.ADMIN).fullName("Admin User").build();
    }

    @Test
    @DisplayName("GET /unpaid returns HTTP 200 with paginated list envelope")
    void getUnpaidBookings_success() {
        AdminUnpaidBookingResponse item = AdminUnpaidBookingResponse.builder()
                .id(101L)
                .route("Addis Ababa → Hawassa")
                .estimatedPrice(new BigDecimal("4500.00"))
                .bookingStatus("accepted")
                .createdAt(LocalDateTime.now())
                .build();

        AdminUnpaidBookingsListResponse page = new AdminUnpaidBookingsListResponse(
                List.of(item), 31, 2, 10, true);
        when(bookingService.getUnpaidBookings(admin, PageRequest.of(2, 10))).thenReturn(page);

        ResponseEntity<AdminUnpaidBookingsListResponse> response = controller.getUnpaidBookings(admin, 2, 10);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getTotal()).isEqualTo(31);
        assertThat(response.getBody().getPage()).isEqualTo(2);
        assertThat(response.getBody().getPageSize()).isEqualTo(10);
        assertThat(response.getBody().isHasNext()).isTrue();
        assertThat(response.getBody().getBookings()).hasSize(1);
        assertThat(response.getBody().getBookings().get(0).getId()).isEqualTo(101L);

        verify(bookingService).getUnpaidBookings(admin, PageRequest.of(2, 10));
    }

    @Test
    @DisplayName("GET /unpaid uses defaults and caps page size at 100")
    void getUnpaidBookings_defaultsAndMaxSize() {
        AdminUnpaidBookingsListResponse empty = new AdminUnpaidBookingsListResponse(List.of(), 0, 0, 20, false);
        when(bookingService.getUnpaidBookings(admin, PageRequest.of(0, 20))).thenReturn(empty);
        when(bookingService.getUnpaidBookings(admin, PageRequest.of(0, 100)))
                .thenReturn(new AdminUnpaidBookingsListResponse(List.of(), 0, 0, 100, false));

        assertThat(controller.getUnpaidBookings(admin, 0, 20).getBody().getPageSize()).isEqualTo(20);
        assertThat(controller.getUnpaidBookings(admin, 0, 1000).getBody().getPageSize()).isEqualTo(100);
    }

    @Test
    @DisplayName("GET /unpaid rejects invalid page and size")
    void getUnpaidBookings_invalidPagination_rejected() {
        assertThatThrownBy(() -> controller.getUnpaidBookings(admin, -1, 20))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Page");
        assertThatThrownBy(() -> controller.getUnpaidBookings(admin, 0, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Size");
    }

    @Test
    @DisplayName("POST /{id}/mark-cash-paid returns HTTP 201 with settlement envelope")
    void markCashPaid_success() {
        PaymentResponse paymentResponse = PaymentResponse.builder()
                .id(501L)
                .bookingId(101L)
                .amount(new BigDecimal("4500.00"))
                .paymentMethod("cash")
                .paymentStatus("paid")
                .build();

        when(paymentService.settleCashPayment(101L, admin)).thenReturn(paymentResponse);

        ResponseEntity<CashPaymentSettlementResponse> response = controller.markCashPaid(101L, admin);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).isEqualTo("Cash payment recorded successfully");
        assertThat(response.getBody().getPayment().getId()).isEqualTo(501L);
        assertThat(response.getBody().getPayment().getPaymentStatus()).isEqualTo("paid");

        verify(paymentService).settleCashPayment(101L, admin);
    }

    @Test
    @DisplayName("Verify @PreAuthorize security annotations on controller methods")
    void securityAnnotations_verified() throws Exception {
        Method getUnpaid = BookingController.class.getMethod("getUnpaidBookings", User.class, int.class, int.class);
        PreAuthorize preAuthGet = getUnpaid.getAnnotation(PreAuthorize.class);
        assertThat(preAuthGet).isNotNull();
        assertThat(preAuthGet.value()).isEqualTo("hasRole('ADMIN')");

        Method markCash = BookingController.class.getMethod("markCashPaid", Long.class, User.class);
        PreAuthorize preAuthPost = markCash.getAnnotation(PreAuthorize.class);
        assertThat(preAuthPost).isNotNull();
        assertThat(preAuthPost.value()).isEqualTo("hasRole('ADMIN')");
    }
}
