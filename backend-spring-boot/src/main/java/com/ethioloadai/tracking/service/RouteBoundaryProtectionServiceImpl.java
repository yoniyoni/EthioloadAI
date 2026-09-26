package com.ethioloadai.tracking.service;

import com.ethioloadai.trip.entity.Trip;
import com.ethioloadai.trip.repository.TripRepository;
import com.ethioloadai.trip.service.RouteDataService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Implementation of route boundary protection with intelligent data management strategies.
 * 
 * Provides comprehensive protection against unbounded route data growth while
 * preserving data quality and route integrity for analytics and compliance.
 */
@Service
@RequiredArgsConstructor
public class RouteBoundaryProtectionServiceImpl implements RouteBoundaryProtectionService {
    private static final Logger log = LoggerFactory.getLogger(RouteBoundaryProtectionServiceImpl.class);

    private final TripRepository tripRepository;
    private final RouteDataService routeDataService;
    
    // Configuration storage (in production, this would be in database/config service)
    private final Map<Trip.TripType, BoundarySettings> boundaryConfigs = new ConcurrentHashMap<>();
    
    // Algorithm constants
    private static final double DOUGLAS_PEUCKER_DEFAULT_TOLERANCE = 0.001; // ~111 meters
    private static final int MIN_POINTS_TO_PRESERVE = 10;
    private static final int EMERGENCY_CLEANUP_MAX_POINTS = 100;
    private static final double CRITICAL_STORAGE_THRESHOLD_MB = 50.0;

    @Override
    @Transactional
    public BoundaryEnforcementResult enforceRouteBoundaries(Trip trip) {
        long startTime = System.currentTimeMillis();
        
        log.debug("Enforcing route boundaries for trip {}", trip.getId());
        
        // Get current health metrics
        RouteHealthMetrics healthBefore = getRouteHealthMetrics(trip);
        
        // Get applicable boundary settings
        BoundarySettings settings = getBoundarySettings(trip.getTripType());
        
        List<String> actionsPerformed = new ArrayList<>();
        int totalPointsRemoved = 0;
        int totalPointsArchived = 0;
        int totalPointsCompressed = 0;
        
        // Check if boundaries are exceeded
        BoundaryCheckResult boundaryCheck = checkRouteBoundaries(trip);
        
        if (!boundaryCheck.exceedsLimit()) {
            log.debug("Trip {} is within boundaries, no action needed", trip.getId());
            long processingTime = System.currentTimeMillis() - startTime;
            return new BoundaryEnforcementResult(false, actionsPerformed, 0, 0, 0, 
                                               healthBefore, healthBefore, processingTime, "NO_ACTION_NEEDED");
        }
        
        try {
            // Step 1: Archive old data if enabled and needed
            if (settings.enableAutoArchival() && boundaryCheck.currentPointCount() > settings.archiveThreshold()) {
                RouteArchivalResult archivalResult = archiveOldRouteData(trip, settings.retentionHours());
                if (archivalResult.success()) {
                    totalPointsArchived = archivalResult.pointsArchived();
                    actionsPerformed.add("ARCHIVED_OLD_DATA");
                    log.info("Archived {} points for trip {}", totalPointsArchived, trip.getId());
                }
                
                // Reload trip after archival
                trip = tripRepository.findById(trip.getId()).orElse(trip);
            }
            
            // Step 2: Apply compression if still needed and enabled
            BoundaryCheckResult postArchivalCheck = checkRouteBoundaries(trip);
            if (settings.enableAutoCompression() && postArchivalCheck.currentPointCount() > settings.compressionThreshold()) {
                int compressionLevel = calculateCompressionLevel(postArchivalCheck, settings);
                RouteCompressionResult compressionResult = compressRoute(trip, compressionLevel);
                
                totalPointsCompressed = compressionResult.pointsRemoved();
                actionsPerformed.add("COMPRESSED_ROUTE");
                log.info("Compressed route for trip {}: {} → {} points", 
                        trip.getId(), compressionResult.originalPoints(), compressionResult.compressedPoints());
                
                // Reload trip after compression
                trip = tripRepository.findById(trip.getId()).orElse(trip);
            }
            
            // Step 3: Emergency cleanup if critically over limits
            BoundaryCheckResult finalCheck = checkRouteBoundaries(trip);
            if (settings.enableEmergencyCleanup() && finalCheck.currentPointCount() > settings.emergencyThreshold()) {
                EmergencyCleanupResult cleanupResult = performEmergencyCleanup(trip);
                if (cleanupResult.cleanupPerformed()) {
                    totalPointsRemoved = cleanupResult.pointsRemoved();
                    actionsPerformed.add("EMERGENCY_CLEANUP");
                    log.warn("Performed emergency cleanup for trip {}: removed {} points", 
                            trip.getId(), totalPointsRemoved);
                }
            }
            
            // Get final health metrics
            RouteHealthMetrics healthAfter = getRouteHealthMetrics(trip);
            long processingTime = System.currentTimeMillis() - startTime;
            
            String status = actionsPerformed.isEmpty() ? "NO_ACTION_TAKEN" : "BOUNDARIES_ENFORCED";
            
            log.info("Boundary enforcement completed for trip {} in {}ms: {}", 
                    trip.getId(), processingTime, String.join(", ", actionsPerformed));
            
            return new BoundaryEnforcementResult(true, actionsPerformed, 
                                               totalPointsRemoved, totalPointsArchived, totalPointsCompressed,
                                               healthBefore, healthAfter, processingTime, status);
            
        } catch (Exception e) {
            long processingTime = System.currentTimeMillis() - startTime;
            log.error("Failed to enforce route boundaries for trip {}: {}", trip.getId(), e.getMessage(), e);
            
            return new BoundaryEnforcementResult(true, actionsPerformed,
                                               totalPointsRemoved, totalPointsArchived, totalPointsCompressed,
                                               healthBefore, healthBefore, processingTime, 
                                               "ERROR: " + e.getMessage());
        }
    }

