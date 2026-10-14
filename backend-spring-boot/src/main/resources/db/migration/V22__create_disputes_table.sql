-- V22: Create disputes table for Freight-Link dispute capability (B7).
--
-- Records and manages disputes filed by shippers or matched drivers for freight shipments.
--
-- Supports:
--   - Dispute filing with structured reasons and descriptions
--   - Lifecycle tracking: open -> under_review -> resolved / closed
--   - Admin resolution rationale, resolution types (investigating, release, refund, split)
--   - Refund amount tracking

CREATE TABLE disputes (
    id             BIGSERIAL PRIMARY KEY,
    freight_id     BIGINT        NOT NULL REFERENCES freight(id)  ON DELETE CASCADE,
    booking_id     BIGINT                 REFERENCES bookings(id) ON DELETE SET NULL,
    reported_by_id BIGINT        NOT NULL REFERENCES users(id)    ON DELETE CASCADE,
    reason         VARCHAR(255)  NOT NULL,
    description    VARCHAR(2000),
    status         VARCHAR(30)   NOT NULL DEFAULT 'open'
        CHECK (status IN ('open', 'under_review', 'resolved', 'closed')),
    resolution     VARCHAR(50)
        CHECK (resolution IS NULL OR resolution IN ('investigating', 'release', 'refund', 'split')),
    admin_notes    VARCHAR(2000),
    refund_amount  NUMERIC(15, 2),
    created_at     TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    resolved_at    TIMESTAMP
);

CREATE INDEX idx_disputes_freight_id      ON disputes (freight_id);
CREATE INDEX idx_disputes_freight_status  ON disputes (freight_id, status);
CREATE INDEX idx_disputes_reported_by     ON disputes (reported_by_id);
CREATE INDEX idx_disputes_status          ON disputes (status);
