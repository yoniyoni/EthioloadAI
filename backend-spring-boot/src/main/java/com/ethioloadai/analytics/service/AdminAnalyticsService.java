package com.ethioloadai.analytics.service;

import com.ethioloadai.analytics.dto.*;
import com.ethioloadai.user.entity.User;

import java.util.List;

public interface AdminAnalyticsService {

    /**
     * Retrieves high-level platform KPI statistics.
     * Accessible only by users with role ADMIN.
     */
    AdminStatsResponse getPlatformStats(User adminUser);

    /**
     * Retrieves escrow statistics broken down by status.
     * Accessible only by users with role ADMIN.
     */
    AdminEscrowResponse getEscrowOverview(User adminUser);

    /**
     * Retrieves monthly revenue and delivery analytics for the current calendar year.
     * Accessible only by users with role ADMIN.
     */
    List<MonthlyRevenueAnalyticsResponse> getRevenueAnalytics(User adminUser);

    /**
     * Retrieves top freight routes ordered by volume.
     * Accessible only by users with role ADMIN.
     */
    List<RouteAnalyticsResponse> getRouteAnalytics(User adminUser);

    /**
     * Retrieves cargo type distribution with volume count and percentage.
     * Accessible only by users with role ADMIN.
     */
    List<CargoAnalyticsResponse> getCargoAnalytics(User adminUser);

    /**
     * Updates driver status (e.g. active, suspended, approved) and syncs verification/active flags.
     * Accessible only by users with role ADMIN.
     */
    DriverStatusUpdateResponse updateDriverStatus(Long driverId, UpdateDriverStatusRequest request, User adminUser);
}
