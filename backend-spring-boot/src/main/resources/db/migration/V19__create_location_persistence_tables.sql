-- Migration V19: Create Location Persistence Tables
-- Implements two-tier architecture for GPS location storage

-- ========================================================================
-- Current Vehicle Locations Table
-- ========================================================================
-- Stores the most recent GPS position for each vehicle
-- Optimized for real-time location queries (one record per vehicle)

CREATE TABLE current_vehicle_locations (
    vehicle_id BIGINT PRIMARY KEY REFERENCES vehicles(id) ON DELETE CASCADE,
    trip_id BIGINT REFERENCES trips(id) ON DELETE SET NULL,
    latitude DECIMAL(10,8) NOT NULL,
    longitude DECIMAL(11,8) NOT NULL,
    altitude DECIMAL(8,2),
    accuracy DECIMAL(6,2),
    speed DECIMAL(5,2),
    heading DECIMAL(5,2),
    timestamp TIMESTAMP NOT NULL,
    battery_level INTEGER CHECK (battery_level >= 0 AND battery_level <= 100),
    signal_strength INTEGER,
    metadata JSONB DEFAULT '{}',
    created_at TIMESTAMP DEFAULT NOW() NOT NULL,
    updated_at TIMESTAMP DEFAULT NOW() NOT NULL
);

-- Indexes for current locations
CREATE INDEX idx_current_locations_trip ON current_vehicle_locations(trip_id) WHERE trip_id IS NOT NULL;
CREATE INDEX idx_current_locations_timestamp ON current_vehicle_locations(timestamp DESC);

-- Geospatial index for proximity queries (requires PostGIS)
CREATE INDEX idx_current_locations_geo ON current_vehicle_locations USING GIST (
    ST_SetSRID(ST_MakePoint(longitude, latitude), 4326)
);

-- Filter recent locations at query time; NOW() is not immutable and cannot
-- appear in a PostgreSQL index predicate.
CREATE INDEX idx_current_locations_active ON current_vehicle_locations(vehicle_id, timestamp);

-- ========================================================================
-- Vehicle Location History Table (Partitioned)
-- ========================================================================
-- Stores complete GPS tracking history for analytics and compliance
-- Partitioned by timestamp for efficient querying and archival

CREATE TABLE vehicle_location_history (
    id BIGSERIAL,
    vehicle_id BIGINT NOT NULL REFERENCES vehicles(id) ON DELETE CASCADE,
    trip_id BIGINT REFERENCES trips(id) ON DELETE SET NULL,
    latitude DECIMAL(10,8) NOT NULL,
    longitude DECIMAL(11,8) NOT NULL,
    altitude DECIMAL(8,2),
    accuracy DECIMAL(6,2),
    speed DECIMAL(5,2),
    heading DECIMAL(5,2),
    timestamp TIMESTAMP NOT NULL,
    battery_level INTEGER CHECK (battery_level >= 0 AND battery_level <= 100),
    signal_strength INTEGER,
    metadata JSONB DEFAULT '{}',
    created_at TIMESTAMP DEFAULT NOW() NOT NULL
) PARTITION BY RANGE (timestamp);

-- Create initial partitions (current year + 2 future years)
-- This allows for advance partition creation and smooth operations

-- Previous year partition (for historical data)
CREATE TABLE vehicle_location_history_y2023 PARTITION OF vehicle_location_history
    FOR VALUES FROM ('2023-01-01 00:00:00') TO ('2024-01-01 00:00:00');

-- Current year partitions (monthly for better management)
CREATE TABLE vehicle_location_history_y2024m01 PARTITION OF vehicle_location_history
    FOR VALUES FROM ('2024-01-01 00:00:00') TO ('2024-02-01 00:00:00');

CREATE TABLE vehicle_location_history_y2024m02 PARTITION OF vehicle_location_history
    FOR VALUES FROM ('2024-02-01 00:00:00') TO ('2024-03-01 00:00:00');

CREATE TABLE vehicle_location_history_y2024m03 PARTITION OF vehicle_location_history
    FOR VALUES FROM ('2024-03-01 00:00:00') TO ('2024-04-01 00:00:00');

CREATE TABLE vehicle_location_history_y2024m04 PARTITION OF vehicle_location_history
    FOR VALUES FROM ('2024-04-01 00:00:00') TO ('2024-05-01 00:00:00');

CREATE TABLE vehicle_location_history_y2024m05 PARTITION OF vehicle_location_history
    FOR VALUES FROM ('2024-05-01 00:00:00') TO ('2024-06-01 00:00:00');

CREATE TABLE vehicle_location_history_y2024m06 PARTITION OF vehicle_location_history
    FOR VALUES FROM ('2024-06-01 00:00:00') TO ('2024-07-01 00:00:00');