    @Override
    public BoundaryCheckResult checkRouteBoundaries(Trip trip) {
        List<Map<String, Object>> routeData = trip.getRouteData();
        int currentPoints = routeData != null ? routeData.size() : 0;
        
        BoundarySettings settings = getBoundarySettings(trip.getTripType());
        
        boolean exceedsLimit = currentPoints > settings.maxPoints();
        double utilizationPercent = (double) currentPoints / settings.maxPoints() * 100;
        
        String riskLevel = calculateRiskLevel(utilizationPercent);
        List<String> concerns = identifyConcerns(currentPoints, settings, utilizationPercent);
        
        log.debug("Boundary check for trip {}: {}/{} points ({}% utilization, risk: {})", 
                 trip.getId(), currentPoints, settings.maxPoints(), 
                 String.format("%.1f", utilizationPercent), riskLevel);
        
        return new BoundaryCheckResult(currentPoints, settings.maxPoints(), exceedsLimit,
                                     utilizationPercent, riskLevel, concerns, settings);
    }

    @Override
    @Transactional
    public RouteCompressionResult compressRoute(Trip trip, int compressionLevel) {
        log.info("Compressing route for trip {} with level {}", trip.getId(), compressionLevel);
        
        long startTime = System.currentTimeMillis();
        List<Map<String, Object>> originalRouteData = new ArrayList<>(trip.getRouteData());
        int originalPoints = originalRouteData.size();
        
        if (originalPoints < MIN_POINTS_TO_PRESERVE) {
            log.debug("Trip {} has too few points for compression", trip.getId());
            return new RouteCompressionResult(originalPoints, originalPoints, 0, 1.0, 1.0,
                                            "NONE", 0, List.of());
        }
        
        try {
            // Convert to GPS data for processing
            List<RouteDataService.GpsLocationData> gpsRoute = originalRouteData.stream()
                .map(this::convertMapToGps)
                .collect(Collectors.toList());
            
            // Calculate compression tolerance based on level
            double tolerance = calculateCompressionTolerance(compressionLevel);
            
            // Apply Douglas-Peucker algorithm
            List<RouteDataService.GpsLocationData> compressedRoute = 
                applyDouglasPeucker(gpsRoute, tolerance);
            
            // Preserve important features
            BoundarySettings settings = getBoundarySettings(trip.getTripType());
            compressedRoute = preserveImportantFeatures(gpsRoute, compressedRoute, settings.preserveFeatures());
            
            // Convert back to map format and update trip
            List<Map<String, Object>> compressedRouteData = compressedRoute.stream()
                .map(this::convertGpsToMap)
                .collect(Collectors.toList());
            
            trip.setRouteData(compressedRouteData);
            tripRepository.save(trip);
            
            // Calculate results
            int compressedPoints = compressedRouteData.size();
            int pointsRemoved = originalPoints - compressedPoints;
            double compressionRatio = (double) compressedPoints / originalPoints;
            double qualityRetention = calculateQualityRetention(gpsRoute, compressedRoute);
            
            long processingTime = System.currentTimeMillis() - startTime;
            
            log.info("Route compression completed for trip {}: {} → {} points ({} removed, {:.2f} ratio) in {}ms",
                    trip.getId(), originalPoints, compressedPoints, pointsRemoved, compressionRatio, processingTime);
            
            return new RouteCompressionResult(originalPoints, compressedPoints, pointsRemoved,
                                            compressionRatio, qualityRetention, "DOUGLAS_PEUCKER",
                                            processingTime, settings.preserveFeatures());
            
        } catch (Exception e) {
            log.error("Failed to compress route for trip {}: {}", trip.getId(), e.getMessage(), e);
            long processingTime = System.currentTimeMillis() - startTime;
            return new RouteCompressionResult(originalPoints, originalPoints, 0, 1.0, 0.0,
                                            "ERROR", processingTime, List.of());
        }
    }

