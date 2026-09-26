package com.ethioloadai.trip.service;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import com.ethioloadai.trip.entity.Trip;

/**
 * Scheduled service for automated trip management tasks.
 * Handles periodic operations like automatic transitions, health checks, and cleanup.
 */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "trip.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class TripScheduledService {
    private static final Logger log = LoggerFactory.getLogger(TripScheduledService.class);

    private final TripStatusTransitionService tripStatusTransitionService;
    private final TripMonitoringService tripMonitoringService;
    private final TripLifecycleService tripLifecycleService;

    /**
     * Process automatic status transitions every 5 minutes.
     * Auto-completes trips when all stops are finished.
     */
    @Scheduled(fixedRate = 300000) // 5 minutes
    public void processAutomaticTransitions() {
        try {
            log.debug("Processing automatic trip status transitions");
            
            List<Trip> transitionedTrips = tripStatusTransitionService.processAutomaticTransitions();
            
            if (!transitionedTrips.isEmpty()) {
                log.info("Automatic transitions processed: {} trips auto-completed", 
                        transitionedTrips.size());
                
                // Log each auto-completed trip
                transitionedTrips.forEach(trip -> 
                    log.info("Auto-completed trip {}: all stops finished", trip.getId()));
            }
            
        } catch (Exception e) {
            log.error("Error processing automatic transitions: {}", e.getMessage(), e);
        }
    }

    /**
     * Check for trips requiring attention every 15 minutes.
     * Identifies overdue and stuck trips for admin notification.
     */
    @Scheduled(fixedRate = 900000) // 15 minutes
    public void checkTripsRequiringAttention() {
        try {
            log.debug("Checking for trips requiring attention");
            
            List<Trip> tripsRequiringAttention = tripLifecycleService.getTripsRequiringAttention();
            
            if (!tripsRequiringAttention.isEmpty()) {
                log.warn("Found {} trips requiring attention", tripsRequiringAttention.size());
                
                // In a full implementation, this would:
                // - Send notifications to admin
                // - Create alerts in monitoring system
                // - Trigger escalation procedures
                
                tripsRequiringAttention.forEach(trip -> {
                    if (tripLifecycleService.isTripOverdue(trip)) {
                        log.warn("Trip {} is overdue - duration: {} hours", 
                               trip.getId(), 
                               tripLifecycleService.calculateTripDuration(trip).toHours());
                    }
                });
            }
            
        } catch (Exception e) {
            log.error("Error checking trips requiring attention: {}", e.getMessage(), e);
        }
    }

    /**
     * Handle trip timeouts every hour.
     * Processes trips that have exceeded maximum duration.
     */
    @Scheduled(fixedRate = 3600000) // 1 hour
    public void handleTripTimeouts() {
        try {
            log.debug("Handling trip timeouts");
            
            List<Trip> ongoingTrips = tripLifecycleService.getTripsRequiringAttention();
            int timeoutActions = 0;
            
            for (Trip trip : ongoingTrips) {
                if (tripLifecycleService.handleTripTimeout(trip)) {
                    timeoutActions++;
                }
            }
            
            if (timeoutActions > 0) {
                log.warn("Handled {} trip timeout scenarios", timeoutActions);
            }
            
        } catch (Exception e) {
            log.error("Error handling trip timeouts: {}", e.getMessage(), e);
        }
    }

    /**
     * Generate system health report every 30 minutes.
     * Monitors overall trip system health and performance.
     */
    @Scheduled(fixedRate = 1800000) // 30 minutes
    public void generateSystemHealthReport() {
        try {
            log.debug("Generating trip system health report");
            
            TripMonitoringService.TripSystemHealth health = tripMonitoringService.checkTripSystemHealth();
            
            if (!health.isHealthy()) {
                log.warn("Trip system health degraded: status={}, issues={}", 
                        health.getStatus(), health.getIssues());
                
                // Log specific issues
                health.getIssues().forEach(issue -> log.warn("Health issue: {}", issue));
                
                // In production, this would trigger alerts
            } else {
                log.debug("Trip system health: {}", health.getStatus());
            }
            
        } catch (Exception e) {
            log.error("Error generating system health report: {}", e.getMessage(), e);
        }
    }

    /**
     * Cleanup old trip data (placeholder for future implementation).
     * Could archive completed trips, clean up route data, etc.
     */
    @Scheduled(cron = "0 0 2 * * *") // Daily at 2 AM
    public void performDailyCleanup() {
        try {
            log.debug("Performing daily trip data cleanup");
            
            // Future implementation could include:
            // - Archive old completed trips
            // - Cleanup excessive route data
            // - Remove orphaned records
            // - Compress historical data
            
            log.debug("Daily cleanup completed");
            
        } catch (Exception e) {
            log.error("Error during daily cleanup: {}", e.getMessage(), e);
        }
    }
}