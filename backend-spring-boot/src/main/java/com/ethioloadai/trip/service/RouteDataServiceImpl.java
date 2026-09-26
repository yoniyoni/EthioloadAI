package com.ethioloadai.trip.service;

import com.ethioloadai.trip.config.TripManagementProperties;
import com.ethioloadai.trip.entity.Trip;
import com.ethioloadai.trip.exception.TripValidationException;
import com.ethioloadai.trip.repository.TripRepository;
import com.ethioloadai.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RouteDataServiceImpl implements RouteDataService {
    private static final Logger log = LoggerFactory.getLogger(RouteDataServiceImpl.class);

    private final TripRepository tripRepository;
    private final TripManagementProperties properties;

    // GPS validation constants
    private static final BigDecimal MIN_LATITUDE = new BigDecimal("-90");
    private static final BigDecimal MAX_LATITUDE = new BigDecimal("90");
    private static final BigDecimal MIN_LONGITUDE = new BigDecimal("-180");
    private static final BigDecimal MAX_LONGITUDE = new BigDecimal("180");
    private static final BigDecimal MAX_SPEED_KMH = new BigDecimal("200"); // 200 km/h max realistic speed
    private static final BigDecimal MIN_ACCURACY_METERS = new BigDecimal("100"); // 100m accuracy threshold
    
    // Route data limits (configurable)
    private static final int MAX_GPS_POINTS = 500; // Prevent unbounded growth
    private static final int ARCHIVE_THRESHOLD = 450; // Start archiving at this point count
    private static final int MIN_POINTS_TO_KEEP = 50; // Always keep recent points
    private static final BigDecimal MAX_JUMP_DISTANCE_KM = new BigDecimal("10"); // Max distance between points
    
    private static final DateTimeFormatter GPS_TIMESTAMP_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    @Override
    @Transactional
    public Trip addGpsLocation(Trip trip, GpsLocationData gpsData, User user) {
        log.debug("Adding GPS location for trip {} by user {}", trip.getId(), user.getId());

        // Validate user is the driver
        if (!trip.getBooking().getDriver().getId().equals(user.getId())) {
            throw TripValidationException.unauthorizedAccess(trip.getId(), user.getId());
        }

        // Validate trip is ongoing
        if (trip.getTripStatus() != Trip.TripStatus.ONGOING) {
            throw TripValidationException.tripNotOngoing(trip.getId());
        }

        // Validate GPS data quality
        GpsValidationResult validation = validateGpsData(gpsData);
        if (!validation.isValid()) {
            log.warn("GPS validation failed for trip {}: {}", trip.getId(), validation.issues());
            throw TripValidationException.invalidGpsData(String.join(", ", validation.issues()));
        }

        // Get current route data
        List<Map<String, Object>> routeData = new ArrayList<>(trip.getRouteData());
        
        // Check for potential GPS jump anomaly
        if (!routeData.isEmpty()) {
            validateGpsJump(routeData.get(routeData.size() - 1), gpsData);
        }

        // Convert GPS data to map format for JSONB storage
        Map<String, Object> gpsMap = convertGpsToMap(gpsData);
        routeData.add(gpsMap);

        // Enforce GPS point limits to prevent unbounded growth
        if (routeData.size() > MAX_GPS_POINTS) {
            log.info("GPS points limit exceeded for trip {}, archiving old data", trip.getId());
            routeData = archiveGpsPoints(routeData);
        }

        // Update trip with new route data
        trip.setRouteData(routeData);
        Trip updatedTrip = tripRepository.save(trip);

        log.debug("GPS location added to trip {} - total points: {}", trip.getId(), routeData.size());
        return updatedTrip;
    }

    @Override
    public GpsLocationData getCurrentLocation(Trip trip) {
        List<Map<String, Object>> routeData = trip.getRouteData();
        if (routeData == null || routeData.isEmpty()) {
            return null;
        }

        Map<String, Object> lastPoint = routeData.get(routeData.size() - 1);
        return convertMapToGps(lastPoint);
    }

    @Override
    public List<GpsLocationData> getRouteHistory(Trip trip, Integer limit, 
                                               LocalDateTime fromTime, LocalDateTime toTime) {
        List<Map<String, Object>> routeData = trip.getRouteData();
        if (routeData == null || routeData.isEmpty()) {
            return List.of();
        }

        return routeData.stream()
                .map(this::convertMapToGps)
                .filter(gps -> filterByTimeRange(gps, fromTime, toTime))
                .collect(Collectors.collectingAndThen(
                    Collectors.toList(),
                    list -> limit != null ? list.stream().limit(limit).toList() : list
                ));
    }

    @Override
    public BigDecimal calculateTripDistance(Trip trip) {
        List<GpsLocationData> route = getRouteHistory(trip, null, null, null);
        if (route.size() < 2) {
            return BigDecimal.ZERO;
        }

        BigDecimal totalDistance = BigDecimal.ZERO;
        for (int i = 1; i < route.size(); i++) {
            BigDecimal segmentDistance = calculateHaversineDistance(
                route.get(i - 1).latitude(), route.get(i - 1).longitude(),
                route.get(i).latitude(), route.get(i).longitude()
            );
            totalDistance = totalDistance.add(segmentDistance);
        }

        return totalDistance;
    }

    @Override
    public BigDecimal calculateAverageSpeed(Trip trip) {
        List<GpsLocationData> route = getRouteHistory(trip, null, null, null);
        if (route.size() < 2) {
            return BigDecimal.ZERO;
        }

        BigDecimal totalDistance = calculateTripDistance(trip);
        LocalDateTime startTime = route.get(0).timestamp();
        LocalDateTime endTime = route.get(route.size() - 1).timestamp();
        
        long durationMinutes = ChronoUnit.MINUTES.between(startTime, endTime);
        if (durationMinutes == 0) {
            return BigDecimal.ZERO;
        }

        BigDecimal durationHours = new BigDecimal(durationMinutes).divide(new BigDecimal("60"), 2, RoundingMode.HALF_UP);
        return totalDistance.divide(durationHours, 2, RoundingMode.HALF_UP);
    }

    @Override
    public RouteAnalytics getRouteAnalytics(Trip trip) {
        List<GpsLocationData> route = getRouteHistory(trip, null, null, null);
        if (route.isEmpty()) {
            return createEmptyAnalytics(trip);
        }

        BigDecimal totalDistance = calculateTripDistance(trip);
        BigDecimal averageSpeed = calculateAverageSpeed(trip);
        
        // Calculate max speed
        BigDecimal maxSpeed = route.stream()
                .filter(gps -> gps.speed() != null)
                .map(GpsLocationData::speed)
                .max(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);

        // Calculate driving vs stopped time
        RouteTimeAnalysis timeAnalysis = analyzeRouteTime(route);
        
        // Create route segments
        List<RouteSegment> segments = createRouteSegments(route);

        // Compile statistics
        Map<String, Object> statistics = Map.of(
            "points_per_hour", calculatePointsPerHour(route),
            "average_accuracy", calculateAverageAccuracy(route),
            "data_quality_score", calculateDataQuality(route)
        );

        return new RouteAnalytics(
            totalDistance,
            averageSpeed,
            maxSpeed,
            timeAnalysis.drivingHours,
            timeAnalysis.stoppedHours,
            route.size(),
            route.get(0).timestamp(),
            route.get(route.size() - 1).timestamp(),
            segments,
            statistics
        );
    }

    @Override
    @Transactional
    public int archiveOldGpsData(Trip trip) {
        List<Map<String, Object>> routeData = new ArrayList<>(trip.getRouteData());
        int originalSize = routeData.size();
        
        if (originalSize <= MIN_POINTS_TO_KEEP) {
            return 0; // Nothing to archive
        }

        // Keep recent points and important waypoints
        List<Map<String, Object>> archivedData = archiveGpsPoints(routeData);
        
        int archived = originalSize - archivedData.size();
        if (archived > 0) {
            trip.setRouteData(archivedData);
            tripRepository.save(trip);
            
            log.info("Archived {} GPS points for trip {}, {} points remaining", 
                    archived, trip.getId(), archivedData.size());
        }
        
        return archived;
    }

    @Override
    @Transactional
    public int compressRouteData(Trip trip, BigDecimal tolerance) {
        List<Map<String, Object>> routeData = new ArrayList<>(trip.getRouteData());
        int originalSize = routeData.size();
        
        if (originalSize < 3) {
            return 0; // Need at least 3 points for compression
        }

        // Convert to GPS data for processing
        List<GpsLocationData> gpsRoute = routeData.stream()
                .map(this::convertMapToGps)
                .toList();

        // Apply Douglas-Peucker algorithm (simplified)
        List<GpsLocationData> compressed = simplifyRoute(gpsRoute, tolerance);
        
        // Convert back to map format
        List<Map<String, Object>> compressedData = compressed.stream()
                .map(this::convertGpsToMap)
                .collect(Collectors.toList());

        int removed = originalSize - compressed.size();
        if (removed > 0) {
            trip.setRouteData(compressedData);
            tripRepository.save(trip);
            
            log.info("Compressed route for trip {}: {} → {} points ({} removed)", 
                    trip.getId(), originalSize, compressed.size(), removed);
        }
        
        return removed;
    }

    @Override
    public GpsValidationResult validateGpsData(GpsLocationData gpsData) {
        List<String> issues = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        
        // Validate coordinates
        if (gpsData.latitude() == null || gpsData.longitude() == null) {
            issues.add("Latitude and longitude are required");
        } else {
            if (gpsData.latitude().compareTo(MIN_LATITUDE) < 0 || gpsData.latitude().compareTo(MAX_LATITUDE) > 0) {
                issues.add("Latitude must be between -90 and 90 degrees");
            }
            if (gpsData.longitude().compareTo(MIN_LONGITUDE) < 0 || gpsData.longitude().compareTo(MAX_LONGITUDE) > 0) {
                issues.add("Longitude must be between -180 and 180 degrees");
            }
        }
        
        // Validate timestamp
        if (gpsData.timestamp() == null) {
            issues.add("Timestamp is required");
        } else {
            LocalDateTime now = LocalDateTime.now();
            if (gpsData.timestamp().isAfter(now.plusMinutes(5))) {
                warnings.add("GPS timestamp is in the future");
            }
            if (gpsData.timestamp().isBefore(now.minusHours(24))) {
                warnings.add("GPS timestamp is more than 24 hours old");
            }
        }
        
        // Validate accuracy
        if (gpsData.accuracy() != null && gpsData.accuracy().compareTo(MIN_ACCURACY_METERS) > 0) {
            warnings.add("GPS accuracy is poor (>" + MIN_ACCURACY_METERS + "m)");
        }
        
        // Validate speed
        if (gpsData.speed() != null && gpsData.speed().compareTo(MAX_SPEED_KMH) > 0) {
            warnings.add("Speed exceeds realistic maximum (" + MAX_SPEED_KMH + " km/h)");
        }

        boolean isValid = issues.isEmpty();
        BigDecimal qualityScore = calculateGpsQualityScore(gpsData, issues, warnings);
        
        return new GpsValidationResult(isValid, issues, warnings, qualityScore);
    }

    @Override
    public RouteStorageStats getRouteStorageStats(Trip trip) {
        List<Map<String, Object>> routeData = trip.getRouteData();
        int currentPoints = routeData != null ? routeData.size() : 0;
        
        // Estimate storage usage (rough calculation)
        BigDecimal storageUsedKb = new BigDecimal(currentPoints * 100).divide(new BigDecimal("1024"), 2, RoundingMode.HALF_UP);
        BigDecimal storageLimit = new BigDecimal(MAX_GPS_POINTS * 100).divide(new BigDecimal("1024"), 2, RoundingMode.HALF_UP);
        
        LocalDateTime oldestPoint = null;
        LocalDateTime newestPoint = null;
        
        if (!routeData.isEmpty()) {
            oldestPoint = convertMapToGps(routeData.get(0)).timestamp();
            newestPoint = convertMapToGps(routeData.get(routeData.size() - 1)).timestamp();
        }
        
        BigDecimal compressionRatio = currentPoints > 0 ? 
            new BigDecimal("1.0") : BigDecimal.ZERO; // Would calculate actual compression

        return new RouteStorageStats(
            currentPoints,
            MAX_GPS_POINTS,
            storageUsedKb,
            storageLimit,
            compressionRatio,
            oldestPoint,
            newestPoint
        );
    }

    @Override
    public String exportRouteData(Trip trip, String format) {
        List<GpsLocationData> route = getRouteHistory(trip, null, null, null);
        
        return switch (format.toLowerCase()) {
            case "json" -> exportAsJson(route, trip);
            case "gpx" -> exportAsGpx(route, trip);
            case "kml" -> exportAsKml(route, trip);
            default -> throw new IllegalArgumentException("Unsupported format: " + format);
        };
    }

    @Override
    public List<RouteAnomaly> detectRouteAnomalies(Trip trip) {
        List<GpsLocationData> route = getRouteHistory(trip, null, null, null);
        List<RouteAnomaly> anomalies = new ArrayList<>();
        
        if (route.size() < 2) {
            return anomalies;
        }

        for (int i = 1; i < route.size(); i++) {
            GpsLocationData prev = route.get(i - 1);
            GpsLocationData curr = route.get(i);
            
            // Check for large distance jumps
            BigDecimal distance = calculateHaversineDistance(
                prev.latitude(), prev.longitude(),
                curr.latitude(), curr.longitude()
            );
            
            if (distance.compareTo(MAX_JUMP_DISTANCE_KM) > 0) {
                anomalies.add(new RouteAnomaly(
                    "large_jump",
                    String.format("Large GPS jump: %.2f km", distance),
                    curr.timestamp(),
                    curr,
                    new BigDecimal("0.8"),
                    Map.of("distance_km", distance, "previous_point", prev)
                ));
            }
            
            // Check for unrealistic speed
            if (curr.speed() != null && curr.speed().compareTo(MAX_SPEED_KMH) > 0) {
                anomalies.add(new RouteAnomaly(
                    "excessive_speed",
                    String.format("Excessive speed: %.2f km/h", curr.speed()),
                    curr.timestamp(),
                    curr,
                    new BigDecimal("0.9"),
                    Map.of("speed_kmh", curr.speed())
                ));
            }
        }
        
        return anomalies;
    }

    // ── Private Helper Methods ───────────────────────────────────────────────

    private Map<String, Object> convertGpsToMap(GpsLocationData gpsData) {
        Map<String, Object> map = new HashMap<>();
        map.put("lat", gpsData.latitude());
        map.put("lng", gpsData.longitude());
        
        if (gpsData.altitude() != null) {
            map.put("alt", gpsData.altitude());
        }
        if (gpsData.accuracy() != null) {
            map.put("accuracy", gpsData.accuracy());
        }
        if (gpsData.speed() != null) {
            map.put("speed", gpsData.speed());
        }
        if (gpsData.heading() != null) {
            map.put("heading", gpsData.heading());
        }
        
        map.put("ts", gpsData.timestamp().format(GPS_TIMESTAMP_FORMAT));
        
        if (gpsData.metadata() != null) {
            map.putAll(gpsData.metadata());
        }
        
        return map;
    }

    private GpsLocationData convertMapToGps(Map<String, Object> map) {
        BigDecimal latitude = new BigDecimal(map.get("lat").toString());
        BigDecimal longitude = new BigDecimal(map.get("lng").toString());
        LocalDateTime timestamp = LocalDateTime.parse(map.get("ts").toString(), GPS_TIMESTAMP_FORMAT);
        
        BigDecimal altitude = map.containsKey("alt") ? new BigDecimal(map.get("alt").toString()) : null;
        BigDecimal accuracy = map.containsKey("accuracy") ? new BigDecimal(map.get("accuracy").toString()) : null;
        BigDecimal speed = map.containsKey("speed") ? new BigDecimal(map.get("speed").toString()) : null;
        BigDecimal heading = map.containsKey("heading") ? new BigDecimal(map.get("heading").toString()) : null;
        
        Map<String, Object> metadata = new HashMap<>(map);
        metadata.remove("lat");
        metadata.remove("lng"); 
        metadata.remove("ts");
        metadata.remove("alt");
        metadata.remove("accuracy");
        metadata.remove("speed");
        metadata.remove("heading");
        
        return new GpsLocationData(latitude, longitude, altitude, accuracy, speed, heading, timestamp, metadata);
    }

    private void validateGpsJump(Map<String, Object> lastPoint, GpsLocationData newPoint) {
        if (lastPoint == null) return;
        
        GpsLocationData lastGps = convertMapToGps(lastPoint);
        BigDecimal distance = calculateHaversineDistance(
            lastGps.latitude(), lastGps.longitude(),
            newPoint.latitude(), newPoint.longitude()
        );
        
        if (distance.compareTo(MAX_JUMP_DISTANCE_KM) > 0) {
            log.warn("Large GPS jump detected: {:.2f} km", distance);
            // Could throw exception or just log warning based on requirements
        }
    }

    private List<Map<String, Object>> archiveGpsPoints(List<Map<String, Object>> routeData) {
        if (routeData.size() <= MIN_POINTS_TO_KEEP) {
            return routeData;
        }
        
        // Strategy: Keep recent points + evenly spaced historical points
        List<Map<String, Object>> archived = new ArrayList<>();
        
        // Always keep the most recent points
        int keepRecentCount = MIN_POINTS_TO_KEEP / 2;
        int startIdx = routeData.size() - keepRecentCount;
        archived.addAll(routeData.subList(startIdx, routeData.size()));
        
        // Keep evenly spaced points from the historical data
        int historicalCount = MIN_POINTS_TO_KEEP / 2;
        int step = Math.max(1, startIdx / historicalCount);
        
        for (int i = 0; i < startIdx; i += step) {
            archived.add(0, routeData.get(i)); // Add to beginning to maintain order
        }
        
        return archived;
    }

    private List<GpsLocationData> simplifyRoute(List<GpsLocationData> route, BigDecimal tolerance) {
        // Simplified Douglas-Peucker algorithm implementation
        if (route.size() <= 2) {
            return route;
        }
        
        // For now, return every nth point as a simple compression
        // Full Douglas-Peucker would be more complex
        List<GpsLocationData> simplified = new ArrayList<>();
        int step = Math.max(1, route.size() / 100); // Keep ~100 points max
        
        for (int i = 0; i < route.size(); i += step) {
            simplified.add(route.get(i));
        }
        
        // Always include the last point
        if (!simplified.contains(route.get(route.size() - 1))) {
            simplified.add(route.get(route.size() - 1));
        }
        
        return simplified;
    }

    private BigDecimal calculateHaversineDistance(BigDecimal lat1, BigDecimal lon1, BigDecimal lat2, BigDecimal lon2) {
        final BigDecimal EARTH_RADIUS_KM = new BigDecimal("6371");
        
        double lat1Rad = Math.toRadians(lat1.doubleValue());
        double lon1Rad = Math.toRadians(lon1.doubleValue());
        double lat2Rad = Math.toRadians(lat2.doubleValue());
        double lon2Rad = Math.toRadians(lon2.doubleValue());
        
        double dLat = lat2Rad - lat1Rad;
        double dLon = lon2Rad - lon1Rad;
        
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                   Math.cos(lat1Rad) * Math.cos(lat2Rad) *
                   Math.sin(dLon / 2) * Math.sin(dLon / 2);
        
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        
        return EARTH_RADIUS_KM.multiply(new BigDecimal(c));
    }

    private boolean filterByTimeRange(GpsLocationData gps, LocalDateTime fromTime, LocalDateTime toTime) {
        if (fromTime != null && gps.timestamp().isBefore(fromTime)) {
            return false;
        }
        if (toTime != null && gps.timestamp().isAfter(toTime)) {
            return false;
        }
        return true;
    }

    private BigDecimal calculateGpsQualityScore(GpsLocationData gpsData, List<String> issues, List<String> warnings) {
        BigDecimal score = new BigDecimal("1.0");
        
        // Deduct for issues and warnings
        score = score.subtract(new BigDecimal(issues.size() * 0.5));
        score = score.subtract(new BigDecimal(warnings.size() * 0.1));
        
        // Adjust for accuracy
        if (gpsData.accuracy() != null) {
            if (gpsData.accuracy().compareTo(new BigDecimal("10")) <= 0) {
                score = score.add(new BigDecimal("0.1")); // Bonus for high accuracy
            }
        }
        
        return score.max(BigDecimal.ZERO).min(new BigDecimal("1.0"));
    }

    private RouteAnalytics createEmptyAnalytics(Trip trip) {
        return new RouteAnalytics(
            BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
            0, null, null, List.of(), Map.of()
        );
    }

    private RouteTimeAnalysis analyzeRouteTime(List<GpsLocationData> route) {
        // Simple implementation - would be more sophisticated in production
        BigDecimal totalHours = BigDecimal.ZERO;
        if (route.size() >= 2) {
            long minutes = ChronoUnit.MINUTES.between(
                route.get(0).timestamp(), 
                route.get(route.size() - 1).timestamp()
            );
            totalHours = new BigDecimal(minutes).divide(new BigDecimal("60"), 2, RoundingMode.HALF_UP);
        }
        
        return new RouteTimeAnalysis(totalHours, BigDecimal.ZERO);
    }

    private List<RouteSegment> createRouteSegments(List<GpsLocationData> route) {
        // Simplified segmentation - would analyze speed patterns in production
        if (route.size() < 2) {
            return List.of();
        }
        
        return List.of(new RouteSegment(
            route.get(0).timestamp(),
            route.get(route.size() - 1).timestamp(),
            calculateTripDistanceFromRoute(route),
            calculateAverageSpeedFromRoute(route),
            "driving",
            route.get(0),
            route.get(route.size() - 1)
        ));
    }

    private BigDecimal calculatePointsPerHour(List<GpsLocationData> route) {
        if (route.size() < 2) return BigDecimal.ZERO;
        
        long hours = ChronoUnit.HOURS.between(
            route.get(0).timestamp(), 
            route.get(route.size() - 1).timestamp()
        );
        
        return hours > 0 ? new BigDecimal(route.size()).divide(new BigDecimal(hours), 2, RoundingMode.HALF_UP) 
                        : new BigDecimal(route.size());
    }

    private BigDecimal calculateAverageAccuracy(List<GpsLocationData> route) {
        return route.stream()
                .filter(gps -> gps.accuracy() != null)
                .map(GpsLocationData::accuracy)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(new BigDecimal(route.size()), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateDataQuality(List<GpsLocationData> route) {
        return new BigDecimal("0.85"); // Simplified quality score
    }

    private BigDecimal calculateTripDistanceFromRoute(List<GpsLocationData> route) {
        if (route.size() < 2) return BigDecimal.ZERO;
        
        BigDecimal totalDistance = BigDecimal.ZERO;
        for (int i = 1; i < route.size(); i++) {
            totalDistance = totalDistance.add(calculateHaversineDistance(
                route.get(i - 1).latitude(), route.get(i - 1).longitude(),
                route.get(i).latitude(), route.get(i).longitude()
            ));
        }
        return totalDistance;
    }

    private BigDecimal calculateAverageSpeedFromRoute(List<GpsLocationData> route) {
        if (route.size() < 2) return BigDecimal.ZERO;
        
        BigDecimal distance = calculateTripDistanceFromRoute(route);
        long minutes = ChronoUnit.MINUTES.between(
            route.get(0).timestamp(),
            route.get(route.size() - 1).timestamp()
        );
        
        if (minutes == 0) return BigDecimal.ZERO;
        
        BigDecimal hours = new BigDecimal(minutes).divide(new BigDecimal("60"), 2, RoundingMode.HALF_UP);
        return distance.divide(hours, 2, RoundingMode.HALF_UP);
    }

    private String exportAsJson(List<GpsLocationData> route, Trip trip) {
        // Simple JSON export - would use proper JSON library in production
        return String.format("{\"trip_id\": %d, \"points\": %d, \"route\": []}", 
                           trip.getId(), route.size());
    }

    private String exportAsGpx(List<GpsLocationData> route, Trip trip) {
        // GPX format export - simplified
        return String.format("<?xml version=\"1.0\"?><gpx><trk><name>Trip %d</name></trk></gpx>", 
                           trip.getId());
    }

    private String exportAsKml(List<GpsLocationData> route, Trip trip) {
        // KML format export - simplified
        return String.format("<?xml version=\"1.0\"?><kml><Document><name>Trip %d</name></Document></kml>", 
                           trip.getId());
    }

    // Helper record for time analysis
    private record RouteTimeAnalysis(BigDecimal drivingHours, BigDecimal stoppedHours) {}
}