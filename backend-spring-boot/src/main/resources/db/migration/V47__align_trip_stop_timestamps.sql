-- V47: Align upgraded trip-stop schemas with the TripStop entity.
ALTER TABLE trip_stops
    ADD COLUMN IF NOT EXISTS arrived_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS completed_at TIMESTAMP;
