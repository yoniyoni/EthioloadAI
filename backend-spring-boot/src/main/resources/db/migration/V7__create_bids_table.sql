-- V7: Create the bids table.
--
-- Status values (all lowercase — consistent with converter pattern):
--   pending   — submitted, awaiting shipper review
--   accepted  — shipper accepted this bid; booking created
--   rejected  — shipper rejected, or outcompeted when another bid was accepted
--   expired   — bid_deadline passed without action
--   countered — one party sent a counter-offer, awaiting the other party's response
--
-- Unique constraint: one bid per vehicle per cargo request (not per driver).
-- Rationale: a fleet owner may own many vehicles; each vehicle gets one shot.
-- This matches the final Laravel migration (bids_cargo_request_id_vehicle_id_unique).
--
-- counter_by tracks which party sent the current counter-offer.

CREATE TABLE bids (
    id                BIGSERIAL PRIMARY KEY,
    cargo_request_id  BIGINT       NOT NULL REFERENCES freight(id)   ON DELETE CASCADE,
    driver_id         BIGINT       NOT NULL REFERENCES users(id)     ON DELETE CASCADE,
    vehicle_id        BIGINT       NOT NULL REFERENCES vehicles(id)  ON DELETE CASCADE,
    amount            DECIMAL(15, 2) NOT NULL CHECK (amount > 0),
    note              TEXT,
    available_datetime TIMESTAMP,                     -- required for intracity bids
    status            VARCHAR(20)  NOT NULL DEFAULT 'pending'
        CHECK (status IN ('pending', 'accepted', 'rejected', 'expired', 'countered')),
    ai_score          DECIMAL(5, 4),                  -- 0.0000–1.0000 from AI engine
    is_recommended    BOOLEAN      NOT NULL DEFAULT FALSE,
    distance_km       DECIMAL(10, 2),                 -- Haversine vehicle→pickup distance
    -- Counter-offer fields
    counter_amount    DECIMAL(15, 2) CHECK (counter_amount > 0),
    counter_note      TEXT,
    counter_by        VARCHAR(10) CHECK (counter_by IN ('shipper', 'driver')),
    counter_at        TIMESTAMP,
    created_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- One bid per vehicle per cargo request
    CONSTRAINT bids_cargo_request_vehicle_unique UNIQUE (cargo_request_id, vehicle_id)
);

CREATE INDEX idx_bids_cargo_request_id ON bids (cargo_request_id);
CREATE INDEX idx_bids_driver_id        ON bids (driver_id);
CREATE INDEX idx_bids_vehicle_id       ON bids (vehicle_id);
CREATE INDEX idx_bids_status           ON bids (status);
-- Fast retrieval of pending/countered bids for a cargo request (used in bid ranking)
-- H2 compatible version (no WHERE clause)
CREATE INDEX idx_bids_cargo_status ON bids (cargo_request_id, status);
