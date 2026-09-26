package com.ethioloadai.tracking.service;

import com.ethioloadai.tracking.dto.LocationUpdateRequest;
import com.ethioloadai.tracking.entity.CurrentVehicleLocation;
import com.ethioloadai.tracking.entity.VehicleLocationHistory;
import com.ethioloadai.tracking.repository.CurrentVehicleLocationRepository;
import com.ethioloadai.tracking.repository.VehicleLocationHistoryRepository;
import com.ethioloadai.trip.entity.Trip;
import com.ethioloadai.user.entity.User;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Implementation of location persistence service with two-tier architecture.
 * 
 * Optimizes for both real-time access and historical analytics by separating
 * current location (hot data) from historical tracking (cold data).
 */
@Service
@RequiredArgsConstructor
public class LocationPersistenceServiceImpl implements LocationPersistenceService {
    private static final Logger log = LoggerFactory.getLogger(LocationPersistenceServiceImpl.class);

    private final CurrentVehicleLocationRepository currentLocationRepository;
    private final VehicleLocationHistoryRepository historyLocationRepository;
    private final ObjectMapper objectMapper;

    // Configuration constants
    private static final BigDecimal DEFAULT_ACCURACY_FILTER = new BigDecimal("100"); // 100 meters
    private static final BigDecimal ANOMALY_SPEED_THRESHOLD = new BigDecimal("150"); // 150 km/h
    private static final int BATCH_SIZE = 100;
    private static final int STALE_THRESHOLD_HOURS = 24;

    @Override
    @Transactional
    public LocationUpdateResult processLocationUpdate(LocationUpdateRequest locationRequest, Trip trip, User user) {
        long startTime = System.currentTimeMillis();
        
        try {
            log.debug("Processing location update for vehicle {} at {},{}", 
                     locationRequest.getVehicleId(), locationRequest.getLatitude(), locationRequest.getLongitude());

            // Create current location record
            CurrentVehicleLocation currentLocation = buildCurrentLocation(locationRequest, trip);
            
            // Create historical location record  
            VehicleLocationHistory historyLocation = buildHistoryLocation(locationRequest, trip);

            // Process current location update (upsert for real-time access)
            boolean currentUpdated = updateCurrentLocation(currentLocation);
            
            // Process historical location asynchronously to avoid blocking real-time updates
            CompletableFuture<Boolean> historyFuture = CompletableFuture.supplyAsync(() -> {
                try {
                    VehicleLocationHistory saved = historyLocationRepository.save(historyLocation);
                    return saved.getId() != null;
                } catch (Exception e) {
                    log.error("Failed to save historical location for vehicle {}: {}", 
                             locationRequest.getVehicleId(), e.getMessage());
                    return false;
                }
            });

            // Wait for both operations to complete
            boolean historyCreated;
            try {
                historyCreated = historyFuture.get();
            } catch (Exception e) {
                log.warn("Historical location save failed for vehicle {}: {}", 
                        locationRequest.getVehicleId(), e.getMessage());
                historyCreated = false;
            }

            long processingTime = System.currentTimeMillis() - startTime;
            
            log.debug("Location update completed for vehicle {} in {}ms (current: {}, history: {})", 
                     locationRequest.getVehicleId(), processingTime, currentUpdated, historyCreated);

            return LocationUpdateResult.success(currentLocation, historyLocation, processingTime);

        } catch (Exception e) {
            long processingTime = System.currentTimeMillis() - startTime;
            log.error("Failed to process location update for vehicle {}: {}", 
                     locationRequest.getVehicleId(), e.getMessage(), e);
            
            return LocationUpdateResult.failure("Location update failed: " + e.getMessage(), processingTime);
        }
    }

    @Override
    public Optional<CurrentVehicleLocation> getCurrentLocation(Long vehicleId) {
        log.debug("Retrieving current location for vehicle {}", vehicleId);
        return currentLocationRepository.findByVehicleId(vehicleId);
    }

    @Override
    public List<CurrentVehicleLocation> getCurrentLocations(List<Long> vehicleIds) {
        log.debug("Retrieving current locations for {} vehicles", vehicleIds.size());
        return currentLocationRepository.findAllById(vehicleIds);
    }

    @Override
    public List<CurrentVehicleLocation> getNearbyVehicles(BigDecimal latitude, BigDecimal longitude, BigDecimal radiusKm) {
        log.debug("Finding vehicles within {}km of {},{}", radiusKm, latitude, longitude);
        return currentLocationRepository.findVehiclesWithinRadius(latitude, longitude, radiusKm);
    }

