package com.ethioloadai.contract;

import com.ethioloadai.booking.entity.Booking;
import com.ethioloadai.booking.repository.BookingRepository;
import com.ethioloadai.contract.dto.ContractResponse;
import com.ethioloadai.contract.exception.ContractException.ContractNotFoundException;
import com.ethioloadai.contract.exception.ContractException.UnauthorizedContractAccessException;
import com.ethioloadai.contract.service.ContractServiceImpl;
import com.ethioloadai.freight.entity.Freight;
import com.ethioloadai.freight.repository.FreightRepository;
import com.ethioloadai.payment.entity.Payment;
import com.ethioloadai.payment.repository.PaymentRepository;
import com.ethioloadai.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ContractServiceImplTest {

    @Mock
    private FreightRepository freightRepository;

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @InjectMocks
    private ContractServiceImpl contractService;

    private User shipper;
    private User driver;
    private User thirdParty;
    private User admin;
    private Freight freight;
    private Booking booking;
    private Payment payment;

    @BeforeEach
    void setUp() {
        shipper = User.builder()
                .id(1L)
                .fullName("Abebe Shipper")
                .email("shipper@example.com")
                .role(User.Role.SHIPPER)
                .build();

        driver = User.builder()
                .id(2L)
                .fullName("Kebede Driver")
                .email("driver@example.com")
                .role(User.Role.DRIVER)
                .build();

        thirdParty = User.builder()
                .id(99L)
                .fullName("Intruder User")
                .email("intruder@example.com")
                .role(User.Role.SHIPPER)
                .build();

        admin = User.builder()
                .id(100L)
                .fullName("System Admin")
                .email("admin@example.com")
                .role(User.Role.ADMIN)
                .build();

        freight = Freight.builder()
                .id(10L)
                .shipper(shipper)
                .matchedDriver(driver)
                .pickupLocation("Addis Ababa, Bole")
                .destination("Hawassa Industrial Park")
                .materialType("Textiles")
                .budget(new BigDecimal("30000.00"))
                .matchedPrice(new BigDecimal("28000.00"))
                .deadline(LocalDate.of(2026, 9, 30))
                .status(Freight.FreightStatus.MATCHED)
                .build();

        booking = Booking.builder()
                .id(50L)
                .cargoRequest(freight)
                .driver(driver)
                .bookingStatus(Booking.BookingStatus.ACCEPTED)
                .estimatedPrice(new BigDecimal("28000.00"))
                .build();

        payment = Payment.builder()
                .id(70L)
                .booking(booking)
                .amount(new BigDecimal("28000.00"))
                .paymentStatus(Payment.PaymentStatus.PAID)
                .build();
    }

    @Test
    @DisplayName("Shipper can retrieve contract successfully")
    void getContract_asShipper_success() {
        when(freightRepository.findById(10L)).thenReturn(Optional.of(freight));
        when(bookingRepository.findFirstByCargoRequest_IdAndBookingStatusNotOrderByIdDesc(
                10L, Booking.BookingStatus.CANCELLED)).thenReturn(Optional.of(booking));
        when(paymentRepository.findByBooking_Id(50L)).thenReturn(Optional.of(payment));

        ContractResponse res = contractService.getContractByFreightId(10L, shipper);

        assertThat(res).isNotNull();
        assertThat(res.id()).isEqualTo(50L);
        assertThat(res.freightId()).isEqualTo(10L);
        assertThat(res.status()).isEqualTo("active");
        assertThat(res.paymentStatus()).isEqualTo("paid");
        assertThat(res.agreedPrice()).isEqualByComparingTo("28000.00");
        assertThat(res.deadline()).isEqualTo(LocalDate.of(2026, 9, 30));
        assertThat(res.shipper().name()).isEqualTo("Abebe Shipper");
        assertThat(res.shipper().email()).isEqualTo("shipper@example.com");
        assertThat(res.driver().name()).isEqualTo("Kebede Driver");
        assertThat(res.driver().email()).isEqualTo("driver@example.com");
        assertThat(res.freight().pickupLocation()).isEqualTo("Addis Ababa, Bole");
        assertThat(res.freight().deliveryLocation()).isEqualTo("Hawassa Industrial Park");
    }

    @Test
    @DisplayName("Matched driver can retrieve contract successfully")
    void getContract_asMatchedDriver_success() {
        when(freightRepository.findById(10L)).thenReturn(Optional.of(freight));
        when(bookingRepository.findFirstByCargoRequest_IdAndBookingStatusNotOrderByIdDesc(
                10L, Booking.BookingStatus.CANCELLED)).thenReturn(Optional.of(booking));
        when(paymentRepository.findByBooking_Id(50L)).thenReturn(Optional.of(payment));

        ContractResponse res = contractService.getContractByFreightId(10L, driver);

        assertThat(res).isNotNull();
        assertThat(res.id()).isEqualTo(50L);
        assertThat(res.driver().name()).isEqualTo("Kebede Driver");
    }

    @Test
    @DisplayName("Admin can retrieve contract for any freight")
    void getContract_asAdmin_success() {
        when(freightRepository.findById(10L)).thenReturn(Optional.of(freight));
        when(bookingRepository.findFirstByCargoRequest_IdAndBookingStatusNotOrderByIdDesc(
                10L, Booking.BookingStatus.CANCELLED)).thenReturn(Optional.of(booking));
        when(paymentRepository.findByBooking_Id(50L)).thenReturn(Optional.of(payment));

        ContractResponse res = contractService.getContractByFreightId(10L, admin);

        assertThat(res).isNotNull();
        assertThat(res.freightId()).isEqualTo(10L);
    }

    @Test
    @DisplayName("Unauthorized third party receives 403 Forbidden")
    void getContract_unauthorizedThirdParty_throws403() {
        when(freightRepository.findById(10L)).thenReturn(Optional.of(freight));
        when(bookingRepository.findFirstByCargoRequest_IdAndBookingStatusNotOrderByIdDesc(
                10L, Booking.BookingStatus.CANCELLED)).thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> contractService.getContractByFreightId(10L, thirdParty))
                .isInstanceOf(UnauthorizedContractAccessException.class)
                .hasMessageContaining("not authorized");
    }

    @Test
    @DisplayName("Missing freight throws 404 Not Found")
    void getContract_missingFreight_throws404() {
        when(freightRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> contractService.getContractByFreightId(999L, shipper))
                .isInstanceOf(ContractNotFoundException.class)
                .hasMessageContaining("Freight not found");
    }

    @Test
    @DisplayName("Freight with no matched driver and no booking throws 404 Not Found")
    void getContract_noCounterparty_throws404() {
        Freight unassignedFreight = Freight.builder()
                .id(20L)
                .shipper(shipper)
                .matchedDriver(null)
                .status(Freight.FreightStatus.POSTED)
                .build();

        when(freightRepository.findById(20L)).thenReturn(Optional.of(unassignedFreight));
        when(bookingRepository.findFirstByCargoRequest_IdAndBookingStatusNotOrderByIdDesc(
                20L, Booking.BookingStatus.CANCELLED)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> contractService.getContractByFreightId(20L, shipper))
                .isInstanceOf(ContractNotFoundException.class)
                .hasMessageContaining("No contract found");
    }

    @Test
    @DisplayName("Completed freight maps status to 'completed'")
    void getContract_completedStatus() {
        freight.setStatus(Freight.FreightStatus.COMPLETED);
        booking.setBookingStatus(Booking.BookingStatus.COMPLETED);

        when(freightRepository.findById(10L)).thenReturn(Optional.of(freight));
        when(bookingRepository.findFirstByCargoRequest_IdAndBookingStatusNotOrderByIdDesc(
                10L, Booking.BookingStatus.CANCELLED)).thenReturn(Optional.of(booking));
        when(paymentRepository.findByBooking_Id(50L)).thenReturn(Optional.of(payment));

        ContractResponse res = contractService.getContractByFreightId(10L, shipper);
        assertThat(res.status()).isEqualTo("completed");
    }

    @Test
    @DisplayName("Cancelled freight maps status to 'cancelled'")
    void getContract_cancelledStatus() {
        freight.setStatus(Freight.FreightStatus.CANCELLED);
        booking.setBookingStatus(Booking.BookingStatus.CANCELLED);

        when(freightRepository.findById(10L)).thenReturn(Optional.of(freight));
        when(bookingRepository.findFirstByCargoRequest_IdAndBookingStatusNotOrderByIdDesc(
                10L, Booking.BookingStatus.CANCELLED)).thenReturn(Optional.empty());

        ContractResponse res = contractService.getContractByFreightId(10L, shipper);
        assertThat(res.status()).isEqualTo("cancelled");
        assertThat(res.id()).isEqualTo(freight.getId());
    }

    @Test
    @DisplayName("Payment status mapping verifies 'pending', 'paid', and 'failed'")
    void getContract_paymentStatuses() {
        when(freightRepository.findById(10L)).thenReturn(Optional.of(freight));
        when(bookingRepository.findFirstByCargoRequest_IdAndBookingStatusNotOrderByIdDesc(
                10L, Booking.BookingStatus.CANCELLED)).thenReturn(Optional.of(booking));

        // 1. Payment pending
        payment.setPaymentStatus(Payment.PaymentStatus.PENDING);
        when(paymentRepository.findByBooking_Id(50L)).thenReturn(Optional.of(payment));
        ContractResponse res1 = contractService.getContractByFreightId(10L, shipper);
        assertThat(res1.paymentStatus()).isEqualTo("pending");

        // 2. Payment failed
        payment.setPaymentStatus(Payment.PaymentStatus.FAILED);
        when(paymentRepository.findByBooking_Id(50L)).thenReturn(Optional.of(payment));
        ContractResponse res2 = contractService.getContractByFreightId(10L, shipper);
        assertThat(res2.paymentStatus()).isEqualTo("failed");

        // 3. No payment record
        when(paymentRepository.findByBooking_Id(50L)).thenReturn(Optional.empty());
        ContractResponse res3 = contractService.getContractByFreightId(10L, shipper);
        assertThat(res3.paymentStatus()).isEqualTo("pending");
    }

    @Test
    @DisplayName("Uses the repository-selected highest-ID non-cancelled booking")
    void getContract_usesHighestIdEligibleBooking() {
        Booking activeBooking = Booking.builder()
                .id(55L)
                .cargoRequest(freight)
                .driver(driver)
                .bookingStatus(Booking.BookingStatus.ACCEPTED)
                .estimatedPrice(new BigDecimal("28000.00"))
                .build();

        when(freightRepository.findById(10L)).thenReturn(Optional.of(freight));
        when(bookingRepository.findFirstByCargoRequest_IdAndBookingStatusNotOrderByIdDesc(
                10L, Booking.BookingStatus.CANCELLED)).thenReturn(Optional.of(activeBooking));
        when(paymentRepository.findByBooking_Id(55L)).thenReturn(Optional.of(payment));

        ContractResponse res = contractService.getContractByFreightId(10L, shipper);
        assertThat(res.id()).isEqualTo(55L);
        assertThat(res.agreedPrice()).isEqualByComparingTo("28000.00");
        verify(bookingRepository).findFirstByCargoRequest_IdAndBookingStatusNotOrderByIdDesc(
                10L, Booking.BookingStatus.CANCELLED);
        verify(bookingRepository, never()).findByCargoRequest_Id(anyLong());
    }

    @Test
    @DisplayName("Fallback to Freight matchedDriver when no Booking record exists")
    void getContract_fallbackToMatchedDriver() {
        when(freightRepository.findById(10L)).thenReturn(Optional.of(freight));
        when(bookingRepository.findFirstByCargoRequest_IdAndBookingStatusNotOrderByIdDesc(
                10L, Booking.BookingStatus.CANCELLED)).thenReturn(Optional.empty());

        ContractResponse res = contractService.getContractByFreightId(10L, shipper);

        assertThat(res).isNotNull();
        assertThat(res.id()).isEqualTo(10L); // Falls back to freight ID
        assertThat(res.driver().name()).isEqualTo("Kebede Driver");
        assertThat(res.agreedPrice()).isEqualByComparingTo("28000.00");
        assertThat(res.paymentStatus()).isEqualTo("pending");
        assertThat(res.status()).isEqualTo("active");
    }

    @Test
    @DisplayName("All-cancelled booking history uses matched-driver fallback")
    void getContract_allBookingsCancelled_fallsBackToMatchedDriver() {
        when(freightRepository.findById(10L)).thenReturn(Optional.of(freight));
        when(bookingRepository.findFirstByCargoRequest_IdAndBookingStatusNotOrderByIdDesc(
                10L, Booking.BookingStatus.CANCELLED)).thenReturn(Optional.empty());

        ContractResponse response = contractService.getContractByFreightId(10L, shipper);

        assertThat(response.id()).isEqualTo(freight.getId());
        assertThat(response.driver().name()).isEqualTo(driver.getFullName());
    }

    @Test
    @DisplayName("All-cancelled booking history without matched driver returns 404")
    void getContract_allBookingsCancelled_withoutMatchedDriver_throws404() {
        Freight unassignedFreight = Freight.builder()
                .id(20L)
                .shipper(shipper)
                .matchedDriver(null)
                .status(Freight.FreightStatus.POSTED)
                .build();
        when(freightRepository.findById(20L)).thenReturn(Optional.of(unassignedFreight));
        when(bookingRepository.findFirstByCargoRequest_IdAndBookingStatusNotOrderByIdDesc(
                20L, Booking.BookingStatus.CANCELLED)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> contractService.getContractByFreightId(20L, shipper))
                .isInstanceOf(ContractNotFoundException.class)
                .hasMessageContaining("No contract found for freight #20");
    }

    @Test
    @DisplayName("Participant authorization uses the selected eligible booking")
    void getContract_authorizationUsesSelectedBooking() {
        User selectedDriver = User.builder()
                .id(3L)
                .fullName("Selected Driver")
                .email("selected@example.com")
                .role(User.Role.DRIVER)
                .build();
        User historicalDriver = User.builder()
                .id(4L)
                .fullName("Historical Driver")
                .email("historical@example.com")
                .role(User.Role.DRIVER)
                .build();
        Freight freightWithoutMatchedDriver = Freight.builder()
                .id(10L)
                .shipper(shipper)
                .matchedDriver(null)
                .status(Freight.FreightStatus.MATCHED)
                .build();
        Booking selectedBooking = Booking.builder()
                .id(55L)
                .cargoRequest(freightWithoutMatchedDriver)
                .driver(selectedDriver)
                .bookingStatus(Booking.BookingStatus.ACCEPTED)
                .build();
        when(freightRepository.findById(10L)).thenReturn(Optional.of(freightWithoutMatchedDriver));
        when(bookingRepository.findFirstByCargoRequest_IdAndBookingStatusNotOrderByIdDesc(
                10L, Booking.BookingStatus.CANCELLED)).thenReturn(Optional.of(selectedBooking));

        assertThatThrownBy(() -> contractService.getContractByFreightId(10L, historicalDriver))
                .isInstanceOf(UnauthorizedContractAccessException.class);

        ContractResponse response = contractService.getContractByFreightId(10L, selectedDriver);
        assertThat(response.driver().name()).isEqualTo("Selected Driver");
    }
}