CREATE TABLE vehicle_location_history_y2024m07 PARTITION OF vehicle_location_history
    FOR VALUES FROM ('2024-07-01 00:00:00') TO ('2024-08-01 00:00:00');

CREATE TABLE vehicle_location_history_y2024m08 PARTITION OF vehicle_location_history
    FOR VALUES FROM ('2024-08-01 00:00:00') TO ('2024-09-01 00:00:00');

CREATE TABLE vehicle_location_history_y2024m09 PARTITION OF vehicle_location_history
    FOR VALUES FROM ('2024-09-01 00:00:00') TO ('2024-10-01 00:00:00');

CREATE TABLE vehicle_location_history_y2024m10 PARTITION OF vehicle_location_history
    FOR VALUES FROM ('2024-10-01 00:00:00') TO ('2024-11-01 00:00:00');

CREATE TABLE vehicle_location_history_y2024m11 PARTITION OF vehicle_location_history
    FOR VALUES FROM ('2024-11-01 00:00:00') TO ('2024-12-01 00:00:00');

CREATE TABLE vehicle_location_history_y2024m12 PARTITION OF vehicle_location_history
    FOR VALUES FROM ('2024-12-01 00:00:00') TO ('2025-01-01 00:00:00');

-- Future year partitions (quarterly for advance planning)
CREATE TABLE vehicle_location_history_y2025q1 PARTITION OF vehicle_location_history
    FOR VALUES FROM ('2025-01-01 00:00:00') TO ('2025-04-01 00:00:00');

CREATE TABLE vehicle_location_history_y2025q2 PARTITION OF vehicle_location_history
    FOR VALUES FROM ('2025-04-01 00:00:00') TO ('2025-07-01 00:00:00');

CREATE TABLE vehicle_location_history_y2025q3 PARTITION OF vehicle_location_history
    FOR VALUES FROM ('2025-07-01 00:00:00') TO ('2025-10-01 00:00:00');

CREATE TABLE vehicle_location_history_y2025q4 PARTITION OF vehicle_location_history
    FOR VALUES FROM ('2025-10-01 00:00:00') TO ('2026-01-01 00:00:00');

-- Indexes for partitioned table (applied to all partitions)
-- Primary query patterns: by vehicle+time, by trip+time, spatial queries

CREATE INDEX ON vehicle_location_history (vehicle_id, timestamp DESC);
CREATE INDEX ON vehicle_location_history (trip_id, timestamp) WHERE trip_id IS NOT NULL;
CREATE INDEX ON vehicle_location_history (timestamp DESC, vehicle_id);

-- Geospatial index for location-based queries
CREATE INDEX ON vehicle_location_history USING GIST (
    ST_SetSRID(ST_MakePoint(longitude, latitude), 4326)
);

-- JSONB index for metadata queries
CREATE INDEX ON vehicle_location_history USING GIN (metadata);

-- Partial index for high-quality GPS data
CREATE INDEX ON vehicle_location_history (vehicle_id, timestamp)
WHERE (accuracy IS NULL OR accuracy <= 50);

-- ========================================================================
-- Data Integrity and Performance Optimizations
-- ========================================================================

-- Add constraints for data quality
ALTER TABLE current_vehicle_locations ADD CONSTRAINT chk_current_latitude 
    CHECK (latitude >= -90 AND latitude <= 90);
ALTER TABLE current_vehicle_locations ADD CONSTRAINT chk_current_longitude 
    CHECK (longitude >= -180 AND longitude <= 180);
ALTER TABLE current_vehicle_locations ADD CONSTRAINT chk_current_accuracy 
    CHECK (accuracy IS NULL OR accuracy >= 0);
ALTER TABLE current_vehicle_locations ADD CONSTRAINT chk_current_speed 
    CHECK (speed IS NULL OR speed >= 0);
ALTER TABLE current_vehicle_locations ADD CONSTRAINT chk_current_heading 
    CHECK (heading IS NULL OR (heading >= 0 AND heading <= 360));

ALTER TABLE vehicle_location_history ADD CONSTRAINT chk_history_latitude 
    CHECK (latitude >= -90 AND latitude <= 90);
ALTER TABLE vehicle_location_history ADD CONSTRAINT chk_history_longitude 
    CHECK (longitude >= -180 AND longitude <= 180);
ALTER TABLE vehicle_location_history ADD CONSTRAINT chk_history_accuracy 
    CHECK (accuracy IS NULL OR accuracy >= 0);
ALTER TABLE vehicle_location_history ADD CONSTRAINT chk_history_speed 
    CHECK (speed IS NULL OR speed >= 0);
ALTER TABLE vehicle_location_history ADD CONSTRAINT chk_history_heading 
    CHECK (heading IS NULL OR (heading >= 0 AND heading <= 360));

