package com.ethioloadai.tracking.repository;

import com.ethioloadai.tracking.entity.VehicleLocationHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Repository for vehicle location history.
 * Optimized for historical data queries and analytics.
 */
@Repository
public interface VehicleLocationHistoryRepository extends JpaRepository<VehicleLocationHistory, Long> {

    /**
     * Find location history for a vehicle within time range.
     * Most common query pattern for route reconstruction.
     * 
     * @param vehicleId vehicle identifier
     * @param startTime range start (inclusive)
     * @param endTime range end (inclusive)
     * @param pageable pagination parameters
     * @return location history ordered by timestamp
     */
    @Query("SELECT vlh FROM VehicleLocationHistory vlh " +
           "WHERE vlh.vehicleId = :vehicleId " +
           "AND vlh.timestamp BETWEEN :startTime AND :endTime " +
           "ORDER BY vlh.timestamp ASC")
    Page<VehicleLocationHistory> findByVehicleIdAndTimestampBetween(
        @Param("vehicleId") Long vehicleId,
        @Param("startTime") LocalDateTime startTime,
        @Param("endTime") LocalDateTime endTime,
        Pageable pageable);

    /**
     * Find location history for a trip.
     * Used for trip route analysis and reporting.
     * 
     * @param tripId trip identifier
     * @param pageable pagination parameters
     * @return location history for trip ordered by timestamp
     */
    @Query("SELECT vlh FROM VehicleLocationHistory vlh " +
           "WHERE vlh.tripId = :tripId " +
           "ORDER BY vlh.timestamp ASC")
    Page<VehicleLocationHistory> findByTripId(@Param("tripId") Long tripId, Pageable pageable);

    /**
     * Find recent location history for a vehicle.
     * Used for real-time tracking and recent route display.
     * 
     * @param vehicleId vehicle identifier
     * @param limit maximum number of points
     * @return recent locations ordered by timestamp descending
     */
    @Query(value = """
        SELECT * FROM vehicle_location_history vlh 
        WHERE vlh.vehicle_id = :vehicleId 
        ORDER BY vlh.timestamp DESC 
        LIMIT :limit
        """, nativeQuery = true)
    List<VehicleLocationHistory> findRecentByVehicleId(
        @Param("vehicleId") Long vehicleId,
        @Param("limit") int limit);

    /**
     * Find location history with good GPS accuracy.
     * Used for route calculations where accuracy matters.
     * 
     * @param vehicleId vehicle identifier
     * @param startTime range start
     * @param endTime range end
     * @param maxAccuracy maximum acceptable accuracy in meters
     * @return accurate location points
     */
    @Query("SELECT vlh FROM VehicleLocationHistory vlh " +
           "WHERE vlh.vehicleId = :vehicleId " +
           "AND vlh.timestamp BETWEEN :startTime AND :endTime " +
           "AND (vlh.accuracy IS NULL OR vlh.accuracy <= :maxAccuracy) " +
           "ORDER BY vlh.timestamp ASC")
    List<VehicleLocationHistory> findAccurateLocationsByVehicleAndTimeRange(
        @Param("vehicleId") Long vehicleId,
        @Param("startTime") LocalDateTime startTime,
        @Param("endTime") LocalDateTime endTime,
        @Param("maxAccuracy") BigDecimal maxAccuracy);

    /**
     * Find vehicles that passed through a geographic area.
     * Uses PostGIS for spatial queries.
     * 
     * @param latitude center point latitude
     * @param longitude center point longitude
     * @param radiusKm search radius in kilometers
     * @param startTime time range start
     * @param endTime time range end
     * @return vehicles that passed through the area
     */
    @Query(value = """
        SELECT DISTINCT vehicle_id FROM vehicle_location_history vlh
        WHERE vlh.timestamp BETWEEN :startTime AND :endTime
        AND ST_DWithin(
            ST_SetSRID(ST_MakePoint(vlh.longitude, vlh.latitude), 4326)::geography,
            ST_SetSRID(ST_MakePoint(:longitude, :latitude), 4326)::geography,
            :radiusKm * 1000
        )
        """, nativeQuery = true)
    List<Long> findVehiclesThatPassedThroughArea(
        @Param("latitude") BigDecimal latitude,
        @Param("longitude") BigDecimal longitude,
        @Param("radiusKm") BigDecimal radiusKm,
        @Param("startTime") LocalDateTime startTime,
        @Param("endTime") LocalDateTime endTime);

