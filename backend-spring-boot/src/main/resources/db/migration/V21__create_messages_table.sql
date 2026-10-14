-- V21: Create messages table for Freight-Link messaging capability (B2).
--
-- Ties messages to a freight shipment between authorized participants
-- (shipper and matched driver).
--
-- Supports:
--   - Text content up to 2000 characters
--   - Phone number detection and masking (for escrow safety during active transactions)
--   - Off-platform payment request detection
--   - Read state tracking per recipient

CREATE TABLE messages (
    id                  BIGSERIAL PRIMARY KEY,
    freight_id          BIGINT        NOT NULL REFERENCES freight(id) ON DELETE CASCADE,
    sender_id           BIGINT        NOT NULL REFERENCES users(id)   ON DELETE CASCADE,
    receiver_id         BIGINT        NOT NULL REFERENCES users(id)   ON DELETE CASCADE,
    content             VARCHAR(2000) NOT NULL,
    masked_content      VARCHAR(2000),
    type                VARCHAR(20)   NOT NULL DEFAULT 'text'
        CHECK (type IN ('text', 'system', 'alert')),
    has_phone_number    BOOLEAN       NOT NULL DEFAULT FALSE,
    has_payment_request BOOLEAN       NOT NULL DEFAULT FALSE,
    is_read             BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at          TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_messages_freight_id      ON messages (freight_id);
CREATE INDEX idx_messages_freight_created ON messages (freight_id, created_at DESC);
CREATE INDEX idx_messages_receiver_read   ON messages (receiver_id, is_read);
CREATE INDEX idx_messages_sender_id       ON messages (sender_id);