    @Override
    @Transactional
    public RouteArchivalResult archiveOldRouteData(Trip trip, int retentionHours) {
        log.info("Archiving old route data for trip {} (retention: {} hours)", trip.getId(), retentionHours);
        
        LocalDateTime cutoffTime = LocalDateTime.now().minusHours(retentionHours);
        List<Map<String, Object>> routeData = new ArrayList<>(trip.getRouteData());
        
        if (routeData.isEmpty()) {
            return new RouteArchivalResult(0, 0, null, cutoffTime, 0L, "NONE", true);
        }
        
        try {
            // Separate points into archive and retain lists
            List<Map<String, Object>> pointsToRetain = new ArrayList<>();
            List<Map<String, Object>> pointsToArchive = new ArrayList<>();
            LocalDateTime oldestArchivedTimestamp = null;
            
            for (Map<String, Object> point : routeData) {
                LocalDateTime pointTimestamp = parseTimestampFromPoint(point);
                
                if (pointTimestamp.isAfter(cutoffTime)) {
                    pointsToRetain.add(point);
                } else {
                    pointsToArchive.add(point);
                    if (oldestArchivedTimestamp == null || pointTimestamp.isBefore(oldestArchivedTimestamp)) {
                        oldestArchivedTimestamp = pointTimestamp;
                    }
                }
            }
            
            // Always keep minimum recent points for route continuity
            if (pointsToRetain.size() < MIN_POINTS_TO_PRESERVE && !routeData.isEmpty()) {
                int additionalPointsNeeded = Math.min(MIN_POINTS_TO_PRESERVE - pointsToRetain.size(), 
                                                     pointsToArchive.size());
                
                // Move the most recent archived points back to retain list
                Collections.reverse(pointsToArchive); // Most recent first
                for (int i = 0; i < additionalPointsNeeded; i++) {
                    pointsToRetain.add(0, pointsToArchive.remove(0));
                }
                Collections.reverse(pointsToArchive); // Restore chronological order
            }
            
            // Update trip with retained points
            trip.setRouteData(pointsToRetain);
            tripRepository.save(trip);
            
            // Estimate storage freed (rough calculation: ~200 bytes per point)
            long storageFreed = pointsToArchive.size() * 200L;
            
            log.info("Archived {} points for trip {} (retained: {}, freed: {} bytes)",
                    pointsToArchive.size(), trip.getId(), pointsToRetain.size(), storageFreed);
            
            return new RouteArchivalResult(pointsToArchive.size(), pointsToRetain.size(),
                                         oldestArchivedTimestamp, cutoffTime, storageFreed,
                                         "TRIP_ROUTE_DATA", true);
            
        } catch (Exception e) {
            log.error("Failed to archive route data for trip {}: {}", trip.getId(), e.getMessage(), e);
            return new RouteArchivalResult(0, routeData.size(), null, cutoffTime, 0L,
                                         "ERROR", false);
        }
    }