    /**
     * Calculate total distance traveled by vehicle.
     * Uses PostGIS for accurate geographic calculations.
     * 
     * @param vehicleId vehicle identifier
     * @param startTime calculation period start
     * @param endTime calculation period end
     * @return total distance in kilometers
     */
    @Query(value = """
        WITH ordered_points AS (
            SELECT latitude, longitude, timestamp,
                   LAG(latitude) OVER (ORDER BY timestamp) AS prev_lat,
                   LAG(longitude) OVER (ORDER BY timestamp) AS prev_lon
            FROM vehicle_location_history
            WHERE vehicle_id = :vehicleId 
            AND timestamp BETWEEN :startTime AND :endTime
            AND (accuracy IS NULL OR accuracy <= 100)
            ORDER BY timestamp
        )
        SELECT COALESCE(SUM(
            ST_Distance(
                ST_SetSRID(ST_MakePoint(prev_lon, prev_lat), 4326)::geography,
                ST_SetSRID(ST_MakePoint(longitude, latitude), 4326)::geography
            ) / 1000
        ), 0) as total_distance_km
        FROM ordered_points 
        WHERE prev_lat IS NOT NULL AND prev_lon IS NOT NULL
        """, nativeQuery = true)
    BigDecimal calculateTotalDistanceKm(
        @Param("vehicleId") Long vehicleId,
        @Param("startTime") LocalDateTime startTime,
        @Param("endTime") LocalDateTime endTime);

    /**
     * Find location anomalies (sudden position jumps).
     * Detects potential GPS errors or data quality issues.
     * 
     * @param vehicleId vehicle identifier
     * @param startTime analysis period start
     * @param endTime analysis period end
     * @param maxSpeedKmh maximum realistic speed
     * @return potentially anomalous location points
     */
    @Query(value = """
        WITH speed_calculations AS (
            SELECT vlh.*, 
                   LAG(vlh.latitude) OVER (ORDER BY vlh.timestamp) AS prev_lat,
                   LAG(vlh.longitude) OVER (ORDER BY vlh.timestamp) AS prev_lon,
                   LAG(vlh.timestamp) OVER (ORDER BY vlh.timestamp) AS prev_time
            FROM vehicle_location_history vlh
            WHERE vlh.vehicle_id = :vehicleId 
            AND vlh.timestamp BETWEEN :startTime AND :endTime
            ORDER BY vlh.timestamp
        )
        SELECT * FROM speed_calculations sc
        WHERE prev_lat IS NOT NULL 
        AND prev_lon IS NOT NULL 
        AND prev_time IS NOT NULL
        AND (
            ST_Distance(
                ST_SetSRID(ST_MakePoint(prev_lon, prev_lat), 4326)::geography,
                ST_SetSRID(ST_MakePoint(longitude, latitude), 4326)::geography
            ) / EXTRACT(EPOCH FROM (timestamp - prev_time)) * 3.6
        ) > :maxSpeedKmh
        """, nativeQuery = true)
    List<VehicleLocationHistory> findLocationAnomalies(
        @Param("vehicleId") Long vehicleId,
        @Param("startTime") LocalDateTime startTime,
        @Param("endTime") LocalDateTime endTime,
        @Param("maxSpeedKmh") BigDecimal maxSpeedKmh);

    /**
     * Batch insert location history records.
     * Optimized for high-throughput GPS data ingestion.
     * 
     * @param locations batch of location records to insert
     * @return number of records inserted
     */
    @Modifying
    @Query(value = """
        INSERT INTO vehicle_location_history (
            vehicle_id, trip_id, latitude, longitude, altitude, accuracy, 
            speed, heading, timestamp, battery_level, signal_strength, 
            metadata, created_at
        ) VALUES ?1
        """, nativeQuery = true)
    int batchInsertLocations(String valuesClause);

    /**
     * Delete old location history for archival.
     * Removes data older than retention policy.
     * 
     * @param beforeTime cutoff time for deletion
     * @return number of records deleted
     */
    @Modifying
    @Query("DELETE FROM VehicleLocationHistory vlh WHERE vlh.timestamp < :beforeTime")
    int deleteOldLocations(@Param("beforeTime") LocalDateTime beforeTime);

