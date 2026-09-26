-- Schema Validation Script for Trip Management
-- 
-- This script validates that all required database objects exist
-- and are properly configured for the trip management system.

-- ═══════════════════════════════════════════════════════════════════════════
-- TABLE VALIDATION
-- ═══════════════════════════════════════════════════════════════════════════

-- Validate trips table structure
DO $$
DECLARE 
    missing_columns TEXT[] := ARRAY[]::TEXT[];
    required_columns TEXT[] := ARRAY[
        'id', 'booking_id', 'start_location', 'destination', 'route_data',
        'trip_status', 'trip_type', 'total_stops', 'completed_stops',
        'start_time', 'end_time', 'created_at', 'updated_at'
    ];
    col TEXT;
BEGIN
    FOREACH col IN ARRAY required_columns
    LOOP
        IF NOT EXISTS (
            SELECT 1 FROM information_schema.columns 
            WHERE table_name = 'trips' AND column_name = col
        ) THEN
            missing_columns := array_append(missing_columns, col);
        END IF;
    END LOOP;
    
    IF array_length(missing_columns, 1) > 0 THEN
        RAISE EXCEPTION 'Missing columns in trips table: %', array_to_string(missing_columns, ', ');
    END IF;
    
    RAISE NOTICE 'trips table structure: VALID';
END;
$$;

-- Validate trip_stops table structure
DO $$
DECLARE 
    missing_columns TEXT[] := ARRAY[]::TEXT[];
    required_columns TEXT[] := ARRAY[
        'id', 'trip_id', 'cargo_request_id', 'stop_order', 'location_name',
        'pickup_lat', 'pickup_lng', 'agreed_price', 'stop_status', 'notes',
        'arrived_at', 'completed_at', 'created_at', 'updated_at'
    ];
    col TEXT;
BEGIN
    FOREACH col IN ARRAY required_columns
    LOOP
        IF NOT EXISTS (
            SELECT 1 FROM information_schema.columns 
            WHERE table_name = 'trip_stops' AND column_name = col
        ) THEN
            missing_columns := array_append(missing_columns, col);
        END IF;
    END LOOP;
    
    IF array_length(missing_columns, 1) > 0 THEN
        RAISE EXCEPTION 'Missing columns in trip_stops table: %', array_to_string(missing_columns, ', ');
    END IF;
    
    RAISE NOTICE 'trip_stops table structure: VALID';
END;
$$;

-- ═══════════════════════════════════════════════════════════════════════════
-- CONSTRAINT VALIDATION
-- ═══════════════════════════════════════════════════════════════════════════

-- Check unique constraints
DO $$
BEGIN
    -- trips.booking_id unique constraint
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints tc
        JOIN information_schema.key_column_usage kcu USING (constraint_name)
        WHERE tc.table_name = 'trips' 
        AND tc.constraint_type = 'UNIQUE'
        AND kcu.column_name = 'booking_id'
    ) THEN
        RAISE EXCEPTION 'Missing unique constraint on trips.booking_id';
    END IF;
    
    -- trip_stops (trip_id, stop_order) unique constraint
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints tc
        WHERE tc.table_name = 'trip_stops' 
        AND tc.constraint_type = 'UNIQUE'
        AND tc.constraint_name LIKE '%trip_order%'
    ) THEN
        RAISE EXCEPTION 'Missing unique constraint on trip_stops (trip_id, stop_order)';
    END IF;
    
    RAISE NOTICE 'Unique constraints: VALID';
END;
$$;

-- Check foreign key constraints
DO $$
BEGIN
    -- trips.booking_id → bookings.id
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints tc
        JOIN information_schema.key_column_usage kcu USING (constraint_name)
        WHERE tc.table_name = 'trips' 
        AND tc.constraint_type = 'FOREIGN KEY'
        AND kcu.column_name = 'booking_id'
    ) THEN
        RAISE EXCEPTION 'Missing foreign key constraint: trips.booking_id → bookings.id';
    END IF;
    
    -- trip_stops.trip_id → trips.id  
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints tc
        JOIN information_schema.key_column_usage kcu USING (constraint_name)
        WHERE tc.table_name = 'trip_stops' 
        AND tc.constraint_type = 'FOREIGN KEY'
        AND kcu.column_name = 'trip_id'
    ) THEN
        RAISE EXCEPTION 'Missing foreign key constraint: trip_stops.trip_id → trips.id';
    END IF;
    
    RAISE NOTICE 'Foreign key constraints: VALID';
END;
$$;

-- ═══════════════════════════════════════════════════════════════════════════
-- INDEX VALIDATION
-- ═══════════════════════════════════════════════════════════════════════════

