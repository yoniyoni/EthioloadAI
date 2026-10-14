-- V10: Create the payments table.
--
-- KEY DESIGN DECISIONS:
--
-- 1. DECIMAL(15,2) for all monetary fields (not DECIMAL(10,2)).
--    ETB freight prices can exceed 999,999,999.99 for large commercial loads.
--    15 digits allows up to 9,999,999,999,999.99 ETB — safely covers all cases.
--
-- 2. amount, commission_amount, driver_net_amount are ALL server-calculated.
--    The API must NEVER accept these from client input.
--    amount            = booking.estimated_price
--    commission_amount = amount × commission_rate (10% default, 5% for fleet)
--    driver_net_amount = amount - commission_amount
--
-- 3. One payment per booking enforced by UNIQUE constraint.
--    Prevents duplicate payments via concurrent requests.
--
-- 4. paid_by: the user who triggered the payment record (shipper or driver).
--
-- 5. payment_method: free-form string ('cash', 'bank_transfer', etc.).
--    Not an enum to allow future payment methods without migrations.
--
-- payment_status values: pending | paid | failed

CREATE TABLE payments (
    id                 BIGSERIAL PRIMARY KEY,
    booking_id         BIGINT        NOT NULL REFERENCES bookings(id) ON DELETE CASCADE,
    amount             DECIMAL(15, 2) NOT NULL CHECK (amount > 0),
    commission_amount  DECIMAL(15, 2) NOT NULL DEFAULT 0 CHECK (commission_amount >= 0),
    driver_net_amount  DECIMAL(15, 2) NOT NULL DEFAULT 0 CHECK (driver_net_amount >= 0),
    paid_by            BIGINT                  REFERENCES users(id)   ON DELETE SET NULL,
    payment_method     VARCHAR(100)  NOT NULL DEFAULT 'cash',
    payment_status     VARCHAR(20)   NOT NULL DEFAULT 'pending'
        CHECK (payment_status IN ('pending', 'paid', 'failed')),
    transaction_ref    VARCHAR(255),
    created_at         TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- Only one payment record per booking
    CONSTRAINT payments_booking_id_unique UNIQUE (booking_id)
);

CREATE INDEX idx_payments_booking_id      ON payments (booking_id);
CREATE INDEX idx_payments_paid_by         ON payments (paid_by);
CREATE INDEX idx_payments_payment_status  ON payments (payment_status);
