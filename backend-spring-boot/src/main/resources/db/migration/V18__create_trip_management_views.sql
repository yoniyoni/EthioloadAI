-- V18: Trip Management Database Views
--
-- This migration creates useful database views to optimize common queries
-- and provide convenient access to trip management data with proper joins.
--
-- FEATURES:
-- - Materialized views for expensive analytics queries
-- - Convenience views for common data access patterns  
-- - Performance-optimized views for monitoring and reporting

-- ═══════════════════════════════════════════════════════════════════════════
-- TRIP OVERVIEW VIEWS
-- ═══════════════════════════════════════════════════════════════════════════

-- Comprehensive trip view with all related data
CREATE VIEW v_trip_details AS
SELECT 
    t.id as trip_id,
    t.start_location,
    t.destination,
    t.trip_status,
    t.trip_type,
    t.total_stops,
    t.completed_stops,
    t.start_time,
    t.end_time,
    t.created_at as trip_created_at,
    
    -- Booking information
    b.id as booking_id,
    b.booking_status,
    b.estimated_price,
    b.commission_fee,
    
    -- Driver information
    d.id as driver_id,
    d.full_name as driver_name,
    d.phone as driver_phone,
    
    -- Vehicle information
    v.id as vehicle_id,
    v.plate_number,
    v.truck_type,
    v.capacity as vehicle_capacity,
    
    -- Shipper information
    s.id as shipper_id,
    s.full_name as shipper_name,
    s.phone as shipper_phone,
    
    -- Cargo information
    f.id as freight_id,
    f.material_type,
    f.weight_tons,
    f.volume_m3,
    f.urgency_level,
    
    -- Calculated fields
    jsonb_array_length(t.route_data) as gps_point_count,
    CASE 
        WHEN t.start_time IS NOT NULL AND t.end_time IS NOT NULL THEN
            EXTRACT(EPOCH FROM (t.end_time - t.start_time)) / 3600.0
        WHEN t.start_time IS NOT NULL THEN
            EXTRACT(EPOCH FROM (CURRENT_TIMESTAMP - t.start_time)) / 3600.0
        ELSE NULL
    END as duration_hours,
    
    CASE 
        WHEN t.total_stops > 0 THEN
            ROUND(t.completed_stops * 100.0 / t.total_stops, 2)
        ELSE 0
    END as completion_percentage

FROM trips t
    JOIN bookings b ON t.booking_id = b.id
    JOIN users d ON b.driver_id = d.id  
    JOIN vehicles v ON b.vehicle_id = v.id
    JOIN freight f ON b.cargo_id = f.id
    JOIN users s ON f.shipper_id = s.id;

-- Index on the view's most commonly filtered columns
CREATE INDEX IF NOT EXISTS idx_v_trip_details_driver_status 
ON trips (booking_id) 
INCLUDE (trip_status, start_time);

COMMENT ON VIEW v_trip_details IS 'Comprehensive trip view with all related booking, driver, vehicle, and cargo information';

-- ═══════════════════════════════════════════════════════════════════════════
-- TRIP STOP VIEWS
-- ═══════════════════════════════════════════════════════════════════════════

-- Detailed trip stop view with trip context
CREATE VIEW v_trip_stop_details AS
SELECT 
    ts.id as stop_id,
    ts.trip_id,
    ts.stop_order,
    ts.location_name,
    ts.pickup_lat,
    ts.pickup_lng,
    ts.agreed_price,
    ts.stop_status,
    ts.notes,
    ts.arrived_at,
    ts.completed_at,
    ts.created_at as stop_created_at,
    
    -- Trip context
    t.trip_status,
    t.trip_type,
    t.start_location as trip_start_location,
    t.destination as trip_destination,
    
    -- Cargo information (if linked)
    f.id as cargo_request_id,
    f.material_type,
    f.weight_tons,
    s.full_name as shipper_name,
    
    -- Driver information
    d.id as driver_id,
    d.full_name as driver_name,
    
    -- Calculated fields
    CASE 
        WHEN ts.arrived_at IS NOT NULL AND ts.completed_at IS NOT NULL THEN
            EXTRACT(EPOCH FROM (ts.completed_at - ts.arrived_at)) / 60.0
        ELSE NULL
    END as processing_time_minutes,
    
    CASE 
        WHEN ts.arrived_at IS NOT NULL THEN
            EXTRACT(EPOCH FROM (COALESCE(ts.completed_at, CURRENT_TIMESTAMP) - ts.arrived_at)) / 60.0  
        ELSE NULL
    END as time_at_stop_minutes

FROM trip_stops ts
    JOIN trips t ON ts.trip_id = t.id
    JOIN bookings b ON t.booking_id = b.id
    JOIN users d ON b.driver_id = d.id
    LEFT JOIN freight f ON ts.cargo_request_id = f.id
    LEFT JOIN users s ON f.shipper_id = s.id;

COMMENT ON VIEW v_trip_stop_details IS 'Detailed trip stop view with trip context and processing time calculations';

