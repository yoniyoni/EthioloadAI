-- Fix: Normalise any uppercase enum values that may have been inserted before
-- VehicleCategoryConverter and FreightStatusConverter were introduced.
--
-- Root cause: @Enumerated(EnumType.STRING) writes Java enum names in uppercase
-- (e.g. 'HEAVY', 'POSTED'), but the CHECK constraints in V2 and V3 expect lowercase
-- ('heavy', 'posted'). The JPA converters introduced alongside this migration fix
-- the write path going forward. This migration normalises any existing data.
--
-- These UPDATE statements are safe to run on an empty schema (0 rows updated).

-- vehicles.vehicle_category: uppercase → lowercase
UPDATE vehicles
SET vehicle_category = LOWER(vehicle_category)
WHERE vehicle_category IS NOT NULL
  AND vehicle_category != LOWER(vehicle_category);

-- freight.status: uppercase → lowercase
UPDATE freight
SET status = LOWER(status)
WHERE status IS NOT NULL
  AND status != LOWER(status);
