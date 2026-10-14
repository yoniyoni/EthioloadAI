-- V17: Trip Management Database Functions and Triggers
--
-- This migration adds database-level functions and triggers to support
-- the trip management business logic and maintain data consistency.
--
-- FEATURES:
-- - Automatic trip progress tracking
-- - Data integrity triggers
-- - Performance monitoring functions
-- - Business rule enforcement at database level

-- ═══════════════════════════════════════════════════════════════════════════
-- TRIP PROGRESS TRACKING FUNCTIONS
-- ═══════════════════════════════════════════════════════════════════════════

-- Function to update trip progress counters when stop status changes
CREATE OR REPLACE FUNCTION update_trip_progress()
RETURNS TRIGGER AS $$
DECLARE
    trip_id_val BIGINT;
    completed_count INTEGER;
    total_count INTEGER;
BEGIN
    -- Get the trip ID (works for both INSERT and UPDATE)
    trip_id_val := COALESCE(NEW.trip_id, OLD.trip_id);
    
    -- Count completed stops for this trip
    SELECT COUNT(*) INTO completed_count
    FROM trip_stops 
    WHERE trip_id = trip_id_val AND stop_status = 'completed';
    
    -- Count total stops for this trip
    SELECT COUNT(*) INTO total_count
    FROM trip_stops 
    WHERE trip_id = trip_id_val;
    
    -- Update trip counters
    UPDATE trips 
    SET 
        completed_stops = completed_count,
        total_stops = total_count,
        -- Auto-update trip type based on stop count
        trip_type = CASE 
            WHEN total_count <= 1 THEN 'single'
            ELSE 'multi_stop'
        END,
        updated_at = CURRENT_TIMESTAMP
    WHERE id = trip_id_val;
    
    RETURN COALESCE(NEW, OLD);
END;
$$ LANGUAGE plpgsql;

-- Trigger to automatically update trip progress
CREATE TRIGGER trg_update_trip_progress
    AFTER INSERT OR UPDATE OR DELETE ON trip_stops
    FOR EACH ROW EXECUTE FUNCTION update_trip_progress();

-- ═══════════════════════════════════════════════════════════════════════════
-- DATA INTEGRITY FUNCTIONS
-- ═══════════════════════════════════════════════════════════════════════════

-- Function to validate stop order consistency
CREATE OR REPLACE FUNCTION validate_stop_order_integrity()
RETURNS TRIGGER AS $$
DECLARE
    max_order INTEGER;
    expected_order INTEGER;
BEGIN
    -- For INSERT, validate the stop order makes sense
    IF TG_OP = 'INSERT' THEN
        -- Get the current maximum stop order for this trip
        SELECT COALESCE(MAX(stop_order), 0) INTO max_order
        FROM trip_stops 
        WHERE trip_id = NEW.trip_id;
        
        -- If no stop_order provided, auto-assign next available
        IF NEW.stop_order IS NULL THEN
            NEW.stop_order := max_order + 1;
        -- If stop_order provided, validate it's reasonable
        ELSIF NEW.stop_order > max_order + 1 THEN
            RAISE EXCEPTION 'Stop order % creates gap (max current order: %)', NEW.stop_order, max_order;
        END IF;
    END IF;
    
    -- For UPDATE, ensure stop order changes don't create conflicts
    IF TG_OP = 'UPDATE' AND OLD.stop_order != NEW.stop_order THEN
        -- Check if new order already exists (other than this record)
        IF EXISTS (
            SELECT 1 FROM trip_stops 
            WHERE trip_id = NEW.trip_id 
            AND stop_order = NEW.stop_order 
            AND id != NEW.id
        ) THEN
            RAISE EXCEPTION 'Stop order % already exists for trip %', NEW.stop_order, NEW.trip_id;
        END IF;
    END IF;
    
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Trigger to validate stop order integrity
CREATE TRIGGER trg_validate_stop_order
    BEFORE INSERT OR UPDATE ON trip_stops
    FOR EACH ROW EXECUTE FUNCTION validate_stop_order_integrity();

-- ═══════════════════════════════════════════════════════════════════════════
-- TRIP STATUS MANAGEMENT FUNCTIONS
-- ═══════════════════════════════════════════════════════════════════════════