    @Override
    public RouteHealthMetrics getRouteHealthMetrics(Trip trip) {
        List<Map<String, Object>> routeData = trip.getRouteData();
        
        if (routeData == null || routeData.isEmpty()) {
            return new RouteHealthMetrics(0, 0, 0.0, 0.0, 1.0, 1.0, 
                                        null, null, List.of(), "EXCELLENT");
        }
        
        int totalPoints = routeData.size();
        
        // Count recent points (last 2 hours)
        LocalDateTime twoHoursAgo = LocalDateTime.now().minusHours(2);
        int recentPoints = (int) routeData.stream()
            .mapToLong(point -> {
                LocalDateTime timestamp = parseTimestampFromPoint(point);
                return timestamp.isAfter(twoHoursAgo) ? 1 : 0;
            })
            .sum();
        
        // Calculate metrics
        LocalDateTime oldestPoint = parseTimestampFromPoint(routeData.get(0));
        LocalDateTime newestPoint = parseTimestampFromPoint(routeData.get(routeData.size() - 1));
        
        double hours = java.time.Duration.between(oldestPoint, newestPoint).toMinutes() / 60.0;
        double averagePointsPerHour = hours > 0 ? totalPoints / hours : totalPoints;
        
        // Estimate storage (rough calculation: ~200 bytes per point)
        double storageUsedMB = (totalPoints * 200.0) / (1024 * 1024);
        
        // Calculate performance and quality scores
        double queryPerformanceScore = calculateQueryPerformanceScore(totalPoints);
        double dataQualityScore = calculateDataQualityScore(routeData);
        
        // Identify health issues
        List<String> healthIssues = identifyHealthIssues(totalPoints, storageUsedMB, 
                                                        queryPerformanceScore, dataQualityScore);
        
        // Determine overall health
        String overallHealth = determineOverallHealth(totalPoints, queryPerformanceScore, 
                                                    dataQualityScore, healthIssues.size());
        
        return new RouteHealthMetrics(totalPoints, recentPoints, averagePointsPerHour, 
                                    storageUsedMB, queryPerformanceScore, dataQualityScore,
                                    oldestPoint, newestPoint, healthIssues, overallHealth);
    }

