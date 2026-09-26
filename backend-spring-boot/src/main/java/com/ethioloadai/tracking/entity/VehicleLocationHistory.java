package com.ethioloadai.tracking.entity;

import com.ethioloadai.trip.entity.Trip;
import com.ethioloadai.vehicle.entity.Vehicle;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Historical GPS location data for vehicles.
 * 
 * Stores complete GPS tracking history for analytics, route reconstruction,
 * and compliance purposes. Table is partitioned by timestamp for performance.
 */
@Entity
@Table(name = "vehicle_location_history",
       indexes = {
           @Index(name = "idx_location_history_vehicle_timestamp", 
                  columnList = "vehicle_id, timestamp DESC"),
           @Index(name = "idx_location_history_trip_timestamp", 
                  columnList = "trip_id, timestamp"),
           // Note: Additional indexes created via migration SQL for partitioned table
       })
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VehicleLocationHistory {

    /**
     * Unique identifier for each GPS reading.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Vehicle that transmitted this GPS data.
     */
    @Column(name = "vehicle_id", nullable = false)
    private Long vehicleId;

    /**
     * Vehicle reference for relationship queries.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vehicle_id", insertable = false, updatable = false)
    private Vehicle vehicle;

    /**
     * Trip associated with this GPS reading.
     * Null if vehicle was not on an active trip.
     */
    @Column(name = "trip_id")
    private Long tripId;

    /**
     * Trip reference for relationship queries.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trip_id", insertable = false, updatable = false)
    private Trip trip;

    /**
     * GPS latitude in decimal degrees.
     * Precision: DECIMAL(10,8) allows Â±90Â° with ~1cm accuracy.
     */
    @Column(name = "latitude", nullable = false, precision = 10, scale = 8)
    private BigDecimal latitude;

    /**
     * GPS longitude in decimal degrees.
     * Precision: DECIMAL(11,8) allows Â±180Â° with ~1cm accuracy.
     */
    @Column(name = "longitude", nullable = false, precision = 11, scale = 8)
    private BigDecimal longitude;

    /**
     * Altitude above sea level in meters.
     * Optional - not all GPS devices provide altitude.
     */
    @Column(name = "altitude", precision = 8, scale = 2)
    private BigDecimal altitude;

    /**
     * GPS accuracy radius in meters.
     * Indicates confidence level of position fix.
     */
    @Column(name = "accuracy", precision = 6, scale = 2)
    private BigDecimal accuracy;

    /**
     * Vehicle speed in km/h.
     * Derived from GPS doppler or calculated from position changes.
     */
    @Column(name = "speed", precision = 5, scale = 2)
    private BigDecimal speed;

    /**
     * Vehicle heading/direction in degrees (0-360).
     * 0 = North, 90 = East, 180 = South, 270 = West.
     */
    @Column(name = "heading", precision = 5, scale = 2)
    private BigDecimal heading;

    /**
     * GPS reading timestamp from the device.
     * Critical for temporal ordering and partition key.
     */
    @Column(name = "timestamp", nullable = false)
    private LocalDateTime timestamp;

    /**
     * Device battery level (0-100).
     * Optional - helps with device health monitoring.
     */
    @Column(name = "battery_level")
    private Integer batteryLevel;

    /**
     * Signal strength indicator.
     * Optional - helps with GPS quality assessment.
     */
    @Column(name = "signal_strength")
    private Integer signalStrength;

    /**
     * Additional metadata as JSONB.
     * Examples: device info, driver info, environmental data.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private Map<String, Object> metadata;

    /**
     * Record creation timestamp.
     */
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // â”€â”€ Business Methods â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    /**
     * Calculate distance to another GPS point using Haversine formula.
     * 
     * @param other other GPS location
     * @return distance in kilometers
     */
    public BigDecimal calculateDistanceTo(VehicleLocationHistory other) {
        return calculateDistance(this.latitude, this.longitude, 
                               other.latitude, other.longitude);
    }

    /**
     * Calculate speed between this and another GPS point.
     * 
     * @param other other GPS location (should be chronologically after this)
     * @return speed in km/h
     */
    public BigDecimal calculateSpeedTo(VehicleLocationHistory other) {
        BigDecimal distance = calculateDistanceTo(other);
        long seconds = java.time.Duration.between(this.timestamp, other.timestamp).getSeconds();
        
        if (seconds <= 0) {
            return BigDecimal.ZERO;
        }
        
        // Convert to km/h: (distance_km / time_seconds) * 3600
        return distance.divide(BigDecimal.valueOf(seconds), 6, java.math.RoundingMode.HALF_UP)
                      .multiply(BigDecimal.valueOf(3600));
    }

    /**
     * Check if GPS accuracy is acceptable for route calculations.
     * 
     * @param maxAccuracyMeters maximum acceptable accuracy
     * @return true if accuracy is acceptable
     */
    public boolean hasAcceptableAccuracy(BigDecimal maxAccuracyMeters) {
        return accuracy == null || accuracy.compareTo(maxAccuracyMeters) <= 0;
    }

    /**
     * Get location as Point for geospatial operations.
     * Format: "POINT(longitude latitude)" for PostGIS compatibility.
     * 
     * @return WKT point representation
     */
    public String getLocationAsPoint() {
        return String.format("POINT(%s %s)", longitude, latitude);
    }

    /**
     * Create a copy of this location for current location table.
     * 
     * @return CurrentVehicleLocation with same data
     */
    public CurrentVehicleLocation toCurrentLocation() {
        CurrentVehicleLocation loc = new CurrentVehicleLocation();
        loc.setVehicleId(this.vehicleId);
        loc.setTrip(this.trip);
        loc.setLatitude(this.latitude);
        loc.setLongitude(this.longitude);
        loc.setAltitude(this.altitude);
        loc.setAccuracy(this.accuracy);
        loc.setSpeed(this.speed);
        loc.setHeading(this.heading);
        loc.setTimestamp(this.timestamp);
        loc.setBatteryLevel(this.batteryLevel);
        loc.setSignalStrength(this.signalStrength);
        loc.setMetadata(this.metadata);
        return loc;
    }

    // â”€â”€ Utility Methods â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    /**
     * Calculate distance between two GPS points using Haversine formula.
     * 
     * @param lat1 latitude of first point
     * @param lon1 longitude of first point  
     * @param lat2 latitude of second point
     * @param lon2 longitude of second point
     * @return distance in kilometers
     */
    private static BigDecimal calculateDistance(BigDecimal lat1, BigDecimal lon1, 
                                              BigDecimal lat2, BigDecimal lon2) {
        final double EARTH_RADIUS_KM = 6371.0;
        
        double dLat = Math.toRadians(lat2.doubleValue() - lat1.doubleValue());
        double dLon = Math.toRadians(lon2.doubleValue() - lon1.doubleValue());
        
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                   Math.cos(Math.toRadians(lat1.doubleValue())) *
                   Math.cos(Math.toRadians(lat2.doubleValue())) *
                   Math.sin(dLon / 2) * Math.sin(dLon / 2);
        
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        
        return BigDecimal.valueOf(EARTH_RADIUS_KM * c);
    }

    // ── Explicit accessors (supplement Lombok @Data for complex JPA entities) ──
    public Long getId()                    { return id; }
    public Long getVehicleId()             { return vehicleId; }
    public Vehicle getVehicle()            { return vehicle; }
    public Long getTripId()                { return tripId; }
    public Trip getTrip()                  { return trip; }
    public BigDecimal getLatitude()        { return latitude; }
    public BigDecimal getLongitude()       { return longitude; }
    public BigDecimal getAltitude()        { return altitude; }
    public BigDecimal getAccuracy()        { return accuracy; }
    public BigDecimal getSpeed()           { return speed; }
    public BigDecimal getHeading()         { return heading; }
    public java.time.LocalDateTime getTimestamp() { return timestamp; }
    public Integer getBatteryLevel()       { return batteryLevel; }
    public Integer getSignalStrength()     { return signalStrength; }
    public java.util.Map<String, Object> getMetadata() { return metadata; }
    public java.time.LocalDateTime getCreatedAt()  { return createdAt; }

    public void setVehicleId(Long vehicleId)         { this.vehicleId = vehicleId; }
    public void setTripId(Long tripId)               { this.tripId = tripId; }
    public void setTrip(Trip trip)                   { this.trip = trip; }
    public void setLatitude(BigDecimal lat)          { this.latitude = lat; }
    public void setLongitude(BigDecimal lon)         { this.longitude = lon; }
    public void setAltitude(BigDecimal alt)          { this.altitude = alt; }
    public void setAccuracy(BigDecimal acc)          { this.accuracy = acc; }
    public void setSpeed(BigDecimal speed)           { this.speed = speed; }
    public void setHeading(BigDecimal heading)       { this.heading = heading; }
    public void setTimestamp(java.time.LocalDateTime ts) { this.timestamp = ts; }
    public void setBatteryLevel(Integer bl)          { this.batteryLevel = bl; }
    public void setSignalStrength(Integer ss)        { this.signalStrength = ss; }
    public void setMetadata(java.util.Map<String, Object> m) { this.metadata = m; }
}