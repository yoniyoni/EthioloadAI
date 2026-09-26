-- V16: Performance optimization indexes for trip management
--
-- This migration adds strategic database indexes to optimize query performance
-- for the comprehensive trip management system implemented in Milestone 5.
--
-- PERFORMANCE ANALYSIS:
-- - Driver queries: High frequency, need optimized joins
-- - Date range queries: Common for analytics, need time-based indexes  
-- - Status queries: Frequent monitoring, need status-specific indexes
-- - Route data: JSONB queries need GIN indexes for efficient searching
-- - Multi-table relationships: Foreign key optimization

-- ═══════════════════════════════════════════════════════════════════════════
-- TRIPS TABLE PERFORMANCE INDEXES
-- ═══════════════════════════════════════════════════════════════════════════

-- Optimize date range queries (analytics, reporting)
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_trips_start_time_desc 
ON trips USING btree (start_time DESC NULLS LAST);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_trips_end_time_desc 
ON trips USING btree (end_time DESC NULLS LAST) 
WHERE end_time IS NOT NULL;

-- Status-based queries optimization
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_trips_status_created 
ON trips USING btree (trip_status, created_at DESC);

-- Multi-stop trip analytics
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_trips_type_stops 
ON trips USING btree (trip_type, total_stops, completed_stops)
WHERE trip_type = 'multi_stop';

-- Route data JSONB optimization for GPS queries
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_trips_route_data_gin 
ON trips USING gin (route_data);

-- Ongoing trips monitoring (for overdue detection)
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_trips_ongoing_start_time 
ON trips USING btree (start_time ASC)
WHERE trip_status = 'ongoing';

-- ═══════════════════════════════════════════════════════════════════════════  
-- TRIP_STOPS TABLE PERFORMANCE INDEXES
-- ═══════════════════════════════════════════════════════════════════════════

-- Stop status and order optimization (sequential completion)
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_trip_stops_status_order 
ON trip_stops USING btree (trip_id, stop_status, stop_order);

-- GPS coordinate queries
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_trip_stops_coordinates 
ON trip_stops USING btree (pickup_lat, pickup_lng)
WHERE pickup_lat IS NOT NULL AND pickup_lng IS NOT NULL;

-- Timeline analysis
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_trip_stops_timeline 
ON trip_stops USING btree (trip_id, arrived_at NULLS LAST, completed_at NULLS LAST);

-- Price aggregation optimization
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_trip_stops_price 
ON trip_stops USING btree (trip_id, agreed_price DESC);

-- Cargo relationship queries
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_trip_stops_cargo_status 
ON trip_stops USING btree (cargo_request_id, stop_status)
WHERE cargo_request_id IS NOT NULL;

-- ═══════════════════════════════════════════════════════════════════════════
-- FOREIGN KEY RELATIONSHIP OPTIMIZATION
-- ═══════════════════════════════════════════════════════════════════════════

-- Note: Primary foreign key indexes already exist from V9 migration
-- Adding performance-specific indexes for frequent join patterns

-- Booking relationship performance (hash for unique lookups)
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_trips_booking_hash 
ON trips USING hash (booking_id);

-- Enhance existing cargo_request_id index with status information  
-- (Supplements existing idx_trip_stops_cargo_request_id from V9)
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_trip_stops_cargo_perf 
ON trip_stops USING btree (cargo_request_id, stop_order) 
WHERE cargo_request_id IS NOT NULL;

-- ═══════════════════════════════════════════════════════════════════════════
-- ADDITIONAL CONSTRAINTS AND VALIDATIONS
-- ═══════════════════════════════════════════════════════════════════════════

-- Add check constraint to ensure completed_stops <= total_stops
ALTER TABLE trips 
ADD CONSTRAINT chk_trips_completed_stops_valid 
CHECK (completed_stops <= total_stops);

-- Add check constraint for logical start/end time relationship
ALTER TABLE trips 
ADD CONSTRAINT chk_trips_time_sequence 
CHECK (end_time IS NULL OR start_time IS NULL OR end_time >= start_time);

-- Add check constraint for stop timeline logic
ALTER TABLE trip_stops 
ADD CONSTRAINT chk_trip_stops_timeline_logic 
CHECK (completed_at IS NULL OR arrived_at IS NULL OR completed_at >= arrived_at);

-- ═══════════════════════════════════════════════════════════════════════════
-- ROUTE DATA JSONB ENHANCEMENTS
-- ═══════════════════════════════════════════════════════════════════════════

-- Add check constraint to prevent unbounded route_data growth
-- This enforces the 500-point limit mentioned in the entity documentation
ALTER TABLE trips 
ADD CONSTRAINT chk_trips_route_data_size 
CHECK (jsonb_array_length(route_data) <= 500);

-- Create function to validate route data structure
CREATE OR REPLACE FUNCTION validate_route_data_structure(route_data jsonb) 
RETURNS boolean AS $$
BEGIN
    -- Check if it's an array
    IF jsonb_typeof(route_data) != 'array' THEN
        RETURN false;
    END IF;
    
    -- Check array length limit
    IF jsonb_array_length(route_data) > 500 THEN
        RETURN false;
    END IF;
    
    -- Additional validation could check individual GPS point structure
    -- For now, just basic array validation
    RETURN true;
END;
$$ LANGUAGE plpgsql IMMUTABLE;

-- Add constraint using validation function
ALTER TABLE trips 
ADD CONSTRAINT chk_trips_route_data_structure 
CHECK (validate_route_data_structure(route_data));

-- ═══════════════════════════════════════════════════════════════════════════
-- PERFORMANCE MONITORING AND MAINTENANCE
-- ═══════════════════════════════════════════════════════════════════════════

-- Update statistics for query planner
ANALYZE trips;
ANALYZE trip_stops;

-- Create function to monitor trip index performance
CREATE OR REPLACE FUNCTION get_trip_index_stats()
RETURNS TABLE (
    table_name text,
    index_name text, 
    size text,
    scans bigint,
    tuples_read bigint,
    tuples_fetched bigint
) AS $$
BEGIN
    RETURN QUERY
    SELECT 
        t.schemaname||'.'||t.tablename::text,
        t.indexname::text,
        pg_size_pretty(pg_relation_size(t.schemaname||'.'||t.indexname))::text,
        COALESCE(s.idx_scan, 0)::bigint,
        COALESCE(s.idx_tup_read, 0)::bigint,
        COALESCE(s.idx_tup_fetch, 0)::bigint
    FROM pg_stat_user_indexes s
    RIGHT JOIN pg_indexes t ON s.indexname = t.indexname 
    WHERE t.schemaname = 'public' 
    AND (t.tablename = 'trips' OR t.tablename = 'trip_stops')
    ORDER BY COALESCE(s.idx_scan, 0) DESC;
END;
$$ LANGUAGE plpgsql;

-- Add helpful comments
COMMENT ON INDEX idx_trips_route_data_gin IS 'GIN index for efficient JSONB route data queries and GPS analytics';
COMMENT ON INDEX idx_trips_ongoing_start_time IS 'Optimizes overdue trip detection in monitoring services';
COMMENT ON INDEX idx_trip_stops_status_order IS 'Optimizes sequential stop completion logic in multi-stop workflows';
COMMENT ON CONSTRAINT chk_trips_route_data_size ON trips IS 'Prevents unbounded route_data growth by limiting to 500 GPS points';
COMMENT ON FUNCTION get_trip_index_stats IS 'Monitoring function for trip management index usage statistics';