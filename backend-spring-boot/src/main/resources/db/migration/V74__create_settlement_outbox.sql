CREATE TABLE settlement_outbox (
    id BIGSERIAL PRIMARY KEY,
    booking_id BIGINT NOT NULL REFERENCES bookings(id) ON DELETE RESTRICT,
    proof_of_delivery_id BIGINT NOT NULL REFERENCES proof_of_delivery(id) ON DELETE RESTRICT,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'PROCESSED')),
    attempt_count INTEGER NOT NULL DEFAULT 0 CHECK (attempt_count >= 0),
    next_attempt_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_error VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processed_at TIMESTAMP,
    CONSTRAINT uq_settlement_outbox_booking UNIQUE (booking_id),
    CONSTRAINT ck_settlement_outbox_processed_at CHECK (
        (status = 'PROCESSED' AND processed_at IS NOT NULL)
        OR (status = 'PENDING' AND processed_at IS NULL)
    )
);

CREATE INDEX idx_settlement_outbox_due
    ON settlement_outbox (status, next_attempt_at, created_at);
