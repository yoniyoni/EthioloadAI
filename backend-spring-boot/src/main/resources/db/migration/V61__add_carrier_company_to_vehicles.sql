-- Phase 4: Associate vehicles with a carrier company.
-- Nullable so existing vehicles (registered before Phase 4) are not broken.
-- Backfill runs in V63 after carrier_drivers exists.
ALTER TABLE vehicles
    ADD COLUMN IF NOT EXISTS carrier_company_id BIGINT
        REFERENCES carrier_companies(id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_vehicles_carrier_company
    ON vehicles(carrier_company_id);

-- Formalise availability_status values for Phase 4.
-- Existing rows use 'available'; new values: 'assigned', 'on_trip', 'maintenance', 'unavailable'.
-- Keep as VARCHAR — no enum migration so existing data is never broken.
-- Application-level converter enforces valid values.
