package com.ethioloadai.tracking.repository;

import com.ethioloadai.tracking.entity.CurrentVehicleLocation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository for current vehicle locations.
 * Optimized for real-time location tracking operations.
 */
@Repository
public interface CurrentVehicleLocationRepository extends JpaRepository<CurrentVehicleLocation, Long> {

    /**
     * Find current location by vehicle ID.
     * Primary use case for real-time tracking.
     * 
     * @param vehicleId vehicle identifier
     * @return current location if exists
     */
    Optional<CurrentVehicleLocation> findByVehicleId(Long vehicleId);

    /**
     * Find current locations for vehicles on a specific trip.
     * Used for multi-vehicle trip tracking.
     * 
     * @param tripId trip identifier
     * @return list of current locations for trip vehicles
     */
    @Query("SELECT cvl FROM CurrentVehicleLocation cvl WHERE cvl.trip.id = :tripId")
    List<CurrentVehicleLocation> findByTripId(@Param("tripId") Long tripId);

    /**
     * Find vehicles within proximity of a location.
     * Uses PostGIS distance calculation for accurate results.
     * 
     * @param latitude center point latitude
     * @param longitude center point longitude
     * @param radiusKm search radius in kilometers
     * @return vehicles within radius
     */
    @Query(value = """
        SELECT * FROM current_vehicle_locations cvl 
        WHERE ST_DWithin(
            ST_SetSRID(ST_MakePoint(cvl.longitude, cvl.latitude), 4326)::geography,
            ST_SetSRID(ST_MakePoint(:longitude, :latitude), 4326)::geography,
            :radiusKm * 1000
        )
        ORDER BY ST_Distance(
            ST_SetSRID(ST_MakePoint(cvl.longitude, cvl.latitude), 4326)::geography,
            ST_SetSRID(ST_MakePoint(:longitude, :latitude), 4326)::geography
        )
        """, nativeQuery = true)
    List<CurrentVehicleLocation> findVehiclesWithinRadius(
        @Param("latitude") BigDecimal latitude,
        @Param("longitude") BigDecimal longitude,
        @Param("radiusKm") BigDecimal radiusKm);

    /**
     * Find stale vehicle locations (older than threshold).
     * Used for cleanup and alerting operations.
     * 
     * @param thresholdTime cutoff time for staleness
     * @return stale vehicle locations
     */
    List<CurrentVehicleLocation> findByTimestampBefore(LocalDateTime thresholdTime);

    /**
     * Find vehicles currently on active trips.
     * 
     * @return vehicles with active trips
     */
    @Query("SELECT cvl FROM CurrentVehicleLocation cvl WHERE cvl.trip IS NOT NULL")
    List<CurrentVehicleLocation> findVehiclesOnTrips();

    /**
     * Count vehicles with recent location updates.
     * Used for monitoring and dashboard metrics.
     * 
     * @param sinceTime time threshold for "recent"
     * @return count of active vehicles
     */
    @Query("SELECT COUNT(cvl) FROM CurrentVehicleLocation cvl WHERE cvl.timestamp >= :sinceTime")
    long countActiveVehiclesSince(@Param("sinceTime") LocalDateTime sinceTime);

    /**
     * Find vehicles with poor GPS accuracy.
     * Used for data quality monitoring.
     * 
     * @param maxAccuracy maximum acceptable accuracy in meters
     * @return vehicles with poor GPS accuracy
     */
    @Query("SELECT cvl FROM CurrentVehicleLocation cvl WHERE cvl.accuracy > :maxAccuracy")
    List<CurrentVehicleLocation> findVehiclesWithPoorAccuracy(@Param("maxAccuracy") BigDecimal maxAccuracy);

    /**
     * Update current location with upsert behavior.
     * PostgreSQL-specific ON CONFLICT clause for atomic upserts.
     * 
     * @param vehicleId vehicle identifier
     * @param tripId current trip (null if not on trip)
     * @param latitude GPS latitude
     * @param longitude GPS longitude
     * @param altitude GPS altitude
     * @param accuracy GPS accuracy
     * @param speed vehicle speed
     * @param heading vehicle heading
     * @param timestamp GPS timestamp
     * @param batteryLevel device battery level
     * @param signalStrength GPS signal strength
     * @param metadata additional metadata as JSON
     */
    @Modifying
    @Query(value = """
        INSERT INTO current_vehicle_locations (
            vehicle_id, trip_id, latitude, longitude, altitude, accuracy, 
            speed, heading, timestamp, battery_level, signal_strength, 
            metadata, created_at, updated_at
        ) VALUES (
            :vehicleId, :tripId, :latitude, :longitude, :altitude, :accuracy,
            :speed, :heading, :timestamp, :batteryLevel, :signalStrength,
            CAST(:metadata AS jsonb), NOW(), NOW()
        )
        ON CONFLICT (vehicle_id) DO UPDATE SET
            trip_id = EXCLUDED.trip_id,
            latitude = EXCLUDED.latitude,
            longitude = EXCLUDED.longitude,
            altitude = EXCLUDED.altitude,
            accuracy = EXCLUDED.accuracy,
            speed = EXCLUDED.speed,
            heading = EXCLUDED.heading,
            timestamp = EXCLUDED.timestamp,
            battery_level = EXCLUDED.battery_level,
            signal_strength = EXCLUDED.signal_strength,
            metadata = EXCLUDED.metadata,
            updated_at = NOW()
        WHERE current_vehicle_locations.timestamp < EXCLUDED.timestamp
        """, nativeQuery = true)
    int upsertLocation(
        @Param("vehicleId") Long vehicleId,
        @Param("tripId") Long tripId,
        @Param("latitude") BigDecimal latitude,
        @Param("longitude") BigDecimal longitude,
        @Param("altitude") BigDecimal altitude,
        @Param("accuracy") BigDecimal accuracy,
        @Param("speed") BigDecimal speed,
        @Param("heading") BigDecimal heading,
        @Param("timestamp") LocalDateTime timestamp,
        @Param("batteryLevel") Integer batteryLevel,
        @Param("signalStrength") Integer signalStrength,
        @Param("metadata") String metadata);

    /**
     * Delete stale vehicle locations.
     * Used for cleanup operations.
     * 
     * @param thresholdTime cutoff time for deletion
     * @return number of records deleted
     */
    @Modifying
    @Query("DELETE FROM CurrentVehicleLocation cvl WHERE cvl.timestamp < :thresholdTime")
    int deleteStaleLocations(@Param("thresholdTime") LocalDateTime thresholdTime);

    /**
     * Delete location for specific vehicle.
     * Used when vehicle becomes inactive.
     * 
     * @param vehicleId vehicle identifier
     * @return number of records deleted
     */
    @Modifying
    int deleteByVehicleId(Long vehicleId);

    /**
     * Get location statistics for monitoring dashboard.
     * 
     * @return statistics as object array [total, active, stale, poor_accuracy]
     */
    @Query(value = """
        SELECT 
            COUNT(*) as total,
            COUNT(CASE WHEN timestamp >= NOW() - INTERVAL '10 minutes' THEN 1 END) as active,
            COUNT(CASE WHEN timestamp < NOW() - INTERVAL '30 minutes' THEN 1 END) as stale,
            COUNT(CASE WHEN accuracy > 100 THEN 1 END) as poor_accuracy
        FROM current_vehicle_locations
        """, nativeQuery = true)
    Object[] getLocationStatistics();
}