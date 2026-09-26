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
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Current location for active vehicle tracking.
 * 
 * Stores the most recent GPS position for each vehicle to enable
 * real-time location queries without scanning historical data.
 * One record per vehicle (upsert pattern).
 */
@Entity
@Table(name = "current_vehicle_locations",
       indexes = {
           @Index(name = "idx_current_locations_trip", columnList = "trip_id"),
           @Index(name = "idx_current_locations_timestamp", columnList = "timestamp"),
           // Note: Geospatial index created via migration SQL for PostGIS compatibility
       })
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CurrentVehicleLocation {

    /**
     * Vehicle ID serves as primary key - one current location per vehicle.
     */
    @Id
    @Column(name = "vehicle_id")
    private Long vehicleId;

    /**
     * Vehicle reference for relationship queries.
     */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vehicle_id", insertable = false, updatable = false)
    private Vehicle vehicle;

    /**
     * Current active trip, null if vehicle not on a trip.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trip_id")
    private Trip trip;

    /**
     * GPS latitude in decimal degrees.
     * Precision: DECIMAL(10,8) allows ±90° with ~1cm accuracy.
     */
    @Column(name = "latitude", nullable = false, precision = 10, scale = 8)
    private BigDecimal latitude;

    /**
     * GPS longitude in decimal degrees.
     * Precision: DECIMAL(11,8) allows ±180° with ~1cm accuracy.
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
     * Critical for temporal ordering and staleness detection.
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

    /**
     * Record last update timestamp.
     */
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // ── Business Methods ─────────────────────────────────────────────────────

    /**
     * Check if location data is stale (older than threshold).
     * 
     * @param thresholdMinutes age threshold in minutes
     * @return true if location is stale
     */
    public boolean isStale(int thresholdMinutes) {
        return timestamp.isBefore(LocalDateTime.now().minusMinutes(thresholdMinutes));
    }

    /**
     * Calculate age of location in minutes.
     * 
     * @return age in minutes
     */
    public long getAgeMinutes() {
        return java.time.Duration.between(timestamp, LocalDateTime.now()).toMinutes();
    }

    /**
     * Check if GPS accuracy is acceptable for tracking.
     * 
     * @param maxAccuracyMeters maximum acceptable accuracy
     * @return true if accuracy is acceptable
     */
    public boolean hasAcceptableAccuracy(BigDecimal maxAccuracyMeters) {
        return accuracy == null || accuracy.compareTo(maxAccuracyMeters) <= 0;
    }

    /**
     * Check if vehicle is currently moving based on speed.
     * 
     * @param minMovingSpeed minimum speed to consider moving (km/h)
     * @return true if vehicle is moving
     */
    public boolean isMoving(BigDecimal minMovingSpeed) {
        return speed != null && speed.compareTo(minMovingSpeed) > 0;
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

    // ── Explicit accessors (supplement Lombok @Data) ──────────────────────────
    public Long getVehicleId()              { return vehicleId; }
    public Trip getTrip()                   { return trip; }
    public BigDecimal getLatitude()         { return latitude; }
    public BigDecimal getLongitude()        { return longitude; }
    public BigDecimal getAltitude()         { return altitude; }
    public BigDecimal getAccuracy()         { return accuracy; }
    public BigDecimal getSpeed()            { return speed; }
    public BigDecimal getHeading()          { return heading; }
    public LocalDateTime getTimestamp()     { return timestamp; }
    public Integer getBatteryLevel()        { return batteryLevel; }
    public Integer getSignalStrength()      { return signalStrength; }
    public Map<String, Object> getMetadata() { return metadata; }
    public LocalDateTime getCreatedAt()     { return createdAt; }
    public LocalDateTime getUpdatedAt()     { return updatedAt; }

    public void setVehicleId(Long vehicleId)            { this.vehicleId = vehicleId; }
    public void setTrip(Trip trip)                      { this.trip = trip; }
    public void setLatitude(BigDecimal latitude)        { this.latitude = latitude; }
    public void setLongitude(BigDecimal longitude)      { this.longitude = longitude; }
    public void setAltitude(BigDecimal altitude)        { this.altitude = altitude; }
    public void setAccuracy(BigDecimal accuracy)        { this.accuracy = accuracy; }
    public void setSpeed(BigDecimal speed)              { this.speed = speed; }
    public void setHeading(BigDecimal heading)          { this.heading = heading; }
    public void setTimestamp(LocalDateTime timestamp)   { this.timestamp = timestamp; }
    public void setBatteryLevel(Integer batteryLevel)   { this.batteryLevel = batteryLevel; }
    public void setSignalStrength(Integer signalStrength) { this.signalStrength = signalStrength; }
    public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }
}