    /**
     * Count location records by vehicle and time range.
     * Used for data quality and completeness monitoring.
     * 
     * @param vehicleId vehicle identifier
     * @param startTime count period start
     * @param endTime count period end
     * @return number of location records
     */
    @Query("SELECT COUNT(vlh) FROM VehicleLocationHistory vlh " +
           "WHERE vlh.vehicleId = :vehicleId " +
           "AND vlh.timestamp BETWEEN :startTime AND :endTime")
    long countByVehicleIdAndTimestampBetween(
        @Param("vehicleId") Long vehicleId,
        @Param("startTime") LocalDateTime startTime,
        @Param("endTime") LocalDateTime endTime);

    /**
     * Get location history statistics for monitoring.
     * 
     * @param startTime statistics period start
     * @param endTime statistics period end
     * @return statistics as object array [total_records, unique_vehicles, avg_accuracy, data_completeness]
     */
    @Query(value = """
        SELECT 
            COUNT(*) as total_records,
            COUNT(DISTINCT vehicle_id) as unique_vehicles,
            AVG(accuracy) as avg_accuracy_meters,
            COUNT(CASE WHEN accuracy <= 50 THEN 1 END)::float / COUNT(*) as good_accuracy_ratio
        FROM vehicle_location_history
        WHERE timestamp BETWEEN :startTime AND :endTime
        """, nativeQuery = true)
    Object[] getLocationStatistics(
        @Param("startTime") LocalDateTime startTime,
        @Param("endTime") LocalDateTime endTime);

    /**
     * Find first and last location for a vehicle in time range.
     * Used for trip boundary detection and route summaries.
     */
    @Query(value = """
        (SELECT * FROM vehicle_location_history 
         WHERE vehicle_id = :vehicleId 
         AND timestamp BETWEEN :startTime AND :endTime 
         ORDER BY timestamp ASC LIMIT 1)
        UNION ALL
        (SELECT * FROM vehicle_location_history 
         WHERE vehicle_id = :vehicleId 
         AND timestamp BETWEEN :startTime AND :endTime 
         ORDER BY timestamp DESC LIMIT 1)
        """, nativeQuery = true)
    List<VehicleLocationHistory> findFirstAndLastLocation(
        @Param("vehicleId") Long vehicleId,
        @Param("startTime") LocalDateTime startTime,
        @Param("endTime") LocalDateTime endTime);

    // ── Phase 15 additions ────────────────────────────────────────────────────

    /**
     * Fetch all points for a trip ordered chronologically, capped at limit.
     */
    @Query(value = """
        SELECT * FROM vehicle_location_history
        WHERE trip_id = :tripId
        ORDER BY timestamp ASC
        LIMIT :limit
        """, nativeQuery = true)
    List<VehicleLocationHistory> findByTripIdOrderByTimestampAsc(
        @Param("tripId")  Long tripId,
        @Param("limit")   int  limit);

    /**
     * Ordered history for a vehicle within a time range (no pagination).
     */
    @Query("SELECT vlh FROM VehicleLocationHistory vlh " +
           "WHERE vlh.vehicleId = :vehicleId " +
           "AND vlh.timestamp BETWEEN :from AND :to " +
           "ORDER BY vlh.timestamp ASC")
    List<VehicleLocationHistory> findByVehicleIdAndTimestampBetweenOrderByTimestampAsc(
        @Param("vehicleId") Long vehicleId,
        @Param("from")      LocalDateTime from,
        @Param("to")        LocalDateTime to);

    /**
     * Most recent single location point for a vehicle.
     */
    @Query("SELECT vlh FROM VehicleLocationHistory vlh " +
           "WHERE vlh.vehicleId = :vehicleId " +
           "ORDER BY vlh.timestamp DESC")
    List<VehicleLocationHistory> findTopByVehicleIdOrderByTimestampDesc(
        @Param("vehicleId") Long vehicleId,
        org.springframework.data.domain.Pageable pageable);

    /**
     * Delete records for a vehicle older than a given time.
     * Returns the number of rows deleted.
     */
    @Modifying
    @Query("DELETE FROM VehicleLocationHistory vlh " +
           "WHERE vlh.vehicleId = :vehicleId AND vlh.timestamp < :before")
    int deleteByVehicleIdAndTimestampBefore(
        @Param("vehicleId") Long vehicleId,
        @Param("before")    LocalDateTime before);
}