    @Override
    @Transactional
    public EmergencyCleanupResult performEmergencyCleanup(Trip trip) {
        log.warn("Performing emergency cleanup for trip {}", trip.getId());
        
        List<Map<String, Object>> routeData = new ArrayList<>(trip.getRouteData());
        int originalPoints = routeData.size();
        
        if (originalPoints <= EMERGENCY_CLEANUP_MAX_POINTS) {
            return new EmergencyCleanupResult(false, "NO_CLEANUP_NEEDED", 0, originalPoints,
                                            0.0, List.of(), "Route within emergency limits", true);
        }
        
        try {
            // Emergency strategy: Keep only the most recent points and key waypoints
            List<Map<String, Object>> cleanedData = new ArrayList<>();
            
            // Keep recent points (last 20% or minimum threshold)
            int recentPointsToKeep = Math.max(MIN_POINTS_TO_PRESERVE, 
                                            Math.min(EMERGENCY_CLEANUP_MAX_POINTS, (int)(originalPoints * 0.2)));
            int startIndex = Math.max(0, originalPoints - recentPointsToKeep);
            
            // Add recent points
            for (int i = startIndex; i < originalPoints; i++) {
                cleanedData.add(routeData.get(i));
            }
            
            // Add important waypoints from earlier data (every Nth point)
            int remainingCapacity = EMERGENCY_CLEANUP_MAX_POINTS - cleanedData.size();
            if (startIndex > 0 && remainingCapacity > 0) {
                int waypointInterval = Math.max(1, startIndex / remainingCapacity);
                List<Map<String, Object>> waypoints = new ArrayList<>();
                
                for (int i = 0; i < startIndex; i += waypointInterval) {
                    if (waypoints.size() < remainingCapacity) {
                        waypoints.add(routeData.get(i));
                    }
                }
                
                // Add waypoints at the beginning to maintain chronological order
                waypoints.addAll(cleanedData);
                cleanedData = waypoints;
            }
            
            // Sort by timestamp to maintain chronological order
            cleanedData.sort((a, b) -> {
                LocalDateTime tsA = parseTimestampFromPoint(a);
                LocalDateTime tsB = parseTimestampFromPoint(b);
                return tsA.compareTo(tsB);
            });
            
            // Update trip
            trip.setRouteData(cleanedData);
            tripRepository.save(trip);
            
            int pointsRemoved = originalPoints - cleanedData.size();
            double dataLossPercentage = ((double) pointsRemoved / originalPoints) * 100;
            
            log.warn("Emergency cleanup completed for trip {}: {} → {} points ({:.1f}% data loss)",
                    trip.getId(), originalPoints, cleanedData.size(), dataLossPercentage);
            
            return new EmergencyCleanupResult(true, "RECENT_POINTS_AND_WAYPOINTS", 
                                            pointsRemoved, cleanedData.size(), dataLossPercentage,
                                            List.of("RECENT_ROUTE", "KEY_WAYPOINTS"), 
                                            "Significant data loss but route structure preserved",
                                            true);
            
        } catch (Exception e) {
            log.error("Emergency cleanup failed for trip {}: {}", trip.getId(), e.getMessage(), e);
            return new EmergencyCleanupResult(false, "ERROR", 0, originalPoints, 0.0,
                                            List.of(), "Emergency cleanup failed: " + e.getMessage(), false);
        }
    }

    @Override
    public List<OptimizationRecommendation> getOptimizationRecommendations(Trip trip) {
        RouteHealthMetrics health = getRouteHealthMetrics(trip);
        BoundaryCheckResult boundaryCheck = checkRouteBoundaries(trip);
        List<OptimizationRecommendation> recommendations = new ArrayList<>();
        
        // Compression recommendation
        if (health.totalPoints() > 300 && health.dataQualityScore() < 0.8) {
            recommendations.add(new OptimizationRecommendation(
                "COMPRESS", "MEDIUM",
                "Route has many points with potentially redundant data. Compression can reduce storage by 30-50% with minimal quality loss.",
                health.totalPoints() / 3, 0.35, 0.05, 
                List.of("Douglas-Peucker algorithm", "Preserve key waypoints"),
                true
            ));
        }
        
        // Archival recommendation
        if (health.totalPoints() > boundaryCheck.appliedSettings().archiveThreshold()) {
            recommendations.add(new OptimizationRecommendation(
                "ARCHIVE", "HIGH",
                "Route data exceeds archival threshold. Moving old data to archive can improve query performance.",
                health.totalPoints() - 100, 0.60, 0.0,
                List.of("Automated archival process", "Preserve recent 24 hours"),
                true
            ));
        }
        
        // Emergency cleanup recommendation
        if (boundaryCheck.riskLevel().equals("CRITICAL")) {
            recommendations.add(new OptimizationRecommendation(
                "CLEANUP", "URGENT",
                "Route data is at critical levels. Immediate cleanup required to prevent system issues.",
                health.totalPoints() - EMERGENCY_CLEANUP_MAX_POINTS, 0.80, 0.30,
                List.of("Manual approval required", "Significant data loss possible"),
                false
            ));
        }
        
        return recommendations;
    }

