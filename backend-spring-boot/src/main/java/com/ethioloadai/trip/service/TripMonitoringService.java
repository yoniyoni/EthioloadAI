package com.ethioloadai.trip.service;

import com.ethioloadai.trip.entity.Trip;

import java.util.List;
import java.util.Map;

/**
 * Service for monitoring trip health, performance, and analytics.
 * Provides insights into trip operations and identifies issues.
 */
public interface TripMonitoringService {

    /**
     * Get comprehensive trip statistics for admin dashboard.
     *
     * @return map of statistics
     */
    Map<String, Object> getTripStatistics();

    /**
     * Get all overdue trips that require immediate attention.
     *
     * @return list of overdue trips
     */
    List<Trip> getOverdueTrips();

    /**
     * Get trips that are approaching their expected completion time.
     *
     * @return list of trips nearing deadline
     */
    List<Trip> getTripsNearingDeadline();

    /**
     * Get average trip duration by type.
     *
     * @return statistics by trip type
     */
    Map<Trip.TripType, Double> getAverageTripDurationByType();

    /**
     * Get trip completion rate statistics.
     *
     * @return completion rate metrics
     */
    TripCompletionMetrics getTripCompletionMetrics();

    /**
     * Check system health related to trip management.
     *
     * @return health status
     */
    TripSystemHealth checkTripSystemHealth();

    /**
     * Trip completion metrics container.
     */
    class TripCompletionMetrics {
        private final long totalTrips;
        private final long completedTrips;
        private final long ongoingTrips;
        private final double completionRate;
        private final double averageCompletionTimeHours;

        public TripCompletionMetrics(long totalTrips, long completedTrips, long ongoingTrips,
                                   double completionRate, double averageCompletionTimeHours) {
            this.totalTrips = totalTrips;
            this.completedTrips = completedTrips;
            this.ongoingTrips = ongoingTrips;
            this.completionRate = completionRate;
            this.averageCompletionTimeHours = averageCompletionTimeHours;
        }

        // Getters
        public long getTotalTrips() { return totalTrips; }
        public long getCompletedTrips() { return completedTrips; }
        public long getOngoingTrips() { return ongoingTrips; }
        public double getCompletionRate() { return completionRate; }
        public double getAverageCompletionTimeHours() { return averageCompletionTimeHours; }
    }

    /**
     * System health status for trip management.
     */
    class TripSystemHealth {
        private final boolean healthy;
        private final int overdueTripsCount;
        private final int stuckTripsCount;
        private final String status;
        private final List<String> issues;

        public TripSystemHealth(boolean healthy, int overdueTripsCount, int stuckTripsCount,
                               String status, List<String> issues) {
            this.healthy = healthy;
            this.overdueTripsCount = overdueTripsCount;
            this.stuckTripsCount = stuckTripsCount;
            this.status = status;
            this.issues = issues;
        }

        // Getters
        public boolean isHealthy() { return healthy; }
        public int getOverdueTripsCount() { return overdueTripsCount; }
        public int getStuckTripsCount() { return stuckTripsCount; }
        public String getStatus() { return status; }
        public List<String> getIssues() { return issues; }
    }
}