    @Override
    public Page<VehicleLocationHistory> getLocationHistory(Long vehicleId, LocalDateTime startTime, 
                                                          LocalDateTime endTime, Pageable pageable) {
        log.debug("Retrieving location history for vehicle {} from {} to {}", vehicleId, startTime, endTime);
        
        if (startTime != null && endTime != null) {
            return historyLocationRepository.findByVehicleIdAndTimestampBetween(vehicleId, startTime, endTime, pageable);
        } else {
            // If no time range specified, get recent history
            List<VehicleLocationHistory> recentHistory = historyLocationRepository.findRecentByVehicleId(
                vehicleId, pageable.getPageSize());
            return new org.springframework.data.domain.PageImpl<>(recentHistory, pageable, recentHistory.size());
        }
    }

    @Override
    public Page<VehicleLocationHistory> getTripRoute(Long tripId, BigDecimal accuracyFilter, Pageable pageable) {
        log.debug("Retrieving trip route for trip {} with accuracy filter {}", tripId, accuracyFilter);
        return historyLocationRepository.findByTripId(tripId, pageable);
    }

    @Override
    public RouteAnalytics calculateRouteAnalytics(Long vehicleId, LocalDateTime startTime, LocalDateTime endTime) {
        log.debug("Calculating route analytics for vehicle {} from {} to {}", vehicleId, startTime, endTime);
        
        try {
            // Get location history for analysis
            List<VehicleLocationHistory> locations = historyLocationRepository
                .findAccurateLocationsByVehicleAndTimeRange(vehicleId, startTime, endTime, DEFAULT_ACCURACY_FILTER);

            if (locations.isEmpty()) {
                return new RouteAnalytics(vehicleId, startTime, endTime, BigDecimal.ZERO, BigDecimal.ZERO,
                                        BigDecimal.ZERO, BigDecimal.ZERO, 0L, 0L, 0, 0, 
                                        Collections.emptyList(), Collections.emptyList());
            }

            // Calculate total distance
            BigDecimal totalDistance = historyLocationRepository.calculateTotalDistanceKm(vehicleId, startTime, endTime);
            
            // Calculate time metrics
            long totalTimeMinutes = java.time.Duration.between(startTime, endTime).toMinutes();
            
            // Analyze route segments and stops
            List<RouteSegment> segments = analyzeRouteSegments(locations);
            List<StopEvent> stops = detectStops(locations);
            
            // Calculate driving vs stopped time
            long stoppedTimeMinutes = stops.stream()
                .mapToLong(StopEvent::durationMinutes)
                .sum();
            long drivingTimeMinutes = Math.max(0, totalTimeMinutes - stoppedTimeMinutes);
            
            // Calculate speeds
            BigDecimal averageSpeed = drivingTimeMinutes > 0 ? 
                totalDistance.multiply(BigDecimal.valueOf(60)).divide(BigDecimal.valueOf(drivingTimeMinutes), 2, java.math.RoundingMode.HALF_UP) :
                BigDecimal.ZERO;
            
            BigDecimal maxSpeed = locations.stream()
                .filter(loc -> loc.getSpeed() != null)
                .map(VehicleLocationHistory::getSpeed)
                .max(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);

            return new RouteAnalytics(vehicleId, startTime, endTime, totalDistance, totalDistance,
                                    averageSpeed, maxSpeed, drivingTimeMinutes, stoppedTimeMinutes,
                                    locations.size(), locations.size(), segments, stops);
            
        } catch (Exception e) {
            log.error("Failed to calculate route analytics for vehicle {}: {}", vehicleId, e.getMessage(), e);
            throw new RuntimeException("Route analytics calculation failed", e);
        }
    }

    @Override
    public List<LocationAnomaly> detectLocationAnomalies(Long vehicleId, LocalDateTime startTime, LocalDateTime endTime) {
        log.debug("Detecting location anomalies for vehicle {} from {} to {}", vehicleId, startTime, endTime);
        
        List<VehicleLocationHistory> anomalies = historyLocationRepository
            .findLocationAnomalies(vehicleId, startTime, endTime, ANOMALY_SPEED_THRESHOLD);
        
        return anomalies.stream()
            .map(this::convertToAnomaly)
            .toList();
    }