    @Override
    public void configureBoundarySettings(Trip.TripType tripType, BoundarySettings settings) {
        log.info("Configuring boundary settings for trip type {}: maxPoints={}, archiveThreshold={}", 
                tripType, settings.maxPoints(), settings.archiveThreshold());
        
        boundaryConfigs.put(tripType, settings);
    }

    // ── Private Helper Methods ───────────────────────────────────────────────

    private BoundarySettings getBoundarySettings(Trip.TripType tripType) {
        return boundaryConfigs.computeIfAbsent(tripType, type -> {
            // Return default settings based on trip type
            return switch (type) {
                case SINGLE -> BoundarySettings.singleTripDefaults();
                case MULTI_STOP -> BoundarySettings.multiStopDefaults();
                default -> BoundarySettings.singleTripDefaults();
            };
        });
    }

    private String calculateRiskLevel(double utilizationPercent) {
        if (utilizationPercent >= 100) return "CRITICAL";
        if (utilizationPercent >= 80) return "HIGH";
        if (utilizationPercent >= 60) return "MEDIUM";
        return "LOW";
    }

    private List<String> identifyConcerns(int currentPoints, BoundarySettings settings, double utilization) {
        List<String> concerns = new ArrayList<>();
        
        if (currentPoints > settings.maxPoints()) {
            concerns.add("Exceeds maximum point limit");
        }
        if (currentPoints > settings.emergencyThreshold()) {
            concerns.add("Critical storage usage - emergency action needed");
        }
        if (utilization > 90) {
            concerns.add("Very high storage utilization");
        }
        if (currentPoints > 1000) {
            concerns.add("Large dataset may impact query performance");
        }
        
        return concerns;
    }

    private int calculateCompressionLevel(BoundaryCheckResult boundaryCheck, BoundarySettings settings) {
        double utilizationPercent = boundaryCheck.utilizationPercent();
        
        if (utilizationPercent > 120) return 8; // Aggressive compression
        if (utilizationPercent > 100) return 6; // High compression
        if (utilizationPercent > 80) return 4;  // Medium compression
        return 2; // Light compression
    }

    private double calculateCompressionTolerance(int compressionLevel) {
        // Higher compression level = higher tolerance (less precision)
        return DOUGLAS_PEUCKER_DEFAULT_TOLERANCE * Math.pow(2, compressionLevel - 1);
    }

    private List<RouteDataService.GpsLocationData> applyDouglasPeucker(
            List<RouteDataService.GpsLocationData> points, double tolerance) {
        
        if (points.size() <= 2) {
            return new ArrayList<>(points);
        }
        
        // Simplified Douglas-Peucker implementation
        // In production, would use a full geometric algorithm
        List<RouteDataService.GpsLocationData> simplified = new ArrayList<>();
        
        // Always keep first and last points
        simplified.add(points.get(0));
        
        // Keep every Nth point based on tolerance
        int step = Math.max(1, (int)(tolerance * 1000)); // Convert tolerance to step size
        for (int i = step; i < points.size() - step; i += step) {
            simplified.add(points.get(i));
        }
        
        simplified.add(points.get(points.size() - 1));
        
        return simplified;
    }

    private List<RouteDataService.GpsLocationData> preserveImportantFeatures(
            List<RouteDataService.GpsLocationData> original,
            List<RouteDataService.GpsLocationData> compressed,
            List<String> featuresToPreserve) {
        
        // For now, return compressed route as-is
        // In production, would analyze original route for turns, stops, speed changes, etc.
        return compressed;
    }