-- ========================================================================
-- Triggers and Functions
-- ========================================================================

-- Function to automatically update updated_at timestamp
CREATE OR REPLACE FUNCTION update_current_location_timestamp()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ language plpgsql;

-- Trigger for automatic timestamp updates
CREATE TRIGGER trigger_current_location_updated_at
    BEFORE UPDATE ON current_vehicle_locations
    FOR EACH ROW
    EXECUTE FUNCTION update_current_location_timestamp();

-- Function for automatic partition creation (monthly)
CREATE OR REPLACE FUNCTION create_monthly_location_partition(partition_date DATE)
RETURNS void AS $$
DECLARE
    partition_name text;
    start_date date;
    end_date date;
BEGIN
    -- Calculate partition boundaries
    start_date := date_trunc('month', partition_date);
    end_date := start_date + interval '1 month';
    
    -- Generate partition name
    partition_name := 'vehicle_location_history_y' || 
                     extract(year from start_date)::text || 
                     'm' || 
                     lpad(extract(month from start_date)::text, 2, '0');
    
    -- Create partition if it doesn't exist
    EXECUTE format('CREATE TABLE IF NOT EXISTS %I PARTITION OF vehicle_location_history
                    FOR VALUES FROM (%L) TO (%L)',
                    partition_name, start_date, end_date);
    
    -- Create indexes on new partition
    EXECUTE format('CREATE INDEX IF NOT EXISTS %I ON %I (vehicle_id, timestamp DESC)',
                    'idx_' || partition_name || '_vehicle_time', partition_name);
                    
    EXECUTE format('CREATE INDEX IF NOT EXISTS %I ON %I (trip_id, timestamp) WHERE trip_id IS NOT NULL',
                    'idx_' || partition_name || '_trip_time', partition_name);
END;
$$ LANGUAGE plpgsql;

-- ========================================================================
-- Comments and Documentation
-- ========================================================================

COMMENT ON TABLE current_vehicle_locations IS 'Real-time vehicle locations for tracking and fleet management. One record per vehicle (upsert pattern).';
COMMENT ON TABLE vehicle_location_history IS 'Complete GPS tracking history partitioned by timestamp. Used for analytics and route reconstruction.';

COMMENT ON COLUMN current_vehicle_locations.vehicle_id IS 'Vehicle identifier (PK). One current location per vehicle.';
COMMENT ON COLUMN current_vehicle_locations.trip_id IS 'Active trip ID. NULL if vehicle not on a trip.';
COMMENT ON COLUMN current_vehicle_locations.accuracy IS 'GPS accuracy radius in meters. NULL if not provided by device.';
COMMENT ON COLUMN current_vehicle_locations.metadata IS 'Additional data as JSONB (device info, driver info, etc.)';

COMMENT ON COLUMN vehicle_location_history.timestamp IS 'GPS reading timestamp. Used as partition key.';
COMMENT ON COLUMN vehicle_location_history.metadata IS 'Additional tracking data as JSONB (expandable schema)';

-- ========================================================================
-- Performance Statistics and Monitoring
-- ========================================================================

-- View for location system statistics
CREATE VIEW location_system_stats AS
SELECT 
    'current_locations' as table_name,
    COUNT(*) as record_count,
    COUNT(CASE WHEN timestamp >= NOW() - INTERVAL '10 minutes' THEN 1 END) as active_count,
    COUNT(CASE WHEN timestamp < NOW() - INTERVAL '1 hour' THEN 1 END) as stale_count,
    AVG(accuracy) as avg_accuracy_meters,
    MIN(timestamp) as oldest_record,
    MAX(timestamp) as newest_record
FROM current_vehicle_locations

UNION ALL

SELECT 
    'history_locations' as table_name,
    COUNT(*) as record_count,
    COUNT(CASE WHEN timestamp >= NOW() - INTERVAL '1 day' THEN 1 END) as recent_count,
    COUNT(CASE WHEN accuracy > 100 THEN 1 END) as poor_accuracy_count,
    AVG(accuracy) as avg_accuracy_meters,
    MIN(timestamp) as oldest_record,
    MAX(timestamp) as newest_record
FROM vehicle_location_history
WHERE timestamp >= NOW() - INTERVAL '7 days'; -- Sample recent data for performance

COMMENT ON VIEW location_system_stats IS 'Real-time statistics for location tracking system monitoring and alerting.';

-- Completion message
DO $$
BEGIN
    RAISE NOTICE 'Location persistence tables created successfully';
    RAISE NOTICE 'Current locations table: ready for real-time tracking';
    RAISE NOTICE 'History table: partitioned and indexed for analytics';
    RAISE NOTICE 'Remember to create future partitions before they are needed';
END $$;