    @Override
    @Transactional
    public ArchivalResult archiveOldLocations(LocalDateTime beforeDate) {
        log.info("Starting archival of location data before {}", beforeDate);
        long startTime = System.currentTimeMillis();
        
        try {
            // Count records to be archived
            long recordCount = historyLocationRepository.countByVehicleIdAndTimestampBetween(
                null, LocalDateTime.of(2000, 1, 1, 0, 0), beforeDate);
            
            // Delete old records (in production, this would move to archive storage)
            int archivedCount = historyLocationRepository.deleteOldLocations(beforeDate);
            
            long processingTime = System.currentTimeMillis() - startTime;
            
            log.info("Archived {} location records in {}ms", archivedCount, processingTime);
            
            return new ArchivalResult(archivedCount, archivedCount * 1024L, // Estimate storage freed
                                    beforeDate.minusYears(1), beforeDate, processingTime, "SUCCESS");
            
        } catch (Exception e) {
            long processingTime = System.currentTimeMillis() - startTime;
            log.error("Archival failed: {}", e.getMessage(), e);
            return new ArchivalResult(0, 0, null, null, processingTime, "FAILED: " + e.getMessage());
        }
    }

    @Override
    @Transactional
    public CleanupResult cleanupStaleCurrentLocations(LocalDateTime staleThreshold) {
        log.info("Cleaning up stale current locations before {}", staleThreshold);
        long startTime = System.currentTimeMillis();
        
        try {
            // Find stale locations
            List<CurrentVehicleLocation> staleLocations = currentLocationRepository.findByTimestampBefore(staleThreshold);
            LocalDateTime oldestTimestamp = staleLocations.stream()
                .map(CurrentVehicleLocation::getTimestamp)
                .min(LocalDateTime::compareTo)
                .orElse(null);
            
            // Delete stale locations
            int deletedCount = currentLocationRepository.deleteStaleLocations(staleThreshold);
            
            long processingTime = System.currentTimeMillis() - startTime;
            Set<Long> affectedVehicles = staleLocations.stream()
                .map(CurrentVehicleLocation::getVehicleId)
                .collect(java.util.stream.Collectors.toSet());
            
            log.info("Cleaned up {} stale current locations for {} vehicles in {}ms", 
                    deletedCount, affectedVehicles.size(), processingTime);
            
            return new CleanupResult(deletedCount, affectedVehicles.size(), oldestTimestamp, 
                                   processingTime, "SUCCESS");
            
        } catch (Exception e) {
            long processingTime = System.currentTimeMillis() - startTime;
            log.error("Cleanup failed: {}", e.getMessage(), e);
            return new CleanupResult(0, 0, null, processingTime, "FAILED: " + e.getMessage());
        }
    }

    @Override
    public PersistenceStatistics getStatistics() {
        log.debug("Generating location persistence statistics");
        
        try {
            // Get current location statistics
            Object[] currentStats = currentLocationRepository.getLocationStatistics();
            long currentLocationCount = ((Number) currentStats[0]).longValue();
            long activeVehicleCount = ((Number) currentStats[1]).longValue();
            
            // Get historical statistics
            LocalDateTime oneDayAgo = LocalDateTime.now().minusDays(1);
            Object[] historyStats = historyLocationRepository.getLocationStatistics(oneDayAgo, LocalDateTime.now());
            long historyLocationCount = ((Number) historyStats[0]).longValue();
            BigDecimal averageAccuracy = historyStats[2] != null ? 
                new BigDecimal(historyStats[2].toString()) : BigDecimal.ZERO;
            
            // Estimate storage usage (rough calculation)
            BigDecimal storageUsedGB = BigDecimal.valueOf(historyLocationCount * 512L / (1024 * 1024 * 1024)); // ~512 bytes per record
            
            return new PersistenceStatistics(currentLocationCount, historyLocationCount, activeVehicleCount,
                                           currentLocationCount, averageAccuracy, storageUsedGB,
                                           LocalDateTime.now().minusYears(2), LocalDateTime.now(),
                                           0.95, historyLocationCount); // Mock values for some fields
            
        } catch (Exception e) {
            log.error("Failed to generate statistics: {}", e.getMessage(), e);
            return new PersistenceStatistics(0, 0, 0, 0, BigDecimal.ZERO, BigDecimal.ZERO,
                                           null, null, 0.0, 0);
        }
    }

