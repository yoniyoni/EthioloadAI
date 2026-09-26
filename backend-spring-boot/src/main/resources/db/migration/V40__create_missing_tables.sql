-- V23: Create tables that are referenced by JPA entities but absent from the DB.
-- These were defined in V23-V39 of the old migration history which no longer
-- has corresponding SQL files in the source tree. This single script recreates
-- them from the entity definitions so Hibernate schema validation passes.

-- ── 1. ratings ────────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS ratings (
    id           BIGSERIAL PRIMARY KEY,
    booking_id   BIGINT        NOT NULL REFERENCES bookings(id) ON DELETE CASCADE,
    shipper_id   BIGINT        NOT NULL REFERENCES users(id),
    driver_id    BIGINT        NOT NULL REFERENCES users(id),
    rater_id     BIGINT        REFERENCES users(id),
    rating       INTEGER       NOT NULL CHECK (rating BETWEEN 1 AND 5),
    feedback     TEXT,
    created_at   TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_ratings_booking_rater UNIQUE (booking_id, rater_id)
);

-- ── 2. ai_recommendations ─────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS ai_recommendations (
    id                    BIGSERIAL PRIMARY KEY,
    cargo_request_id      BIGINT          NOT NULL REFERENCES freight(id) ON DELETE CASCADE,
    vehicle_id            BIGINT          NOT NULL REFERENCES vehicles(id) ON DELETE CASCADE,
    recommendation_score  DECIMAL(5, 4)   NOT NULL DEFAULT 0,
    predicted_price       DECIMAL(15, 2)  NOT NULL DEFAULT 0,
    backhaul_probability  DECIMAL(5, 4)   NOT NULL DEFAULT 0,
    created_at            TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_ai_rec_cargo
    ON ai_recommendations (cargo_request_id);

-- ── 3. platform_settings ──────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS platform_settings (
    id          BIGSERIAL    PRIMARY KEY,
    setting_key VARCHAR(255) NOT NULL UNIQUE,
    value       TEXT,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- Seed default pricing settings used by AI price prediction fallback
INSERT INTO platform_settings (setting_key, value)
VALUES
    ('pricing.rate_min',              '18'),
    ('pricing.rate_max',              '28'),
    ('pricing.intracity_rate_min',    '50'),
    ('pricing.intracity_rate_max',    '100'),
    ('pricing.commission_rate',       '0.10'),
    ('pricing.fleet_commission_rate', '0.05')
ON CONFLICT (setting_key) DO NOTHING;

-- ── 4. messages ───────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS messages (
    id                  BIGSERIAL    PRIMARY KEY,
    freight_id          BIGINT        NOT NULL REFERENCES freight(id) ON DELETE CASCADE,
    sender_id           BIGINT        NOT NULL REFERENCES users(id),
    receiver_id         BIGINT        NOT NULL REFERENCES users(id),
    content             VARCHAR(2000) NOT NULL,
    masked_content      VARCHAR(2000),
    type                VARCHAR(20)   NOT NULL DEFAULT 'TEXT',
    has_phone_number    BOOLEAN       NOT NULL DEFAULT FALSE,
    has_payment_request BOOLEAN       NOT NULL DEFAULT FALSE,
    is_read             BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_messages_freight ON messages (freight_id);
CREATE INDEX IF NOT EXISTS idx_messages_receiver ON messages (receiver_id);

-- ── 5. current_vehicle_locations ─────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS current_vehicle_locations (
    vehicle_id      BIGINT          PRIMARY KEY REFERENCES vehicles(id) ON DELETE CASCADE,
    trip_id         BIGINT          REFERENCES trips(id) ON DELETE SET NULL,
    latitude        DECIMAL(10, 8)  NOT NULL,
    longitude       DECIMAL(11, 8)  NOT NULL,
    altitude        DECIMAL(8,  2),
    accuracy        DECIMAL(6,  2),
    speed           DECIMAL(5,  2),
    heading         DECIMAL(5,  2),
    timestamp       TIMESTAMPTZ     NOT NULL,
    battery_level   INTEGER,
    signal_strength INTEGER,
    metadata        JSONB,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_current_locations_trip
    ON current_vehicle_locations (trip_id);
CREATE INDEX IF NOT EXISTS idx_current_locations_timestamp
    ON current_vehicle_locations (timestamp);

-- ── 6. vehicle_location_history ──────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS vehicle_location_history (
    id              BIGSERIAL       PRIMARY KEY,
    vehicle_id      BIGINT          NOT NULL,
    trip_id         BIGINT,
    latitude        DECIMAL(10, 8)  NOT NULL,
    longitude       DECIMAL(11, 8)  NOT NULL,
    altitude        DECIMAL(8,  2),
    accuracy        DECIMAL(6,  2),
    speed           DECIMAL(5,  2),
    heading         DECIMAL(5,  2),
    timestamp       TIMESTAMPTZ     NOT NULL,
    battery_level   INTEGER,
    signal_strength INTEGER,
    metadata        JSONB,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_location_history_vehicle_timestamp
    ON vehicle_location_history (vehicle_id, timestamp DESC);
CREATE INDEX IF NOT EXISTS idx_location_history_trip_timestamp
    ON vehicle_location_history (trip_id, timestamp);
