-- V49: Add Spring dispute columns to the legacy disputes table shape.
ALTER TABLE disputes
    ADD COLUMN IF NOT EXISTS freight_id BIGINT,
    ADD COLUMN IF NOT EXISTS reported_by_id BIGINT,
    ADD COLUMN IF NOT EXISTS reason VARCHAR(255),
    ADD COLUMN IF NOT EXISTS resolution VARCHAR(50),
    ADD COLUMN IF NOT EXISTS admin_notes VARCHAR(2000),
    ADD COLUMN IF NOT EXISTS refund_amount NUMERIC(15, 2);
