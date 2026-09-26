-- Phase 4: Driver ↔ Vehicle assignment history table.
-- Each row is an assignment event.  Only one ACTIVE assignment per driver AND
-- per vehicle is allowed at any point in time (partial unique indexes enforce this).

CREATE TABLE vehicle_driver_assignments (
    id                  BIGSERIAL PRIMARY KEY,
    carrier_company_id  BIGINT NOT NULL REFERENCES carrier_companies(id) ON DELETE CASCADE,
    carrier_driver_id   BIGINT NOT NULL REFERENCES carrier_drivers(id),
    vehicle_id          BIGINT NOT NULL REFERENCES vehicles(id),

    -- ACTIVE   = current assignment
    -- ENDED    = assignment ended normally (unassign / reassign)
    -- CANCELLED = cancelled before the driver accepted or trip started
    status              VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
                            CHECK (status IN ('ACTIVE','ENDED','CANCELLED')),

    assigned_by         BIGINT NOT NULL REFERENCES users(id),
    ended_by            BIGINT REFERENCES users(id),

    assigned_at         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ended_at            TIMESTAMP,
    notes               VARCHAR(1000),

    created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- Business rule: ended/cancelled assignments must have an ended_at.
    CONSTRAINT ck_assignment_ended_at CHECK (
        (status = 'ACTIVE' AND ended_at IS NULL)
        OR (status IN ('ENDED','CANCELLED') AND ended_at IS NOT NULL)
    )
);

-- Enforce at most one ACTIVE assignment per driver.
CREATE UNIQUE INDEX uq_active_driver_assignment
    ON vehicle_driver_assignments(carrier_driver_id)
    WHERE status = 'ACTIVE';

-- Enforce at most one ACTIVE assignment per vehicle.
CREATE UNIQUE INDEX uq_active_vehicle_assignment
    ON vehicle_driver_assignments(vehicle_id)
    WHERE status = 'ACTIVE';

-- Query indexes.
CREATE INDEX idx_assignment_company
    ON vehicle_driver_assignments(carrier_company_id, status);
CREATE INDEX idx_assignment_vehicle
    ON vehicle_driver_assignments(vehicle_id, status);
CREATE INDEX idx_assignment_driver
    ON vehicle_driver_assignments(carrier_driver_id, status);
CREATE INDEX idx_assignment_assigned_by
    ON vehicle_driver_assignments(assigned_by);
CREATE INDEX idx_assignment_ended_by
    ON vehicle_driver_assignments(ended_by);

-- ── Backfill V63 ──────────────────────────────────────────────────────────────
-- If any vehicles already have carrier_company_id set (from future live data
-- migration), and the corresponding fleet owner is a CARRIER_OWNER, backfill
-- a CarrierDriver row so existing data is not orphaned.
-- This is intentionally conservative: only rows where a matching active
-- CARRIER_OWNER membership exists are backfilled.

INSERT INTO carrier_drivers (
    carrier_company_id, driver_user_id, status, availability, added_by, joined_at, created_at, updated_at
)
SELECT DISTINCT
    v.carrier_company_id,
    v.user_id,               -- user_id = currently assigned driver (or fleet owner placeholder)
    'ACTIVE',
    'AVAILABLE',
    m.user_id,               -- added_by = company owner
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
FROM vehicles v
JOIN carrier_companies cc ON cc.id = v.carrier_company_id
JOIN company_memberships m ON m.carrier_company_id = v.carrier_company_id
    AND m.role = 'CARRIER_OWNER' AND m.status = 'ACTIVE'
JOIN users u ON u.id = v.user_id AND u.role = 'driver'
WHERE v.carrier_company_id IS NOT NULL
    AND NOT EXISTS (
        SELECT 1 FROM carrier_drivers cd
        WHERE cd.carrier_company_id = v.carrier_company_id
          AND cd.driver_user_id = v.user_id
    );