    @Override
    @Transactional
    public BatchLocationResult processBatchLocationUpdates(List<LocationUpdateRequest> locationUpdates) {
        log.info("Processing batch of {} location updates", locationUpdates.size());
        long startTime = System.currentTimeMillis();
        
        int successCount = 0;
        List<String> errors = new ArrayList<>();
        
        for (LocationUpdateRequest request : locationUpdates) {
            try {
                // Process each update (simplified for batch processing)
                LocationUpdateResult result = processLocationUpdate(request, null, null);
                if (result.success()) {
                    successCount++;
                } else {
                    errors.add("Vehicle " + request.getVehicleId() + ": " + result.message());
                }
            } catch (Exception e) {
                errors.add("Vehicle " + request.getVehicleId() + ": " + e.getMessage());
            }
        }
        
        long processingTime = System.currentTimeMillis() - startTime;
        int failedCount = locationUpdates.size() - successCount;
        
        log.info("Batch processing completed: {} successful, {} failed in {}ms", 
                successCount, failedCount, processingTime);
        
        return new BatchLocationResult(locationUpdates.size(), successCount, failedCount, 
                                     processingTime, errors);
    }

    // ── Private Helper Methods ───────────────────────────────────────────────

    private CurrentVehicleLocation buildCurrentLocation(LocationUpdateRequest request, Trip trip) {
        return CurrentVehicleLocation.builder()
            .vehicleId(request.getVehicleId())
            .trip(trip)
            .latitude(request.getLatitude())
            .longitude(request.getLongitude())
            .accuracy(request.getAccuracy())
            .speed(request.getSpeed())
            .heading(request.getHeading())
            .timestamp(request.getTimestamp())
            .metadata(buildMetadata(request))
            .build();
    }

    private VehicleLocationHistory buildHistoryLocation(LocationUpdateRequest request, Trip trip) {
        return VehicleLocationHistory.builder()
            .vehicleId(request.getVehicleId())
            .tripId(trip != null ? trip.getId() : null)
            .trip(trip)
            .latitude(request.getLatitude())
            .longitude(request.getLongitude())
            .accuracy(request.getAccuracy())
            .speed(request.getSpeed())
            .heading(request.getHeading())
            .timestamp(request.getTimestamp())
            .metadata(buildMetadata(request))
            .build();
    }

    private Map<String, Object> buildMetadata(LocationUpdateRequest request) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("source", "gps_update");
        metadata.put("processed_at", LocalDateTime.now());
        return metadata;
    }

    private boolean updateCurrentLocation(CurrentVehicleLocation location) {
        try {
            String metadataJson = objectMapper.writeValueAsString(location.getMetadata());
            int result = currentLocationRepository.upsertLocation(
                location.getVehicleId(),
                location.getTrip() != null ? location.getTrip().getId() : null,
                location.getLatitude(),
                location.getLongitude(),
                location.getAltitude(),
                location.getAccuracy(),
                location.getSpeed(),
                location.getHeading(),
                location.getTimestamp(),
                location.getBatteryLevel(),
                location.getSignalStrength(),
                metadataJson
            );
            return result > 0;
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize metadata for vehicle {}: {}", location.getVehicleId(), e.getMessage());
            return false;
        }
    }

    private List<RouteSegment> analyzeRouteSegments(List<VehicleLocationHistory> locations) {
        // Simplified segment analysis - in production this would be more sophisticated
        List<RouteSegment> segments = new ArrayList<>();
        
        for (int i = 1; i < locations.size(); i++) {
            VehicleLocationHistory prev = locations.get(i - 1);
            VehicleLocationHistory curr = locations.get(i);
            
            BigDecimal distance = prev.calculateDistanceTo(curr);
            BigDecimal speed = prev.calculateSpeedTo(curr);
            
            String segmentType = speed.compareTo(new BigDecimal("5")) > 0 ? "DRIVING" : "STOPPED";
            
            segments.add(new RouteSegment(prev.getTimestamp(), curr.getTimestamp(),
                                        prev.getLatitude(), prev.getLongitude(),
                                        curr.getLatitude(), curr.getLongitude(),
                                        distance, speed, segmentType));
        }
        
        return segments;
    }

    private List<StopEvent> detectStops(List<VehicleLocationHistory> locations) {
        // Simplified stop detection - in production this would use clustering algorithms
        List<StopEvent> stops = new ArrayList<>();
        
        // Mock implementation for now
        return stops;
    }

    private LocationAnomaly convertToAnomaly(VehicleLocationHistory location) {
        return new LocationAnomaly(
            location.getVehicleId(),
            location.getTimestamp(),
            location.getLatitude(),
            location.getLongitude(),
            "SPEED_JUMP",
            new BigDecimal("0.8"), // Mock severity
            "Detected unrealistic speed change",
            location.getSpeed(),
            new BigDecimal("80") // Mock expected value
        );
    }
}