-- Check critical indexes exist
DO $$
DECLARE
    critical_indexes TEXT[] := ARRAY[
        'idx_trips_booking_id',          -- V9 original
        'idx_trips_trip_status',         -- V9 original
        'idx_trip_stops_trip_id',        -- V9 original
        'idx_trips_start_time_desc',     -- V16 performance
        'idx_trips_route_data_gin',      -- V16 JSONB
        'idx_trip_stops_status_order'    -- V16 performance
    ];
    idx_name TEXT;
BEGIN
    FOREACH idx_name IN ARRAY critical_indexes
    LOOP
        IF NOT EXISTS (
            SELECT 1 FROM pg_indexes 
            WHERE indexname = idx_name
        ) THEN
            RAISE WARNING 'Missing critical index: %', idx_name;
        END IF;
    END LOOP;
    
    RAISE NOTICE 'Critical indexes: CHECKED (see warnings for missing indexes)';
END;
$$;

-- ═══════════════════════════════════════════════════════════════════════════
-- FUNCTION AND TRIGGER VALIDATION
-- ═══════════════════════════════════════════════════════════════════════════

-- Check critical functions exist
DO $$
DECLARE
    required_functions TEXT[] := ARRAY[
        'update_trip_progress',
        'validate_stop_order_integrity', 
        'auto_complete_trip_check',
        'manage_route_data_size',
        'get_trip_performance_summary',
        'refresh_trip_analytics'
    ];
    func_name TEXT;
BEGIN
    FOREACH func_name IN ARRAY required_functions
    LOOP
        IF NOT EXISTS (
            SELECT 1 FROM information_schema.routines
            WHERE routine_name = func_name
            AND routine_type = 'FUNCTION'
        ) THEN
            RAISE WARNING 'Missing function: %', func_name;
        END IF;
    END LOOP;
    
    RAISE NOTICE 'Required functions: CHECKED (see warnings for missing functions)';
END;
$$;

-- Check critical triggers exist
DO $$
DECLARE
    required_triggers TEXT[] := ARRAY[
        'trg_update_trip_progress',
        'trg_validate_stop_order',
        'trg_auto_complete_trip',
        'trg_manage_route_data'
    ];
    trig_name TEXT;
BEGIN
    FOREACH trig_name IN ARRAY required_triggers
    LOOP
        IF NOT EXISTS (
            SELECT 1 FROM information_schema.triggers
            WHERE trigger_name = trig_name
        ) THEN
            RAISE WARNING 'Missing trigger: %', trig_name;
        END IF;
    END LOOP;
    
    RAISE NOTICE 'Required triggers: CHECKED (see warnings for missing triggers)';
END;
$$;

-- ═══════════════════════════════════════════════════════════════════════════
-- VIEW VALIDATION
-- ═══════════════════════════════════════════════════════════════════════════

-- Check views exist
DO $$
DECLARE
    required_views TEXT[] := ARRAY[
        'v_trip_details',
        'v_trip_stop_details', 
        'v_system_health',
        'v_recent_activity'
    ];
    view_name TEXT;
BEGIN
    FOREACH view_name IN ARRAY required_views
    LOOP
        IF NOT EXISTS (
            SELECT 1 FROM information_schema.views
            WHERE table_name = view_name
        ) THEN
            RAISE WARNING 'Missing view: %', view_name;
        END IF;
    END LOOP;
    
    RAISE NOTICE 'Required views: CHECKED (see warnings for missing views)';
END;
$$;

-- Check materialized views exist (PostgreSQL only)
DO $$
DECLARE
    required_mv TEXT[] := ARRAY[
        'mv_trip_performance_daily',
        'mv_driver_performance'
    ];
    mv_name TEXT;
BEGIN
    -- Only check if we're on PostgreSQL (not H2)
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'pg_matviews') THEN
        FOREACH mv_name IN ARRAY required_mv
        LOOP
            IF NOT EXISTS (
                SELECT 1 FROM pg_matviews
                WHERE matviewname = mv_name
            ) THEN
                RAISE WARNING 'Missing materialized view: %', mv_name;
            END IF;
        END LOOP;
        RAISE NOTICE 'Materialized views: CHECKED (PostgreSQL)';
    ELSE
        RAISE NOTICE 'Materialized views: SKIPPED (H2 database)';
    END IF;
END;
$$;

-- ═══════════════════════════════════════════════════════════════════════════
-- DATA TYPE VALIDATION
-- ═══════════════════════════════════════════════════════════════════════════

-- Check JSONB support for route_data
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'trips' 
        AND column_name = 'route_data'
        AND data_type IN ('jsonb', 'json', 'clob', 'text') -- H2 uses CLOB for JSON
    ) THEN
        RAISE NOTICE 'route_data column type: VALID';
    ELSE
        RAISE EXCEPTION 'Invalid or missing route_data column type';
    END IF;
END;
$$;

-- ═══════════════════════════════════════════════════════════════════════════
-- SUMMARY
-- ═══════════════════════════════════════════════════════════════════════════

SELECT 'Trip Management Database Schema Validation COMPLETED' as validation_result;