-- ═══════════════════════════════════════════════════════════════════════════
-- ANALYTICS MATERIALIZED VIEWS (Updated Periodically)
-- ═══════════════════════════════════════════════════════════════════════════

-- Trip performance analytics (refreshed hourly)
CREATE MATERIALIZED VIEW mv_trip_performance_daily AS
SELECT 
    DATE(t.created_at) as date,
    COUNT(*) as total_trips,
    COUNT(*) FILTER (WHERE t.trip_status = 'completed') as completed_trips,
    COUNT(*) FILTER (WHERE t.trip_status = 'ongoing') as ongoing_trips,
    
    -- Completion rate
    ROUND(
        COUNT(*) FILTER (WHERE t.trip_status = 'completed') * 100.0 / NULLIF(COUNT(*), 0), 
        2
    ) as completion_rate_percentage,
    
    -- Duration statistics (for completed trips)
    ROUND(
        AVG(EXTRACT(EPOCH FROM (t.end_time - t.start_time)) / 3600.0) 
        FILTER (WHERE t.trip_status = 'completed' AND t.start_time IS NOT NULL AND t.end_time IS NOT NULL),
        2
    ) as avg_duration_hours,
    
    -- Trip types
    COUNT(*) FILTER (WHERE t.trip_type = 'single') as single_stop_trips,
    COUNT(*) FILTER (WHERE t.trip_type = 'multi_stop') as multi_stop_trips,
    
    -- Route data statistics
    ROUND(AVG(jsonb_array_length(t.route_data)), 0) as avg_gps_points,
    MAX(jsonb_array_length(t.route_data)) as max_gps_points,
    
    -- Revenue (sum of stop prices for completed trips)
    COALESCE(
        SUM(
            (SELECT SUM(ts.agreed_price) 
             FROM trip_stops ts 
             WHERE ts.trip_id = t.id)
        ) FILTER (WHERE t.trip_status = 'completed'), 
        0
    ) as total_revenue

FROM trips t
GROUP BY DATE(t.created_at)
ORDER BY date DESC;

-- Create unique index on date for efficient lookups
CREATE UNIQUE INDEX idx_mv_trip_performance_daily_date 
ON mv_trip_performance_daily (date);

COMMENT ON MATERIALIZED VIEW mv_trip_performance_daily IS 'Daily trip performance analytics - refreshed hourly';

-- Driver performance analytics
CREATE MATERIALIZED VIEW mv_driver_performance AS
SELECT 
    d.id as driver_id,
    d.full_name as driver_name,
    COUNT(t.id) as total_trips,
    COUNT(t.id) FILTER (WHERE t.trip_status = 'completed') as completed_trips,
    COUNT(t.id) FILTER (WHERE t.trip_status = 'ongoing') as ongoing_trips,
    
    -- Performance metrics
    ROUND(
        COUNT(t.id) FILTER (WHERE t.trip_status = 'completed') * 100.0 / NULLIF(COUNT(t.id), 0),
        2
    ) as completion_rate_percentage,
    
    -- Average duration for completed trips
    ROUND(
        AVG(EXTRACT(EPOCH FROM (t.end_time - t.start_time)) / 3600.0)
        FILTER (WHERE t.trip_status = 'completed' AND t.start_time IS NOT NULL AND t.end_time IS NOT NULL),
        2
    ) as avg_trip_duration_hours,
    
    -- Revenue generated
    COALESCE(
        SUM(
            (SELECT SUM(ts.agreed_price) 
             FROM trip_stops ts 
             WHERE ts.trip_id = t.id)
        ) FILTER (WHERE t.trip_status = 'completed'),
        0
    ) as total_revenue_generated,
    
    -- Recent activity
    MAX(t.created_at) as last_trip_date,
    COUNT(t.id) FILTER (WHERE t.created_at >= CURRENT_DATE - INTERVAL '30 days') as trips_last_30_days

FROM users d
    LEFT JOIN bookings b ON d.id = b.driver_id  
    LEFT JOIN trips t ON b.id = t.booking_id
WHERE d.role = 'DRIVER'
GROUP BY d.id, d.full_name
HAVING COUNT(t.id) > 0  -- Only drivers with trips
ORDER BY total_revenue_generated DESC;

CREATE UNIQUE INDEX idx_mv_driver_performance_driver_id 
ON mv_driver_performance (driver_id);

COMMENT ON MATERIALIZED VIEW mv_driver_performance IS 'Driver performance analytics - refreshed daily';

-- ═══════════════════════════════════════════════════════════════════════════
-- MONITORING AND HEALTH VIEWS
-- ═══════════════════════════════════════════════════════════════════════════

