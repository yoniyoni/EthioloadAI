-- Phase 7: Proof of Delivery table.
--
-- A ProofOfDelivery is submitted by the assigned driver after reaching the
-- destination. The shipper then verifies or rejects it.
--
-- Lifecycle:
--   SUBMITTED → VERIFIED   (shipper confirms receipt)
--   SUBMITTED → REJECTED   (shipper rejects → opens dispute)
--
-- Security invariants:
--   * Only the trip's assigned driver may create a proof row.
--   * Only the freight's shipper (or ADMIN) may verify or reject.
--   * file_path is NEVER returned to API clients — only a download endpoint.
--   * One proof per trip (unique constraint on trip_id).

CREATE TABLE proof_of_delivery (
    id              BIGSERIAL PRIMARY KEY,

    -- Core relationships
    trip_id         BIGINT NOT NULL REFERENCES trips(id) ON DELETE RESTRICT,
    booking_id      BIGINT NOT NULL REFERENCES bookings(id) ON DELETE RESTRICT,
    freight_id      BIGINT NOT NULL REFERENCES freight(id) ON DELETE RESTRICT,

    -- Uploader
    uploaded_by     BIGINT NOT NULL REFERENCES users(id),

    -- File storage (mirrors DriverDocument pattern)
    storage_key     VARCHAR(500) NOT NULL,   -- internal storage key / path; never exposed
    file_name       VARCHAR(255) NOT NULL,   -- original filename shown to users
    content_type    VARCHAR(100) NOT NULL,
    file_size       BIGINT NOT NULL CHECK (file_size > 0),

    -- Optional delivery metadata
    recipient_name  VARCHAR(255),
    note            TEXT,

    -- Lifecycle
    status          VARCHAR(40)  NOT NULL DEFAULT 'SUBMITTED'
                        CHECK (status IN ('SUBMITTED', 'VERIFIED', 'REJECTED')),

    -- Review
    reviewed_by     BIGINT REFERENCES users(id),
    reviewed_at     TIMESTAMP,
    rejection_reason TEXT,

    -- Timestamps
    uploaded_at     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- One proof per trip — prevents duplicate proof uploads after verification
    CONSTRAINT uq_proof_per_trip UNIQUE (trip_id),

    -- reviewed_at and reviewed_by must both be set for terminal states
    CONSTRAINT ck_proof_review_consistency CHECK (
        (status IN ('VERIFIED', 'REJECTED') AND reviewed_by IS NOT NULL AND reviewed_at IS NOT NULL)
        OR (status = 'SUBMITTED')
    ),

    -- rejection_reason required when REJECTED
    CONSTRAINT ck_proof_rejection_reason CHECK (
        (status = 'REJECTED' AND rejection_reason IS NOT NULL)
        OR (status <> 'REJECTED')
    )
);

-- Indexes
CREATE INDEX idx_pod_trip_id     ON proof_of_delivery(trip_id);
CREATE INDEX idx_pod_booking_id  ON proof_of_delivery(booking_id);
CREATE INDEX idx_pod_freight_id  ON proof_of_delivery(freight_id);
CREATE INDEX idx_pod_status      ON proof_of_delivery(status);
CREATE INDEX idx_pod_uploaded_by ON proof_of_delivery(uploaded_by);
