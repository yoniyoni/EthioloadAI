package com.ethioloadai.trip.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for trip management system.
 * Allows customization of business rules, thresholds, and behavior.
 */
@Component
@ConfigurationProperties(prefix = "trip.management")
@Data
public class TripManagementProperties {

    /**
     * Scheduling configuration.
     */
    private final Scheduling scheduling = new Scheduling();

    /**
     * Business rules configuration.
     */
    private final BusinessRules businessRules = new BusinessRules();

    /**
     * Monitoring configuration.
     */
    private final Monitoring monitoring = new Monitoring();

    /**
     * Route data configuration.
     */
    private final RouteData routeData = new RouteData();

    @Data
    public static class Scheduling {
        /**
         * Enable/disable scheduled tasks.
         */
        private boolean enabled = true;

        /**
         * Automatic transition check interval (milliseconds).
         */
        private long automaticTransitionInterval = 300000; // 5 minutes

        /**
         * Attention check interval (milliseconds).
         */
        private long attentionCheckInterval = 900000; // 15 minutes

        /**
         * Timeout handling interval (milliseconds).
         */
        private long timeoutCheckInterval = 3600000; // 1 hour

        /**
         * Health report interval (milliseconds).
         */
        private long healthReportInterval = 1800000; // 30 minutes
    }

    @Data
    public static class BusinessRules {
        /**
         * Maximum concurrent trips per driver.
         */
        private int maxConcurrentTripsPerDriver = 3;

        /**
         * Overdue warning threshold (hours).
         */
        private int overdueWarningHours = 48;

        /**
         * Maximum trip duration (hours).
         */
        private int maxTripDurationHours = 72;

        /**
         * Deadline warning threshold (hours).
         */
        private int deadlineWarningHours = 36;

        /**
         * Stuck trip threshold (hours).
         */
        private int stuckTripHours = 72;

        /**
         * Minimum trip duration for warnings (minutes).
         */
        private int minimumTripDurationMinutes = 30;

        /**
         * Rollback time limit (hours).
         */
        private int rollbackTimeLimitHours = 24;

        /**
         * Enable automatic trip completion.
         */
        private boolean enableAutomaticCompletion = true;
    }

    @Data
    public static class Monitoring {
        /**
         * High ongoing trips threshold.
         */
        private int highOngoingTripsThreshold = 100;

        /**
         * Critical overdue trips count.
         */
        private int criticalOverdueCount = 5;

        /**
         * Enable system health monitoring.
         */
        private boolean healthMonitoringEnabled = true;

        /**
         * Enable performance analytics.
         */
        private boolean performanceAnalyticsEnabled = true;
    }

    @Data
    public static class RouteData {
        /**
         * Maximum GPS breadcrumbs per trip.
         */
        private int maxGpsBreadcrumbs = 500;

        /**
         * Route data retention days.
         */
        private int retentionDays = 90;

        /**
         * Enable route data compression.
         */
        private boolean compressionEnabled = true;
    }
}