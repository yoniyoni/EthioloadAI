-- Phase 4: Carrier-company driver relationship table.
-- Separates "User identity" from "driver's relationship with a carrier company".
-- A driver may be in at most one ACTIVE carrier-company relationship at a time
-- (enforced by the partial unique index below).
-- Historical rows (INACTIVE/REMOVED) are preserved for trip traceability.

CREATE TABLE carrier_drivers (
    id                  BIGSERIAL PRIMARY KEY,
    carrier_company_id  BIGINT NOT NULL REFERENCES carrier_companies(id) ON DELETE CASCADE,
    driver_user_id      BIGINT NOT NULL REFERENCES users(id),

    -- Lifecycle status within the company.
    -- ACTIVE   = current driver, can be assigned
    -- INACTIVE = temporarily suspended by owner/fleet-manager, cannot be assigned
    -- REMOVED  = left or was removed; historical record kept for trip traceability
    status              VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
                            CHECK (status IN ('ACTIVE', 'INACTIVE', 'REMOVED')),

    -- Driver operational availability (set by fleet manager or derived from trips).
    -- AVAILABLE    = ready to accept a vehicle/job
    -- ASSIGNED     = has an active vehicle assignment
    -- ON_TRIP      = currently on an active trip
    -- UNAVAILABLE  = set unavailable manually (e.g. sick leave)
    availability        VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE'
                            CHECK (availability IN ('AVAILABLE','ASSIGNED','ON_TRIP','UNAVAILABLE')),

    added_by            BIGINT REFERENCES users(id),
    joined_at           TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    left_at             TIMESTAMP,
    notes               VARCHAR(1000),

    created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- One driver, one company (per row); history allowed via INACTIVE/REMOVED rows.
    CONSTRAINT uq_carrier_driver_active UNIQUE (carrier_company_id, driver_user_id),

    -- An ACTIVE driver must not have a left_at date.
    CONSTRAINT ck_carrier_driver_left_at CHECK (
        (status = 'ACTIVE' AND left_at IS NULL)
        OR (status IN ('INACTIVE','REMOVED'))
    )
);

-- Efficient company-scoped driver queries.
CREATE INDEX idx_carrier_driver_company         ON carrier_drivers(carrier_company_id, status);
CREATE INDEX idx_carrier_driver_user            ON carrier_drivers(driver_user_id);
CREATE INDEX idx_carrier_driver_avail           ON carrier_drivers(carrier_company_id, availability);
CREATE INDEX idx_carrier_driver_added_by        ON carrier_drivers(added_by);
