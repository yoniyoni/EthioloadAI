-- V8: Create the bookings table.
--
-- A booking is created atomically when a bid is accepted.
-- cargo_id FK uses the freight table (Spring Boot calls the table 'freight',
-- Laravel called it 'cargo_requests' — same data, different name).
--
-- Status values:
--   pending   — created but not yet confirmed
--   accepted  — booking confirmed, driver assigned
--   delivered — cargo physically delivered, awaiting payment
--   completed — payment confirmed, booking closed
--   confirmed — alias used in some workflows (kept for parity with Laravel)
--   cancelled — booking cancelled before trip start
--
-- bid_id is nullable: direct bookings (fleet dispatch, fixed-price direct accept)
-- may not go through the bidding flow.
--
-- commission_fee: platform's 10% cut (5% for fleet-owner bookings).
-- estimated_price: the agreed price from the accepted bid.
-- Both are server-calculated — never trusted from client input.

CREATE TABLE bookings (
    id               BIGSERIAL PRIMARY KEY,
    cargo_id         BIGINT       NOT NULL REFERENCES freight(id)   ON DELETE CASCADE,
    vehicle_id       BIGINT       NOT NULL REFERENCES vehicles(id)  ON DELETE CASCADE,
    driver_id        BIGINT       NOT NULL REFERENCES users(id)     ON DELETE CASCADE,
    bid_id           BIGINT                REFERENCES bids(id)      ON DELETE SET NULL,
    booking_status   VARCHAR(20)  NOT NULL DEFAULT 'pending'
        CHECK (booking_status IN ('pending', 'accepted', 'delivered', 'completed', 'confirmed', 'cancelled')),
    estimated_price  DECIMAL(15, 2),
    commission_fee   DECIMAL(15, 2) NOT NULL DEFAULT 0 CHECK (commission_fee >= 0),
    created_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_bookings_cargo_id    ON bookings (cargo_id);
CREATE INDEX idx_bookings_driver_id   ON bookings (driver_id);
CREATE INDEX idx_bookings_vehicle_id  ON bookings (vehicle_id);
CREATE INDEX idx_bookings_bid_id      ON bookings (bid_id);
CREATE INDEX idx_bookings_status      ON bookings (booking_status);
