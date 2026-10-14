-- V13: Create the backhaul_recommendations table.
--
-- Populated asynchronously when a trip starts (via a background job that calls
-- the FastAPI AI engine). The driver is shown these recommendations after
-- completing a delivery so they can pick up a return load.
--
-- status values:
--   pending    — generated, not yet shown to driver
--   viewed     — driver has seen the recommendation
--   bid_placed — driver submitted a bid on the recommended cargo
--   dismissed  — driver explicitly dismissed the recommendation
--
-- score: 0.000–1.000 (3 decimal places), higher = better match.
-- metadata: JSONB bag for AI engine details (direction_match, proximity_score, etc.).
--
-- Unique constraint: prevents duplicate recommendations for the same
--   (trip, driver, cargo_request) combination from re-runs of the job.

CREATE TABLE backhaul_recommendations (
    id               BIGSERIAL PRIMARY KEY,
    trip_id          BIGINT        NOT NULL REFERENCES trips(id)   ON DELETE CASCADE,
    driver_id        BIGINT        NOT NULL REFERENCES users(id)   ON DELETE CASCADE,
    cargo_request_id BIGINT        NOT NULL REFERENCES freight(id) ON DELETE CASCADE,
    score            DECIMAL(4, 3) NOT NULL DEFAULT 0
        CHECK (score >= 0 AND score <= 1),
    status           VARCHAR(20)   NOT NULL DEFAULT 'pending'
        CHECK (status IN ('pending', 'viewed', 'bid_placed', 'dismissed')),
    metadata         JSONB         NOT NULL DEFAULT '{}',
    created_at       TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT backhaul_unique UNIQUE (trip_id, driver_id, cargo_request_id)
);

CREATE INDEX idx_backhaul_trip_id          ON backhaul_recommendations (trip_id);
CREATE INDEX idx_backhaul_driver_id        ON backhaul_recommendations (driver_id);
CREATE INDEX idx_backhaul_cargo_request_id ON backhaul_recommendations (cargo_request_id);
CREATE INDEX idx_backhaul_status           ON backhaul_recommendations (status);
