-- V11: Create the ratings table.
--
-- Design: both the shipper and the driver may each submit one rating per booking.
-- The rater_id tracks WHO submitted this particular rating row.
-- Unique constraint: (booking_id, rater_id) allows two rows per booking
--   — one from the shipper, one from the driver.
-- shipper_id and driver_id identify the two parties involved (for querying).
-- rating: INTEGER 1–5, enforced by CHECK constraint.
-- feedback: optional free-text comment.

CREATE TABLE ratings (
    id          BIGSERIAL PRIMARY KEY,
    booking_id  BIGINT   NOT NULL REFERENCES bookings(id) ON DELETE CASCADE,
    shipper_id  BIGINT   NOT NULL REFERENCES users(id)    ON DELETE CASCADE,
    driver_id   BIGINT   NOT NULL REFERENCES users(id)    ON DELETE CASCADE,
    rater_id    BIGINT            REFERENCES users(id)    ON DELETE SET NULL,
    rating      INTEGER  NOT NULL CHECK (rating >= 1 AND rating <= 5),
    feedback    TEXT,
    created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- Each user can rate a booking at most once
    CONSTRAINT ratings_booking_rater_unique UNIQUE (booking_id, rater_id)
);

CREATE INDEX idx_ratings_booking_id  ON ratings (booking_id);
CREATE INDEX idx_ratings_driver_id   ON ratings (driver_id);
CREATE INDEX idx_ratings_shipper_id  ON ratings (shipper_id);
-- Average driver rating queries
CREATE INDEX idx_ratings_driver_rating ON ratings (driver_id, rating);
