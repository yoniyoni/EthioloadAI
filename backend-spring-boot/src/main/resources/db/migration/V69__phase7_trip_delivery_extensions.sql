-- Phase 7: Extend trip status for delivery workflow.
--
-- New trip_status values:
--   arrived                      — driver has reached the destination
--   delivery_pending_verification — proof submitted; awaiting shipper verification
--
-- Also adds arrived_at timestamp to trips for auditing.

-- Step 1: Drop the existing check constraint on trip_status
ALTER TABLE trips DROP CONSTRAINT IF EXISTS trips_trip_status_check;

-- Step 2: Add new check constraint with all valid statuses
ALTER TABLE trips
    ADD CONSTRAINT trips_trip_status_check
        CHECK (trip_status IN (
            'ongoing',
            'arrived',
            'delivery_pending_verification',
            'completed'
        ));

-- Step 3: Add arrived_at timestamp to trips
ALTER TABLE trips
    ADD COLUMN IF NOT EXISTS arrived_at TIMESTAMP;

-- Index for carrier dispatch queries by status
CREATE INDEX IF NOT EXISTS idx_trips_status_booking
    ON trips(trip_status, booking_id);
