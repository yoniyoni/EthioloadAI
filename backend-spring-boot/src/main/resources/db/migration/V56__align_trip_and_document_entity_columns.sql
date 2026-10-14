-- Add fields required by Trip and DriverDocument on a fresh installation.
-- Preserve existing values; derive missing values from their canonical source.
ALTER TABLE trips
    ADD COLUMN IF NOT EXISTS driver_id BIGINT,
    ADD COLUMN IF NOT EXISTS vehicle_id BIGINT;

UPDATE trips t
SET driver_id = b.driver_id
FROM bookings b
WHERE t.booking_id = b.id AND t.driver_id IS NULL;

UPDATE trips t
SET vehicle_id = b.vehicle_id
FROM bookings b
WHERE t.booking_id = b.id AND t.vehicle_id IS NULL;

ALTER TABLE trips
    ALTER COLUMN driver_id SET NOT NULL,
    ALTER COLUMN vehicle_id SET NOT NULL;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'trips'::regclass AND conname = 'fk_trips_driver_id'
    ) THEN
        ALTER TABLE trips ADD CONSTRAINT fk_trips_driver_id
            FOREIGN KEY (driver_id) REFERENCES users(id);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'trips'::regclass AND conname = 'fk_trips_vehicle_id'
    ) THEN
        ALTER TABLE trips ADD CONSTRAINT fk_trips_vehicle_id
            FOREIGN KEY (vehicle_id) REFERENCES vehicles(id);
    END IF;
END $$;

ALTER TABLE driver_documents
    ADD COLUMN IF NOT EXISTS document_label VARCHAR(100),
    ADD COLUMN IF NOT EXISTS file_url VARCHAR(500);

-- Match DocumentType.getLabel() and the existing upload service's fileUrl value.
UPDATE driver_documents
SET document_label = CASE LOWER(document_type)
    WHEN 'license' THEN 'Driver''s License'
    WHEN 'national_id' THEN 'National ID / Kebele ID'
    WHEN 'vehicle_registration' THEN 'Vehicle Registration'
    WHEN 'insurance' THEN 'Insurance Certificate'
    WHEN 'tin' THEN 'TIN Certificate'
END
WHERE document_label IS NULL;

UPDATE driver_documents
SET file_url = file_path
WHERE file_url IS NULL;

-- Missing source data must be reviewed instead of replaced with placeholders.
ALTER TABLE driver_documents
    ALTER COLUMN document_label SET NOT NULL,
    ALTER COLUMN file_url SET NOT NULL;
