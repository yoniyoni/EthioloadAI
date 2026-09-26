package com.ethioloadai.trip.service;

import com.ethioloadai.trip.entity.Trip;
import com.ethioloadai.trip.repository.TripRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TripMonitoringServiceImpl implements TripMonitoringService {
    private static final Logger log = LoggerFactory.getLogger(TripMonitoringServiceImpl.class);

    private final TripRepository tripRepository;
    private final TripLifecycleService tripLifecycleService;

    // Business rule constants
    private static final int OVERDUE_HOURS_THRESHOLD = 48;
    private static final int DEADLINE_WARNING_HOURS = 36;
    private static final int STUCK_TRIP_HOURS = 72;

    @Override
    public Map<String, Object> getTripStatistics() {
        log.debug("Generating comprehensive trip statistics");

        Map<String, Object> stats = new HashMap<>();

        // Basic counts
        long totalTrips = tripRepository.count();
        long ongoingTrips = tripRepository.findByTripStatus(Trip.TripStatus.ONGOING).size();
        long completedTrips = tripRepository.findByTripStatus(Trip.TripStatus.COMPLETED).size();

        // Overdue and problem trips
        List<Trip> overdueTrips = getOverdueTrips();
        List<Trip> nearDeadlineTrips = getTripsNearingDeadline();

        // Duration statistics
        Map<Trip.TripType, Double> avgDurationByType = getAverageTripDurationByType();

        // Completion metrics
        TripCompletionMetrics completionMetrics = getTripCompletionMetrics();

        // Populate statistics map
        stats.put("totalTrips", totalTrips);
        stats.put("ongoingTrips", ongoingTrips);
        stats.put("completedTrips", completedTrips);
        stats.put("overdueTrips", overdueTrips.size());
        stats.put("nearDeadlineTrips", nearDeadlineTrips.size());
        stats.put("averageDurationByType", avgDurationByType);
        stats.put("completionRate", completionMetrics.getCompletionRate());
        stats.put("averageCompletionTimeHours", completionMetrics.getAverageCompletionTimeHours());

        // System health
        TripSystemHealth systemHealth = checkTripSystemHealth();
        stats.put("systemHealthy", systemHealth.isHealthy());
        stats.put("systemStatus", systemHealth.getStatus());
        stats.put("systemIssues", systemHealth.getIssues());

        log.debug("Trip statistics generated: {} trips total, {} ongoing, {} overdue",
                totalTrips, ongoingTrips, overdueTrips.size());

        return stats;
    }

    @Override
    public List<Trip> getOverdueTrips() {
        List<Trip> ongoingTrips = tripRepository.findByTripStatus(Trip.TripStatus.ONGOING);
        
        return ongoingTrips.stream()
                .filter(trip -> {
                    Duration duration = tripLifecycleService.calculateTripDuration(trip);
                    return duration.toHours() > OVERDUE_HOURS_THRESHOLD;
                })
                .collect(Collectors.toList());
    }

    @Override
    public List<Trip> getTripsNearingDeadline() {
        List<Trip> ongoingTrips = tripRepository.findByTripStatus(Trip.TripStatus.ONGOING);
        
        return ongoingTrips.stream()
                .filter(trip -> {
                    Duration duration = tripLifecycleService.calculateTripDuration(trip);
                    long hours = duration.toHours();
                    return hours > DEADLINE_WARNING_HOURS && hours <= OVERDUE_HOURS_THRESHOLD;
                })
                .collect(Collectors.toList());
    }

    @Override
    public Map<Trip.TripType, Double> getAverageTripDurationByType() {
        List<Trip> completedTrips = tripRepository.findByTripStatus(Trip.TripStatus.COMPLETED);
        
        Map<Trip.TripType, List<Duration>> durationsByType = completedTrips.stream()
                .filter(trip -> trip.getStartTime() != null && trip.getEndTime() != null)
                .collect(Collectors.groupingBy(
                    Trip::getTripType,
                    Collectors.mapping(
                        trip -> Duration.between(trip.getStartTime(), trip.getEndTime()),
                        Collectors.toList()
                    )
                ));

        return durationsByType.entrySet().stream()
                .collect(Collectors.toMap(
                    Map.Entry::getKey,
                    entry -> entry.getValue().stream()
                            .mapToDouble(duration -> duration.toMinutes() / 60.0) // Convert to hours
                            .average()
                            .orElse(0.0)
                ));
    }

    @Override
    public TripCompletionMetrics getTripCompletionMetrics() {
        long totalTrips = tripRepository.count();
        long completedTrips = tripRepository.findByTripStatus(Trip.TripStatus.COMPLETED).size();
        long ongoingTrips = tripRepository.findByTripStatus(Trip.TripStatus.ONGOING).size();

        double completionRate = totalTrips > 0 ? 
            (double) completedTrips / totalTrips * 100.0 : 0.0;

        // Calculate average completion time for completed trips
        List<Trip> completedTripsList = tripRepository.findByTripStatus(Trip.TripStatus.COMPLETED);
        double averageCompletionTimeHours = completedTripsList.stream()
                .filter(trip -> trip.getStartTime() != null && trip.getEndTime() != null)
                .mapToDouble(trip -> {
                    Duration duration = Duration.between(trip.getStartTime(), trip.getEndTime());
                    return duration.toMinutes() / 60.0;
                })
                .average()
                .orElse(0.0);

        return new TripCompletionMetrics(
            totalTrips, completedTrips, ongoingTrips, 
            completionRate, averageCompletionTimeHours
        );
    }

    @Override
    public TripSystemHealth checkTripSystemHealth() {
        List<String> issues = new ArrayList<>();
        
        // Check for overdue trips
        List<Trip> overdueTrips = getOverdueTrips();
        int overdueCount = overdueTrips.size();
        
        // Check for stuck trips (extremely long duration)
        List<Trip> stuckTrips = getStuckTrips();
        int stuckCount = stuckTrips.size();

        // Analyze issues
        if (overdueCount > 0) {
            issues.add(String.format("%d trips are overdue (>%d hours)", overdueCount, OVERDUE_HOURS_THRESHOLD));
        }
        
        if (stuckCount > 0) {
            issues.add(String.format("%d trips appear stuck (>%d hours)", stuckCount, STUCK_TRIP_HOURS));
        }

        // Check for unusual patterns
        long ongoingTrips = tripRepository.findByTripStatus(Trip.TripStatus.ONGOING).size();
        if (ongoingTrips > 100) { // Configurable threshold
            issues.add(String.format("High number of ongoing trips: %d", ongoingTrips));
        }

        // Determine overall health status
        boolean healthy = issues.isEmpty();
        String status = healthy ? "HEALTHY" : 
                       stuckCount > 0 ? "CRITICAL" : 
                       overdueCount > 5 ? "WARNING" : "DEGRADED";

        log.debug("Trip system health check: {} (overdue: {}, stuck: {}, issues: {})",
                status, overdueCount, stuckCount, issues.size());

        return new TripSystemHealth(healthy, overdueCount, stuckCount, status, issues);
    }

    // ── Private Helper Methods ───────────────────────────────────────────────

    private List<Trip> getStuckTrips() {
        List<Trip> ongoingTrips = tripRepository.findByTripStatus(Trip.TripStatus.ONGOING);
        
        return ongoingTrips.stream()
                .filter(trip -> {
                    Duration duration = tripLifecycleService.calculateTripDuration(trip);
                    return duration.toHours() > STUCK_TRIP_HOURS;
                })
                .collect(Collectors.toList());
    }
}