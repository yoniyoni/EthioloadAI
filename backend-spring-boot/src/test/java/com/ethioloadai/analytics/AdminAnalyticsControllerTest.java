package com.ethioloadai.analytics;

import com.ethioloadai.analytics.controller.AdminAnalyticsController;
import com.ethioloadai.analytics.dto.*;
import com.ethioloadai.analytics.service.AdminAnalyticsService;
import com.ethioloadai.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdminAnalyticsController (B17)")
class AdminAnalyticsControllerTest {

    @Mock
    private AdminAnalyticsService adminAnalyticsService;

    @InjectMocks
    private AdminAnalyticsController controller;

    private User adminUser;

    @BeforeEach
    void setUp() {
        adminUser = User.builder()
                .id(1L)
                .fullName("System Admin")
                .role(User.Role.ADMIN)
                .build();
    }

    @Test
    @DisplayName("GET /api/admin/stats returns 200 OK with AdminStatsResponse")
    void getPlatformStats_returns200Ok() {
        AdminStatsResponse mockResponse = AdminStatsResponse.builder()
                .totalUsers(100L)
                .totalDrivers(20L)
                .activeFreight(15L)
                .completedDeliveries(50L)
                .totalRevenue(200000.0)
                .build();

        when(adminAnalyticsService.getPlatformStats(adminUser)).thenReturn(mockResponse);

        ResponseEntity<AdminStatsResponse> response = controller.getPlatformStats(adminUser);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getTotalUsers()).isEqualTo(100L);
    }

    @Test
    @DisplayName("GET /api/admin/escrow returns 200 OK with AdminEscrowResponse")
    void getEscrowOverview_returns200Ok() {
        AdminEscrowResponse mockResponse = AdminEscrowResponse.builder()
                .held(new EscrowMetricItem(0L, 0.0))
                .inTransit(new EscrowMetricItem(0L, 0.0))
                .released(new EscrowMetricItem(0L, 0.0))
                .disputed(new EscrowMetricItem(0L, 0.0))
                .build();

        when(adminAnalyticsService.getEscrowOverview(adminUser)).thenReturn(mockResponse);

        ResponseEntity<AdminEscrowResponse> response = controller.getEscrowOverview(adminUser);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getHeld().getCount()).isEqualTo(0L);
    }

    @Test
    @DisplayName("GET /api/admin/analytics/revenue returns 200 OK with monthly list")
    void getRevenueAnalytics_returns200Ok() {
        MonthlyRevenueAnalyticsResponse m1 = MonthlyRevenueAnalyticsResponse.builder()
                .month("Jan")
                .revenue(10000.0)
                .commissions(1000.0)
                .deliveries(5)
                .build();

        when(adminAnalyticsService.getRevenueAnalytics(adminUser)).thenReturn(List.of(m1));

        ResponseEntity<List<MonthlyRevenueAnalyticsResponse>> response = controller.getRevenueAnalytics(adminUser);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(1);
        assertThat(response.getBody().get(0).getMonth()).isEqualTo("Jan");
    }

    @Test
    @DisplayName("GET /api/admin/analytics/routes returns 200 OK with route list")
    void getRouteAnalytics_returns200Ok() {
        RouteAnalyticsResponse r1 = RouteAnalyticsResponse.builder()
                .pickup("Addis Ababa")
                .delivery("Mojo")
                .route("Addis Ababa → Mojo")
                .count(25L)
                .build();

        when(adminAnalyticsService.getRouteAnalytics(adminUser)).thenReturn(List.of(r1));

        ResponseEntity<List<RouteAnalyticsResponse>> response = controller.getRouteAnalytics(adminUser);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(1);
        assertThat(response.getBody().get(0).getRoute()).isEqualTo("Addis Ababa → Mojo");
    }

    @Test
    @DisplayName("GET /api/admin/analytics/cargo returns 200 OK with cargo list")
    void getCargoAnalytics_returns200Ok() {
        CargoAnalyticsResponse c1 = CargoAnalyticsResponse.builder()
                .cargoType("Perishable Goods")
                .count(30L)
                .percentage(40)
                .build();

        when(adminAnalyticsService.getCargoAnalytics(adminUser)).thenReturn(List.of(c1));

        ResponseEntity<List<CargoAnalyticsResponse>> response = controller.getCargoAnalytics(adminUser);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(1);
        assertThat(response.getBody().get(0).getCargoType()).isEqualTo("Perishable Goods");
    }

    @Test
    @DisplayName("PATCH /api/drivers/{id}/status returns 200 OK with DriverStatusUpdateResponse")
    void updateDriverStatus_returns200Ok() {
        UpdateDriverStatusRequest request = new UpdateDriverStatusRequest("active");
        DriverStatusUpdateResponse mockResponse = DriverStatusUpdateResponse.builder()
                .id(10L)
                .userId(10L)
                .status("active")
                .user(Map.of("id", 10L, "isActive", true, "verificationStatus", true))
                .build();

        when(adminAnalyticsService.updateDriverStatus(eq(10L), eq(request), eq(adminUser))).thenReturn(mockResponse);

        ResponseEntity<DriverStatusUpdateResponse> response = controller.updateDriverStatus(10L, request, adminUser);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo("active");
    }
}
