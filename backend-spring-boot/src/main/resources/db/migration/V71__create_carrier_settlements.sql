-- Phase 7: Carrier Company Settlement table.
--
-- Created when a delivery is verified. Records the commercial breakdown:
--   gross_amount     = booking.estimated_price  (= CarrierOffer.agreed_amount)
--   commission_rate  = platform commission rate (0.10 standard)
--   commission_amount = gross_amount × commission_rate
--   net_amount       = gross_amount − commission_amount
--
-- Settlement recipient is always the CARRIER COMPANY, not the individual driver.
-- Driver salary/payroll is out of scope for freight settlement.
--
-- Lifecycle:
--   PENDING → PROCESSING → PAID
--                       → FAILED      (payment gateway error; may be retried)
--   PENDING → BLOCKED                 (open dispute blocking settlement)
--   BLOCKED → PENDING                 (dispute resolved)
--   PENDING/FAILED → CANCELLED        (admin cancels)
--
-- Idempotency:
--   Unique constraint on booking_id: at most one settlement per booking.
--   idempotency_key: used to guard against duplicate payment gateway callbacks.
--
-- Currency:
--   All amounts in ETB. Scale 2, HALF_UP rounding.
--
-- Amount integrity constraint:
--   net_amount = gross_amount − commission_amount (enforced in application layer;
--   DB-level CHECK on NUMERIC is unreliable with rounding, so we only guard ranges).

CREATE TABLE carrier_settlements (
    id                  BIGSERIAL PRIMARY KEY,

    -- Core relationships
    booking_id          BIGINT NOT NULL REFERENCES bookings(id) ON DELETE RESTRICT,
    carrier_company_id  BIGINT NOT NULL REFERENCES carrier_companies(id),
    proof_of_delivery_id BIGINT REFERENCES proof_of_delivery(id),

    -- Amounts — all server-calculated; never from client
    gross_amount        DECIMAL(15,2) NOT NULL CHECK (gross_amount > 0),
    commission_rate     DECIMAL(5,4)  NOT NULL CHECK (commission_rate >= 0 AND commission_rate < 1),
    commission_amount   DECIMAL(15,2) NOT NULL CHECK (commission_amount >= 0),
    net_amount          DECIMAL(15,2) NOT NULL CHECK (net_amount >= 0),

    -- Currency
    currency            VARCHAR(3)   NOT NULL DEFAULT 'ETB',

    -- Lifecycle
    status              VARCHAR(20)  NOT NULL DEFAULT 'PENDING'
                            CHECK (status IN (
                                'PENDING',
                                'PROCESSING',
                                'PAID',
                                'FAILED',
                                'BLOCKED',
                                'CANCELLED'
                            )),

    -- Payment gateway link (nullable — used when real provider is wired up)
    payment_reference   VARCHAR(255),
    external_tx_id      VARCHAR(255),

    -- Timestamps
    created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processed_at        TIMESTAMP,
    paid_at             TIMESTAMP,
    failed_at           TIMESTAMP,
    updated_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- Failure information (for FAILED state and retry audit)
    failure_reason      TEXT,
    retry_count         INTEGER NOT NULL DEFAULT 0 CHECK (retry_count >= 0),

    -- Idempotency key — prevents duplicate processing callbacks
    idempotency_key     VARCHAR(255) UNIQUE,

    -- Optimistic locking for concurrent settlement state updates
    version             BIGINT NOT NULL DEFAULT 0,

    -- ── Uniqueness ──────────────────────────────────────────────────────────

    -- At most one settlement per booking (idempotency guarantee)
    CONSTRAINT uq_settlement_per_booking UNIQUE (booking_id),

    -- ── State consistency ────────────────────────────────────────────────────

    -- paid_at required when PAID
    CONSTRAINT ck_settlement_paid_at CHECK (
        (status = 'PAID' AND paid_at IS NOT NULL)
        OR (status <> 'PAID')
    ),

    -- failed_at required when FAILED
    CONSTRAINT ck_settlement_failed_at CHECK (
        (status = 'FAILED' AND failed_at IS NOT NULL)
        OR (status <> 'FAILED')
    )
);

-- Indexes
CREATE INDEX idx_settlement_booking_id         ON carrier_settlements(booking_id);
CREATE INDEX idx_settlement_carrier_company_id ON carrier_settlements(carrier_company_id, status);
CREATE INDEX idx_settlement_status             ON carrier_settlements(status, created_at DESC);
CREATE INDEX idx_settlement_idempotency_key    ON carrier_settlements(idempotency_key)
    WHERE idempotency_key IS NOT NULL;
