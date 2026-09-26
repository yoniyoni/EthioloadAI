-- V50: Allow the canonical Booking entity to insert rows while the legacy
-- freight_id alias remains present for older database consumers.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = current_schema() AND table_name = 'bookings' AND column_name = 'freight_id'
    ) THEN
        ALTER TABLE bookings ALTER COLUMN freight_id DROP NOT NULL;
    END IF;
END $$;