-- Function to auto-complete trip when all stops are completed
CREATE OR REPLACE FUNCTION auto_complete_trip_check()
RETURNS TRIGGER AS $$
DECLARE
    trip_record trips%ROWTYPE;
    remaining_stops INTEGER;
BEGIN
    -- Only proceed if a stop was just completed
    IF NEW.stop_status = 'completed' AND (OLD IS NULL OR OLD.stop_status != 'completed') THEN
        
        -- Get the trip record
        SELECT * INTO trip_record FROM trips WHERE id = NEW.trip_id;
        
        -- Only check ongoing trips
        IF trip_record.trip_status = 'ongoing' THEN
            
            -- Count remaining incomplete stops
            SELECT COUNT(*) INTO remaining_stops
            FROM trip_stops 
            WHERE trip_id = NEW.trip_id 
            AND stop_status != 'completed';
            
            -- If no incomplete stops remain, auto-complete the trip
            IF remaining_stops = 0 THEN
                UPDATE trips 
                SET 
                    trip_status = 'completed',
                    end_time = CURRENT_TIMESTAMP,
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = NEW.trip_id;
                
                -- Log the auto-completion
                INSERT INTO trip_audit_log (trip_id, action, details, created_at)
                VALUES (
                    NEW.trip_id, 
                    'AUTO_COMPLETED', 
                    'Trip auto-completed when all stops finished',
                    CURRENT_TIMESTAMP
                );
            END IF;
        END IF;
    END IF;
    
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Create audit log table if it doesn't exist
CREATE TABLE IF NOT EXISTS trip_audit_log (
    id BIGSERIAL PRIMARY KEY,
    trip_id BIGINT NOT NULL REFERENCES trips(id) ON DELETE CASCADE,
    action VARCHAR(50) NOT NULL,
    details TEXT,
    user_id BIGINT,  -- Optional user who performed the action
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_trip_audit_log_trip_id ON trip_audit_log (trip_id);
CREATE INDEX IF NOT EXISTS idx_trip_audit_log_created_at ON trip_audit_log (created_at DESC);

-- Trigger for auto-completion
CREATE TRIGGER trg_auto_complete_trip
    AFTER UPDATE ON trip_stops
    FOR EACH ROW EXECUTE FUNCTION auto_complete_trip_check();

-- ═══════════════════════════════════════════════════════════════════════════
-- ROUTE DATA MANAGEMENT FUNCTIONS  
-- ═══════════════════════════════════════════════════════════════════════════

-- Function to manage route_data size and prevent unbounded growth
CREATE OR REPLACE FUNCTION manage_route_data_size()
RETURNS TRIGGER AS $$
DECLARE
    current_size INTEGER;
    max_size INTEGER := 500;
    keep_recent INTEGER := 50;
    archive_points JSONB;
BEGIN
    -- Check current route_data size
    current_size := jsonb_array_length(NEW.route_data);
    
    -- If approaching limit, archive older points
    IF current_size > (max_size - 10) THEN
        
        -- Keep recent points + evenly distributed historical waypoints
        -- This implements the archival strategy from RouteDataService
        WITH indexed_points AS (
            SELECT 
                value,
                ordinality as idx
            FROM jsonb_array_elements(NEW.route_data) WITH ORDINALITY
        ),
        points_to_keep AS (
            -- Keep recent points
            SELECT value, idx FROM indexed_points WHERE idx > (current_size - keep_recent)
            UNION ALL
            -- Keep evenly distributed historical waypoints
            SELECT value, idx FROM indexed_points 
            WHERE idx <= (current_size - keep_recent)
            AND idx % GREATEST(1, CEIL((current_size - keep_recent)::numeric / (max_size - keep_recent))::integer) = 0
        )
        SELECT jsonb_agg(value ORDER BY idx) INTO archive_points
        FROM points_to_keep;
        
        -- Update with archived points
        NEW.route_data := COALESCE(archive_points, '[]'::jsonb);
        
        -- Log the archival
        INSERT INTO trip_audit_log (trip_id, action, details, created_at)
        VALUES (
            NEW.id,
            'ROUTE_ARCHIVED', 
            format('Route data archived: %s -> %s points', current_size, jsonb_array_length(NEW.route_data)),
            CURRENT_TIMESTAMP
        );
    END IF;
    
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Trigger for route data management
CREATE TRIGGER trg_manage_route_data
    BEFORE UPDATE ON trips
    FOR EACH ROW 
    WHEN (NEW.route_data IS DISTINCT FROM OLD.route_data)
    EXECUTE FUNCTION manage_route_data_size();

-- ═══════════════════════════════════════════════════════════════════════════
-- PERFORMANCE MONITORING FUNCTIONS
-- ═══════════════════════════════════════════════════════════════════════════

-- Function to get comprehensive trip performance metrics
CREATE OR REPLACE FUNCTION get_trip_performance_summary(
    start_date DATE DEFAULT NULL,
    end_date DATE DEFAULT NULL
)
RETURNS TABLE (
    metric_name TEXT,
    metric_value NUMERIC,
    metric_unit TEXT,
    metric_description TEXT
) AS $$
BEGIN
    -- Set default date range if not provided
    start_date := COALESCE(start_date, CURRENT_DATE - INTERVAL '30 days');
    end_date := COALESCE(end_date, CURRENT_DATE);
    
    RETURN QUERY
    WITH trip_stats AS (
        SELECT 
            t.*,
            EXTRACT(EPOCH FROM (t.end_time - t.start_time)) / 3600.0 as duration_hours,
            jsonb_array_length(t.route_data) as gps_points
        FROM trips t
        WHERE t.created_at::date BETWEEN start_date AND end_date
    )
    SELECT 
        'total_trips'::TEXT,
        COUNT(*)::NUMERIC,
        'count'::TEXT,
        'Total trips in period'::TEXT
    FROM trip_stats
    
    UNION ALL
    
    SELECT 
        'completed_trips'::TEXT,
        COUNT(*) FILTER (WHERE trip_status = 'completed')::NUMERIC,
        'count'::TEXT,
        'Completed trips in period'::TEXT
    FROM trip_stats
    
    UNION ALL
    
    SELECT 
        'completion_rate'::TEXT,
        ROUND(
            COUNT(*) FILTER (WHERE trip_status = 'completed') * 100.0 / NULLIF(COUNT(*), 0), 
            2
        )::NUMERIC,
        'percentage'::TEXT,
        'Trip completion rate'::TEXT
    FROM trip_stats
    
    UNION ALL
    
    SELECT 
        'avg_duration'::TEXT,
        ROUND(AVG(duration_hours) FILTER (WHERE duration_hours IS NOT NULL), 2)::NUMERIC,
        'hours'::TEXT,
        'Average trip duration'::TEXT
    FROM trip_stats
    
    UNION ALL
    
    SELECT 
        'avg_gps_points'::TEXT,
        ROUND(AVG(gps_points))::NUMERIC,
        'count'::TEXT,
        'Average GPS points per trip'::TEXT
    FROM trip_stats
    
    UNION ALL
    
    SELECT 
        'multi_stop_percentage'::TEXT,
        ROUND(
            COUNT(*) FILTER (WHERE trip_type = 'multi_stop') * 100.0 / NULLIF(COUNT(*), 0),
            2
        )::NUMERIC,
        'percentage'::TEXT,
        'Percentage of multi-stop trips'::TEXT
    FROM trip_stats;
END;
$$ LANGUAGE plpgsql;

-- Function to identify problematic trips requiring attention
CREATE OR REPLACE FUNCTION get_problematic_trips()
RETURNS TABLE (
    trip_id BIGINT,
    problem_type TEXT,
    problem_description TEXT,
    duration_hours NUMERIC,
    created_at TIMESTAMP
) AS $$
BEGIN
    RETURN QUERY
    WITH trip_analysis AS (
        SELECT 
            t.id,
            t.trip_status,
            t.start_time,
            t.created_at,
            t.total_stops,
            t.completed_stops,
            EXTRACT(EPOCH FROM (COALESCE(t.end_time, CURRENT_TIMESTAMP) - COALESCE(t.start_time, t.created_at))) / 3600.0 as hours_elapsed
        FROM trips t
        WHERE t.trip_status = 'ongoing'
    )
    -- Overdue trips (> 48 hours)
    SELECT 
        ta.id,
        'OVERDUE'::TEXT,
        format('Trip running for %.1f hours (threshold: 48h)', ta.hours_elapsed)::TEXT,
        ta.hours_elapsed,
        ta.created_at
    FROM trip_analysis ta
    WHERE ta.hours_elapsed > 48
    
    UNION ALL
    
    -- Stuck trips (> 72 hours) 
    SELECT 
        ta.id,
        'STUCK'::TEXT,
        format('Trip stuck for %.1f hours (threshold: 72h)', ta.hours_elapsed)::TEXT,
        ta.hours_elapsed,
        ta.created_at
    FROM trip_analysis ta
    WHERE ta.hours_elapsed > 72
    
    UNION ALL
    
    -- Multi-stop trips with no progress
    SELECT 
        ta.id,
        'NO_PROGRESS'::TEXT,
        format('Multi-stop trip with %d/%d stops completed, running %.1f hours', 
               ta.completed_stops, ta.total_stops, ta.hours_elapsed)::TEXT,
        ta.hours_elapsed,
        ta.created_at
    FROM trip_analysis ta
    WHERE ta.total_stops > 1 
    AND ta.completed_stops = 0 
    AND ta.hours_elapsed > 12
    
    ORDER BY hours_elapsed DESC;
END;
$$ LANGUAGE plpgsql;

-- ═══════════════════════════════════════════════════════════════════════════
-- MAINTENANCE AND CLEANUP FUNCTIONS
-- ═══════════════════════════════════════════════════════════════════════════

-- Function for daily maintenance tasks
CREATE OR REPLACE FUNCTION perform_daily_trip_maintenance()
RETURNS TEXT AS $$
DECLARE
    cleanup_count INTEGER;
    archive_count INTEGER;
    result_text TEXT := '';
BEGIN
    -- Clean up old audit log entries (older than 90 days)
    DELETE FROM trip_audit_log 
    WHERE created_at < CURRENT_TIMESTAMP - INTERVAL '90 days';
    
    GET DIAGNOSTICS cleanup_count = ROW_COUNT;
    result_text := result_text || format('Cleaned up %s old audit entries. ', cleanup_count);
    
    -- Update statistics
    ANALYZE trips;
    ANALYZE trip_stops;
    ANALYZE trip_audit_log;
    
    result_text := result_text || 'Updated table statistics. ';
    
    -- Log maintenance completion
    INSERT INTO trip_audit_log (trip_id, action, details, created_at)
    VALUES (NULL, 'MAINTENANCE', result_text, CURRENT_TIMESTAMP);
    
    RETURN result_text;
END;
$$ LANGUAGE plpgsql;

-- ═══════════════════════════════════════════════════════════════════════════
-- DOCUMENTATION AND COMMENTS
-- ═══════════════════════════════════════════════════════════════════════════

COMMENT ON FUNCTION update_trip_progress() IS 'Automatically updates trip progress counters when stop status changes';
COMMENT ON FUNCTION validate_stop_order_integrity() IS 'Ensures stop order consistency and prevents gaps/conflicts';
COMMENT ON FUNCTION auto_complete_trip_check() IS 'Auto-completes trips when all stops are finished';
COMMENT ON FUNCTION manage_route_data_size() IS 'Prevents unbounded route_data growth by archiving old GPS points';
COMMENT ON FUNCTION get_trip_performance_summary(DATE, DATE) IS 'Returns comprehensive performance metrics for trip analysis';
COMMENT ON FUNCTION get_problematic_trips() IS 'Identifies trips requiring attention (overdue, stuck, no progress)';
COMMENT ON FUNCTION perform_daily_trip_maintenance() IS 'Performs daily cleanup and maintenance tasks';

COMMENT ON TABLE trip_audit_log IS 'Audit trail for trip management actions and system events';
COMMENT ON TRIGGER trg_update_trip_progress ON trip_stops IS 'Maintains trip progress counters automatically';
COMMENT ON TRIGGER trg_validate_stop_order ON trip_stops IS 'Enforces stop order integrity constraints';
COMMENT ON TRIGGER trg_auto_complete_trip ON trip_stops IS 'Auto-completes trips when all stops finished';
COMMENT ON TRIGGER trg_manage_route_data ON trips IS 'Manages route_data size to prevent unbounded growth';
