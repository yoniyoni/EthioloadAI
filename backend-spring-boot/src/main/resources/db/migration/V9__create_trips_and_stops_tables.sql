-- V9: Create the trips and trip_stops tables.
--
-- TRIPS
-- Each booking has at most one trip (enforced by unique constraint on booking_id).
-- route_data stores GPS breadcrumbs as JSONB. This grows with every GPS ping.
-- The application layer must cap the stored breadcrumb count (e.g. last 500 points)
-- to prevent unbounded growth. See audit note in production audit report.
--
-- trip_status: ongoing | completed
-- trip_type:   single  | multi_stop
-- total_stops / completed_stops: counters for multi-stop progress tracking.
--
-- TRIP_STOPS
-- Each stop belongs to a trip and optionally references a freight/cargo request.
-- pickup_lat uses DECIMAL(10,8) and pickup_lng uses DECIMAL(11,8) for full
-- GPS coordinate precision (±90/±180 degrees with 8 decimal places ≈ 1mm precision).
-- agreed_price is the per-stop price contributing to the trip's total_amount.
--
-- stop_status: pending | arrived | loaded | completed

CREATE TABLE trips (
    id               BIGSERIAL PRIMARY KEY,
    booking_id       BIGINT       NOT NULL REFERENCES bookings(id) ON DELETE CASCADE,
    start_location   VARCHAR(255) NOT NULL,
    destination      VARCHAR(255) NOT NULL,
    -- JSONB for GPS breadcrumbs. Application must cap at ~500 points per trip.
    -- Format: [{"lat": 9.03, "lng": 38.74, "ts": "2026-01-01T10:00:00Z"}, ...]
    route_data       JSONB        NOT NULL DEFAULT '[]',
    trip_status      VARCHAR(20)  NOT NULL DEFAULT 'ongoing'
        CHECK (trip_status IN ('ongoing', 'completed')),
    trip_type        VARCHAR(20)  NOT NULL DEFAULT 'single'
        CHECK (trip_type IN ('single', 'multi_stop')),
    total_stops      INTEGER      NOT NULL DEFAULT 1 CHECK (total_stops >= 1),
    completed_stops  INTEGER      NOT NULL DEFAULT 0 CHECK (completed_stops >= 0),
    start_time       TIMESTAMP,
    end_time         TIMESTAMP,
    created_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- One trip per booking — enforced at the DB level
    CONSTRAINT trips_booking_id_unique UNIQUE (booking_id)
);

CREATE INDEX idx_trips_booking_id   ON trips (booking_id);
CREATE INDEX idx_trips_trip_status  ON trips (trip_status);

-- TRIP STOPS ------------------------------------------------------------------

CREATE TABLE trip_stops (
    id               BIGSERIAL PRIMARY KEY,
    trip_id          BIGINT       NOT NULL  REFERENCES trips(id)          ON DELETE CASCADE,
    -- Nullable: stops can exist without a formal cargo request (e.g. fuel/rest stops)
    cargo_request_id BIGINT                 REFERENCES freight(id)        ON DELETE SET NULL,
    stop_order       INTEGER      NOT NULL  CHECK (stop_order >= 1),
    location_name    VARCHAR(255) NOT NULL,
    -- Full 8-decimal GPS precision for stop coordinates
    pickup_lat       DECIMAL(10, 8),
    pickup_lng       DECIMAL(11, 8),
    agreed_price     DECIMAL(15, 2) NOT NULL DEFAULT 0 CHECK (agreed_price >= 0),
    stop_status      VARCHAR(20)  NOT NULL  DEFAULT 'pending'
        CHECK (stop_status IN ('pending', 'arrived', 'loaded', 'completed')),
    notes            TEXT,
    arrived_at       TIMESTAMP,
    completed_at     TIMESTAMP,
    created_at       TIMESTAMP    NOT NULL  DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMP    NOT NULL  DEFAULT CURRENT_TIMESTAMP,

    -- Stop order must be unique within a trip
    CONSTRAINT trip_stops_trip_order_unique UNIQUE (trip_id, stop_order)
);

CREATE INDEX idx_trip_stops_trip_id          ON trip_stops (trip_id);
CREATE INDEX idx_trip_stops_cargo_request_id ON trip_stops (cargo_request_id);
