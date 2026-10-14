package com.ethioloadai.analytics;

import com.ethioloadai.analytics.dto.*;
import com.ethioloadai.analytics.service.AdminAnalyticsServiceImpl;
import com.ethioloadai.booking.entity.Booking;
import com.ethioloadai.booking.repository.BookingRepository;
import com.ethioloadai.exception.AuthorizationException;
import com.ethioloadai.freight.entity.Freight;
import com.ethioloadai.freight.repository.FreightRepository;
import com.ethioloadai.payment.entity.Payment;
import com.ethioloadai.payment.repository.PaymentRepository;
import com.ethioloadai.user.entity.User;
import com.ethioloadai.user.exception.UserException;
import com.ethioloadai.user.repository.UserRepository;
import com.ethioloadai.vehicle.repository.VehicleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("AdminAnalyticsService (B17)")
class AdminAnalyticsServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private VehicleRepository vehicleRepository;
    @Mock private FreightRepository freightRepository;
    @Mock private PaymentRepository paymentRepository;
    @Mock private BookingRepository bookingRepository;

    @InjectMocks
    private AdminAnalyticsServiceImpl service;

    private User adminUser;
    private User driverUser;
    private User shipperUser;

    @BeforeEach
    void setUp() {
        adminUser = User.builder()
                .id(1L)
                .fullName("System Admin")
                .email("admin@ethioloadai.com")
                .phone("+251911000001")
                .role(User.Role.ADMIN)
                .build();

        driverUser = User.builder()
                .id(10L)
                .fullName("Abebe Driver")
                .email("abebe@driver.et")
                .phone("+251911000010")
                .role(User.Role.DRIVER)
                .isActive(true)
                .verificationStatus(false)
                .build();

        shipperUser = User.builder()
                .id(20L)
                .fullName("Almaz Shipper")
                .email("almaz@shipper.et")
                .phone("+251911000020")
                .role(User.Role.SHIPPER)
                .build();
    }

    @Nested
    @DisplayName("Authorization Checks")
    class AuthorizationTests {

        @Test
        @DisplayName("Anonymous/null user throws AuthorizationException")
        void nullUser_throwsAuthorizationException() {
            assertThatThrownBy(() -> service.getPlatformStats(null))
                    .isInstanceOf(AuthorizationException.class);
            assertThatThrownBy(() -> service.getEscrowOverview(null))
                    .isInstanceOf(AuthorizationException.class);
            assertThatThrownBy(() -> service.getRevenueAnalytics(null))
                    .isInstanceOf(AuthorizationException.class);
            assertThatThrownBy(() -> service.getRouteAnalytics(null))
                    .isInstanceOf(AuthorizationException.class);
            assertThatThrownBy(() -> service.getCargoAnalytics(null))
                    .isInstanceOf(AuthorizationException.class);
            assertThatThrownBy(() -> service.updateDriverStatus(10L, new UpdateDriverStatusRequest("active"), null))
                    .isInstanceOf(AuthorizationException.class);
        }

        @Test
        @DisplayName("Non-admin role (Driver/Shipper) throws AuthorizationException")
        void nonAdminUser_throwsAuthorizationException() {
            assertThatThrownBy(() -> service.getPlatformStats(driverUser))
                    .isInstanceOf(AuthorizationException.class);
            assertThatThrownBy(() -> service.getEscrowOverview(shipperUser))
                    .isInstanceOf(AuthorizationException.class);
            assertThatThrownBy(() -> service.getRevenueAnalytics(driverUser))
                    .isInstanceOf(AuthorizationException.class);
            assertThatThrownBy(() -> service.getRouteAnalytics(shipperUser))
                    .isInstanceOf(AuthorizationException.class);
            assertThatThrownBy(() -> service.getCargoAnalytics(driverUser))
                    .isInstanceOf(AuthorizationException.class);
            assertThatThrownBy(() -> service.updateDriverStatus(10L, new UpdateDriverStatusRequest("active"), shipperUser))
                    .isInstanceOf(AuthorizationException.class);
        }
    }

    @Nested
    @DisplayName("getPlatformStats")
    class PlatformStatsTests {

        @Test
        @DisplayName("Aggregates all counts, sums revenue, and formats nested structures")
        void platformStats_success() {
            when(userRepository.count()).thenReturn(50L);
            when(userRepository.countByRole(User.Role.DRIVER)).thenReturn(15L);
            when(userRepository.countByRoleAndVerificationStatus(User.Role.DRIVER, true)).thenReturn(10L);
            when(userRepository.countByRoleAndVerificationStatus(User.Role.DRIVER, false)).thenReturn(5L);

            when(freightRepository.countByStatusIn(List.of(
                    Freight.FreightStatus.POSTED,
                    Freight.FreightStatus.MATCHED,
                    Freight.FreightStatus.IN_TRANSIT
            ))).thenReturn(8L);

            when(freightRepository.countByStatusIn(List.of(
                    Freight.FreightStatus.DELIVERED,
                    Freight.FreightStatus.COMPLETED
            ))).thenReturn(25L);

            when(paymentRepository.count()).thenReturn(30L);
            when(paymentRepository.totalRevenue()).thenReturn(BigDecimal.valueOf(125000.50));
            when(vehicleRepository.countByAvailabilityStatus("available")).thenReturn(12L);

            AdminStatsResponse response = service.getPlatformStats(adminUser);

            assertThat(response).isNotNull();
            assertThat(response.getTotalUsers()).isEqualTo(50L);
            assertThat(response.getTotalDrivers()).isEqualTo(15L);
            assertThat(response.getVerifiedDrivers()).isEqualTo(10L);
            assertThat(response.getPendingVerifications()).isEqualTo(5L);
            assertThat(response.getActiveFreight()).isEqualTo(8L);
            assertThat(response.getCompletedDeliveries()).isEqualTo(25L);
            assertThat(response.getTotalRevenue()).isEqualTo(125000.50);
            assertThat(response.getActiveVehicles()).isEqualTo(12L);

            // Legacy preserved constants
            assertThat(response.getEscrowHeld()).isEqualTo(0.0);
            assertThat(response.getTotalCommissions()).isEqualTo(0.0);
            assertThat(response.getOpenDisputes()).isEqualTo(0L);
            assertThat(response.getAverageDeliveryTime()).isEqualTo(4.2);
            assertThat(response.getSuccessRate()).isEqualTo(85);

            // Nested compatibility maps
            assertThat(response.getUsers()).containsEntry("total", 50L);
            assertThat(response.getDrivers()).containsEntry("total", 15L).containsEntry("active", 10L);
            assertThat(response.getFreight()).containsEntry("posted", 8L).containsEntry("completed", 25L);
            assertThat(response.getPayments())
                    .containsEntry("total", 30L)
                    .containsEntry("revenue", 125000.50)
                    .containsEntry("escrowHeld", 0.0)
                    .containsEntry("commissions", 0.0)
                    .containsEntry("openDisputes", 0L);
        }

        @Test
        @DisplayName("If totalDrivers is 0, successRate is 0")
        void platformStats_zeroDrivers_successRateZero() {
            when(userRepository.count()).thenReturn(5L);
            when(userRepository.countByRole(User.Role.DRIVER)).thenReturn(0L);
            when(paymentRepository.totalRevenue()).thenReturn(null);

            AdminStatsResponse response = service.getPlatformStats(adminUser);

            assertThat(response.getSuccessRate()).isEqualTo(0);
            assertThat(response.getTotalRevenue()).isEqualTo(0.0);
        }
    }

    @Nested
    @DisplayName("getEscrowOverview")
    class EscrowTests {

        @Test
        @DisplayName("Returns 4 escrow buckets with count 0 and total 0.0")
        void escrowOverview_success() {
            AdminEscrowResponse response = service.getEscrowOverview(adminUser);

            assertThat(response).isNotNull();
            assertThat(response.getHeld()).isNotNull();
            assertThat(response.getHeld().getCount()).isEqualTo(0L);
            assertThat(response.getHeld().getTotal()).isEqualTo(0.0);

            assertThat(response.getInTransit().getCount()).isEqualTo(0L);
            assertThat(response.getInTransit().getTotal()).isEqualTo(0.0);

            assertThat(response.getReleased().getCount()).isEqualTo(0L);
            assertThat(response.getReleased().getTotal()).isEqualTo(0.0);

            assertThat(response.getDisputed().getCount()).isEqualTo(0L);
            assertThat(response.getDisputed().getTotal()).isEqualTo(0.0);
        }
    }

    @Nested
    @DisplayName("getRevenueAnalytics")
    class RevenueAnalyticsTests {

        @Test
        @DisplayName("Returns 12-month array with data for current/past months and zeroes for future months")
        void revenueAnalytics_success() {
            LocalDate now = LocalDate.now();
            int currentMonth = now.getMonthValue();

            PaymentRepository.MonthlyPaymentAggregate januaryPayments =
                    org.mockito.Mockito.mock(PaymentRepository.MonthlyPaymentAggregate.class);
            when(januaryPayments.getMonthNumber()).thenReturn(1);
            when(januaryPayments.getAmount()).thenReturn(new BigDecimal("5000.01"));
            when(januaryPayments.getPaymentCount()).thenReturn(2L);

            BookingRepository.MonthlyBookingCommissionAggregate februaryCommissions =
                    org.mockito.Mockito.mock(BookingRepository.MonthlyBookingCommissionAggregate.class);
            when(februaryCommissions.getMonthNumber()).thenReturn(2);
            when(februaryCommissions.getCommissions()).thenReturn(new BigDecimal("500.02"));

            when(paymentRepository.aggregateMonthlyPaidRevenue(eq(Payment.PaymentStatus.PAID), any(), any()))
                    .thenReturn(List.of(januaryPayments));
            when(bookingRepository.aggregateMonthlyBookingCommissions(any(), any(), any()))
                    .thenReturn(List.of(februaryCommissions));

            List<MonthlyRevenueAnalyticsResponse> result = service.getRevenueAnalytics(adminUser);

            assertThat(result).hasSize(12);
            MonthlyRevenueAnalyticsResponse jan = result.get(0);
            assertThat(jan.getMonth()).isEqualTo("Jan");
            assertThat(jan.getRevenue()).isCloseTo(5000.01, org.assertj.core.data.Offset.offset(0.000001));
            assertThat(jan.getCommissions()).isEqualTo(0.0);
            assertThat(jan.getDeliveries()).isEqualTo(2);
            MonthlyRevenueAnalyticsResponse feb = result.get(1);
            assertThat(feb.getRevenue()).isEqualTo(0.0);
            assertThat(feb.getCommissions()).isCloseTo(500.02, org.assertj.core.data.Offset.offset(0.000001));
            assertThat(feb.getDeliveries()).isZero();

            verify(paymentRepository).aggregateMonthlyPaidRevenue(
                    Payment.PaymentStatus.PAID,
                    LocalDate.of(now.getYear(), 1, 1).atStartOfDay(),
                    LocalDate.of(now.getYear() + 1, 1, 1).atStartOfDay());
            verify(bookingRepository).aggregateMonthlyBookingCommissions(
                    List.of(Booking.BookingStatus.CONFIRMED, Booking.BookingStatus.COMPLETED),
                    LocalDate.of(now.getYear(), 1, 1).atStartOfDay(),
                    LocalDate.of(now.getYear() + 1, 1, 1).atStartOfDay());

            if (currentMonth < 12) {
                MonthlyRevenueAnalyticsResponse future = result.get(11);
                assertThat(future.getMonth()).isEqualTo("Dec");
                assertThat(future.getRevenue()).isEqualTo(0.0);
                assertThat(future.getCommissions()).isEqualTo(0.0);
                assertThat(future.getDeliveries()).isEqualTo(0);
            }
        }

        @Test
        @DisplayName("Empty aggregates produce zero-valued months")
        void revenueAnalytics_emptyAggregates_returnsZeroes() {
            when(paymentRepository.aggregateMonthlyPaidRevenue(any(), any(), any())).thenReturn(Collections.emptyList());
            when(bookingRepository.aggregateMonthlyBookingCommissions(any(), any(), any()))
                    .thenReturn(Collections.emptyList());

            List<MonthlyRevenueAnalyticsResponse> result = service.getRevenueAnalytics(adminUser);

            assertThat(result).hasSize(12);
            assertThat(result).allSatisfy(month -> {
                assertThat(month.getRevenue()).isEqualTo(0.0);
                assertThat(month.getCommissions()).isEqualTo(0.0);
                assertThat(month.getDeliveries()).isZero();
            });
        }
    }

    @Nested
    @DisplayName("getRouteAnalytics")
    class RouteAnalyticsTests {

        @Test
        @DisplayName("Maps projections to RouteAnalyticsResponse with formatted route string")
        void routeAnalytics_success() {
            RouteCountProjection p1 = new RouteCountProjection() {
                @Override public String getPickup() { return "Addis Ababa"; }
                @Override public String getDelivery() { return "Hawassa"; }
                @Override public Long getCount() { return 14L; }
            };
            RouteCountProjection p2 = new RouteCountProjection() {
                @Override public String getPickup() { return "Dire Dawa"; }
                @Override public String getDelivery() { return "Djibouti"; }
                @Override public Long getCount() { return 8L; }
            };

            when(freightRepository.findTopRoutes(any(Pageable.class))).thenReturn(List.of(p1, p2));

            List<RouteAnalyticsResponse> routes = service.getRouteAnalytics(adminUser);

            assertThat(routes).hasSize(2);
            assertThat(routes.get(0).getPickup()).isEqualTo("Addis Ababa");
            assertThat(routes.get(0).getDelivery()).isEqualTo("Hawassa");
            assertThat(routes.get(0).getRoute()).isEqualTo("Addis Ababa → Hawassa");
            assertThat(routes.get(0).getCount()).isEqualTo(14L);

            assertThat(routes.get(1).getRoute()).isEqualTo("Dire Dawa → Djibouti");
            assertThat(routes.get(1).getCount()).isEqualTo(8L);
        }
    }

    @Nested
    @DisplayName("getCargoAnalytics")
    class CargoAnalyticsTests {

        @Test
        @DisplayName("Returns empty list when total freight count is 0")
        void cargoAnalytics_emptyDistribution() {
            when(freightRepository.findCargoTypeDistribution()).thenReturn(Collections.emptyList());

            List<CargoAnalyticsResponse> result = service.getCargoAnalytics(adminUser);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("Computes count and integer percentage correctly")
        void cargoAnalytics_calculatesPercentages() {
            CargoTypeCountProjection c1 = new CargoTypeCountProjection() {
                @Override public String getCargoType() { return "Agricultural"; }
                @Override public Long getCount() { return 75L; }
            };
            CargoTypeCountProjection c2 = new CargoTypeCountProjection() {
                @Override public String getCargoType() { return "Industrial"; }
                @Override public Long getCount() { return 25L; }
            };

            when(freightRepository.findCargoTypeDistribution()).thenReturn(List.of(c1, c2));

            List<CargoAnalyticsResponse> result = service.getCargoAnalytics(adminUser);

            assertThat(result).hasSize(2);
            assertThat(result.get(0).getCargoType()).isEqualTo("Agricultural");
            assertThat(result.get(0).getCount()).isEqualTo(75L);
            assertThat(result.get(0).getPercentage()).isEqualTo(75);

            assertThat(result.get(1).getCargoType()).isEqualTo("Industrial");
            assertThat(result.get(1).getCount()).isEqualTo(25L);
            assertThat(result.get(1).getPercentage()).isEqualTo(25);
        }
    }

    @Nested
    @DisplayName("updateDriverStatus")
    class UpdateDriverStatusTests {

        @Test
        @DisplayName("Throws UserNotFoundException when driver ID does not exist or user is not a driver")
        void driverNotFound_throwsException() {
            when(userRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.updateDriverStatus(999L, new UpdateDriverStatusRequest("active"), adminUser))
                    .isInstanceOf(UserException.UserNotFoundException.class);
        }

        @Test
        @DisplayName("Throws UserNotFoundException when user exists but has role SHIPPER")
        void userNotDriver_throwsException() {
            when(userRepository.findById(20L)).thenReturn(Optional.of(shipperUser));

            assertThatThrownBy(() -> service.updateDriverStatus(20L, new UpdateDriverStatusRequest("active"), adminUser))
                    .isInstanceOf(UserException.UserNotFoundException.class);
        }

        @Test
        @DisplayName("Setting status to 'active' updates verificationStatus=true and isActive=true")
        void statusActive_setsVerificationAndActiveTrue() {
            when(userRepository.findById(10L)).thenReturn(Optional.of(driverUser));

            DriverStatusUpdateResponse response = service.updateDriverStatus(10L, new UpdateDriverStatusRequest("active"), adminUser);

            assertThat(response).isNotNull();
            assertThat(response.getStatus()).isEqualTo("active");
            assertThat(driverUser.getVerificationStatus()).isTrue();
            assertThat(driverUser.getIsActive()).isTrue();
            assertThat(response.getUser()).containsEntry("verificationStatus", true);
            assertThat(response.getUser()).containsEntry("isActive", true);
            verify(userRepository).save(driverUser);
        }

        @Test
        @DisplayName("Setting status to 'suspended' sets verificationStatus=false and isActive=false")
        void statusSuspended_setsVerificationAndActiveFalse() {
            driverUser.setVerificationStatus(true);
            driverUser.setIsActive(true);
            when(userRepository.findById(10L)).thenReturn(Optional.of(driverUser));

            DriverStatusUpdateResponse response = service.updateDriverStatus(10L, new UpdateDriverStatusRequest("suspended"), adminUser);

            assertThat(response).isNotNull();
            assertThat(response.getStatus()).isEqualTo("suspended");
            assertThat(driverUser.getVerificationStatus()).isFalse();
            assertThat(driverUser.getIsActive()).isFalse();
            assertThat(response.getUser()).containsEntry("verificationStatus", false);
            assertThat(response.getUser()).containsEntry("isActive", false);
            verify(userRepository).save(driverUser);
        }

        @Test
        @DisplayName("Setting status to other (e.g. pending) sets verificationStatus=false and preserves isActive")
        void statusPending_setsVerificationFalse_preservesActive() {
            driverUser.setVerificationStatus(true);
            driverUser.setIsActive(true);
            when(userRepository.findById(10L)).thenReturn(Optional.of(driverUser));

            DriverStatusUpdateResponse response = service.updateDriverStatus(10L, new UpdateDriverStatusRequest("pending"), adminUser);

            assertThat(response.getStatus()).isEqualTo("pending");
            assertThat(driverUser.getVerificationStatus()).isFalse();
            assertThat(driverUser.getIsActive()).isTrue();
            verify(userRepository).save(driverUser);
        }
    }
}
