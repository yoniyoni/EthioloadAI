-- V20: Harden payment table for M7 requirements.
--
-- 1. The UNIQUE constraint on booking_id already exists (V10), providing the
--    primary idempotency guard.  This migration adds a partial index for
--    fast "does a PENDING payment exist?" queries, which the service layer
--    uses to avoid a full-table scan on repeated retries.
--
-- 2. Add a non-null CHECK on payment_method (VARCHAR NOT NULL already exists
--    from V10; this migration is a no-op DDL-wise for that column, but
--    documents the intent).
--
-- NOTE: All monetary constraints (amount > 0, commission >= 0, net >= 0)
-- are already in V10 and do not need to be repeated here.

-- Partial index: fast lookup for idempotency check (existsByBooking_Id)
-- and for monitoring pending payments.
CREATE INDEX IF NOT EXISTS idx_payments_status_pending
    ON payments (payment_status)
    WHERE payment_status = 'pending';

-- Partial index: fast lookup of completed payments for analytics.
CREATE INDEX IF NOT EXISTS idx_payments_status_paid
    ON payments (payment_status)
    WHERE payment_status = 'paid';

-- Composite index to support getPaymentsForCaller queries by driver.
-- The booking table already has idx_bookings_driver_id, but this covers
-- the join path payments → bookings(driver_id).
CREATE INDEX IF NOT EXISTS idx_payments_booking_id_status
    ON payments (booking_id, payment_status);