-- Current system health view
CREATE VIEW v_system_health AS
SELECT 
    -- Current counts
    (SELECT COUNT(*) FROM trips WHERE trip_status = 'ongoing') as ongoing_trips,
    (SELECT COUNT(*) FROM trips WHERE trip_status = 'completed') as completed_trips_total,
    (SELECT COUNT(*) FROM trip_stops WHERE stop_status != 'completed') as incomplete_stops,
    
    -- Problem detection
    (SELECT COUNT(*) 
     FROM trips 
     WHERE trip_status = 'ongoing' 
     AND start_time IS NOT NULL 
     AND EXTRACT(EPOCH FROM (CURRENT_TIMESTAMP - start_time)) / 3600.0 > 48
    ) as overdue_trips,
    
    (SELECT COUNT(*) 
     FROM trips 
     WHERE trip_status = 'ongoing' 
     AND start_time IS NOT NULL 
     AND EXTRACT(EPOCH FROM (CURRENT_TIMESTAMP - start_time)) / 3600.0 > 72
    ) as stuck_trips,
    
    -- Performance indicators  
    (SELECT ROUND(AVG(jsonb_array_length(route_data)), 0)
     FROM trips 
     WHERE created_at >= CURRENT_DATE - INTERVAL '24 hours'
    ) as avg_gps_points_24h,
    
    -- Today's activity
    (SELECT COUNT(*) 
     FROM trips 
     WHERE created_at::date = CURRENT_DATE
    ) as trips_today,
    
    (SELECT COUNT(*) 
     FROM trips 
     WHERE trip_status = 'completed' 
     AND end_time::date = CURRENT_DATE
    ) as completed_today;

COMMENT ON VIEW v_system_health IS 'Real-time system health metrics for monitoring dashboards';

-- Recent activity view for dashboards
CREATE VIEW v_recent_activity AS
SELECT 
    'trip_created' as activity_type,
    t.id as entity_id,
    format('Trip #%s created: %s → %s', t.id, t.start_location, t.destination) as description,
    d.full_name as user_name,
    t.created_at as activity_time
FROM trips t
    JOIN bookings b ON t.booking_id = b.id
    JOIN users d ON b.driver_id = d.id
WHERE t.created_at >= CURRENT_TIMESTAMP - INTERVAL '24 hours'

UNION ALL

SELECT 
    'trip_completed' as activity_type,
    t.id as entity_id,
    format('Trip #%s completed: %s → %s', t.id, t.start_location, t.destination) as description,
    d.full_name as user_name,
    t.end_time as activity_time
FROM trips t
    JOIN bookings b ON t.booking_id = b.id
    JOIN users d ON b.driver_id = d.id
WHERE t.trip_status = 'completed' 
AND t.end_time >= CURRENT_TIMESTAMP - INTERVAL '24 hours'

UNION ALL

SELECT 
    'stop_completed' as activity_type,
    ts.id as entity_id,
    format('Stop #%s completed at %s (Trip #%s)', ts.id, ts.location_name, ts.trip_id) as description,
    d.full_name as user_name,
    ts.completed_at as activity_time
FROM trip_stops ts
    JOIN trips t ON ts.trip_id = t.id
    JOIN bookings b ON t.booking_id = b.id
    JOIN users d ON b.driver_id = d.id
WHERE ts.completed_at >= CURRENT_TIMESTAMP - INTERVAL '24 hours'

ORDER BY activity_time DESC;

COMMENT ON VIEW v_recent_activity IS 'Recent trip management activities for dashboard feeds';

-- ═══════════════════════════════════════════════════════════════════════════
-- REFRESH FUNCTIONS FOR MATERIALIZED VIEWS
-- ═══════════════════════════════════════════════════════════════════════════

-- Function to refresh all trip analytics materialized views
CREATE OR REPLACE FUNCTION refresh_trip_analytics()
RETURNS TEXT AS $$
DECLARE
    start_time TIMESTAMP := CURRENT_TIMESTAMP;
    result_text TEXT := '';
BEGIN
    -- Refresh daily performance view
    REFRESH MATERIALIZED VIEW mv_trip_performance_daily;
    result_text := result_text || 'Refreshed mv_trip_performance_daily. ';
    
    -- Refresh driver performance view  
    REFRESH MATERIALIZED VIEW mv_driver_performance;
    result_text := result_text || 'Refreshed mv_driver_performance. ';
    
    -- Log the refresh
    INSERT INTO trip_audit_log (trip_id, action, details, created_at)
    VALUES (
        NULL, 
        'ANALYTICS_REFRESH', 
        format('%s (Duration: %s seconds)', 
               result_text, 
               EXTRACT(EPOCH FROM (CURRENT_TIMESTAMP - start_time))
        ),
        CURRENT_TIMESTAMP
    );
    
    RETURN result_text;
END;
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION refresh_trip_analytics() IS 'Refreshes all trip analytics materialized views';

-- ═══════════════════════════════════════════════════════════════════════════
-- VIEW PERMISSIONS AND SECURITY
-- ═══════════════════════════════════════════════════════════════════════════

-- Note: In production, these views should have appropriate access controls
-- The application layer will handle role-based access through the service layer

COMMENT ON VIEW v_trip_details IS 'Contains sensitive data - access controlled through application layer';
COMMENT ON VIEW v_trip_stop_details IS 'Contains sensitive data - access controlled through application layer';
COMMENT ON VIEW v_system_health IS 'System metrics - admin access only';
COMMENT ON VIEW v_recent_activity IS 'Activity feed - filtered by user role in application';