    private double calculateQualityRetention(List<RouteDataService.GpsLocationData> original,
                                           List<RouteDataService.GpsLocationData> compressed) {
        // Simplified quality calculation based on point retention ratio
        return (double) compressed.size() / original.size();
    }

    private RouteDataService.GpsLocationData convertMapToGps(Map<String, Object> map) {
        BigDecimal latitude = new BigDecimal(map.get("lat").toString());
        BigDecimal longitude = new BigDecimal(map.get("lng").toString());
        LocalDateTime timestamp = parseTimestampFromPoint(map);
        
        BigDecimal altitude = map.containsKey("alt") ? new BigDecimal(map.get("alt").toString()) : null;
        BigDecimal accuracy = map.containsKey("accuracy") ? new BigDecimal(map.get("accuracy").toString()) : null;
        BigDecimal speed = map.containsKey("speed") ? new BigDecimal(map.get("speed").toString()) : null;
        BigDecimal heading = map.containsKey("heading") ? new BigDecimal(map.get("heading").toString()) : null;
        
        return new RouteDataService.GpsLocationData(latitude, longitude, altitude, accuracy, 
                                                   speed, heading, timestamp, new HashMap<>(map));
    }

    private Map<String, Object> convertGpsToMap(RouteDataService.GpsLocationData gps) {
        Map<String, Object> map = new HashMap<>();
        map.put("lat", gps.latitude());
        map.put("lng", gps.longitude());
        map.put("ts", gps.timestamp().toString());
        
        if (gps.altitude() != null) map.put("alt", gps.altitude());
        if (gps.accuracy() != null) map.put("accuracy", gps.accuracy());
        if (gps.speed() != null) map.put("speed", gps.speed());
        if (gps.heading() != null) map.put("heading", gps.heading());
        
        return map;
    }

    private LocalDateTime parseTimestampFromPoint(Map<String, Object> point) {
        String timestampStr = point.get("ts").toString();
        return LocalDateTime.parse(timestampStr);
    }

    private double calculateQueryPerformanceScore(int totalPoints) {
        // Performance degrades with more points
        if (totalPoints <= 100) return 1.0;
        if (totalPoints <= 300) return 0.9;
        if (totalPoints <= 500) return 0.8;
        if (totalPoints <= 1000) return 0.6;
        return 0.4;
    }

    private double calculateDataQualityScore(List<Map<String, Object>> routeData) {
        // Simplified quality score based on data completeness
        int totalPoints = routeData.size();
        int qualityPoints = (int) routeData.stream()
            .mapToLong(point -> {
                boolean hasAccuracy = point.containsKey("accuracy");
                boolean hasSpeed = point.containsKey("speed");
                return (hasAccuracy && hasSpeed) ? 1 : 0;
            })
            .sum();
        
        return totalPoints > 0 ? (double) qualityPoints / totalPoints : 1.0;
    }

    private List<String> identifyHealthIssues(int totalPoints, double storageUsedMB,
                                            double queryPerformanceScore, double dataQualityScore) {
        List<String> issues = new ArrayList<>();
        
        if (totalPoints > 1000) {
            issues.add("Very large route dataset");
        }
        if (storageUsedMB > CRITICAL_STORAGE_THRESHOLD_MB) {
            issues.add("High storage usage");
        }
        if (queryPerformanceScore < 0.6) {
            issues.add("Poor query performance expected");
        }
        if (dataQualityScore < 0.7) {
            issues.add("Low data quality score");
        }
        
        return issues;
    }

    private String determineOverallHealth(int totalPoints, double queryPerformanceScore,
                                        double dataQualityScore, int issueCount) {
        if (issueCount >= 3) return "CRITICAL";
        if (issueCount >= 2) return "POOR";
        if (totalPoints > 500 || queryPerformanceScore < 0.7) return "FAIR";
        if (dataQualityScore > 0.8 && queryPerformanceScore > 0.8) return "EXCELLENT";
        return "GOOD";
    }
}