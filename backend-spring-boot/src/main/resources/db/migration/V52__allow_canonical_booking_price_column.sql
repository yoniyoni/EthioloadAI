-- V52: Allow the canonical estimated_price field while the legacy
-- agreed_price alias remains available to older consumers.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = current_schema() AND table_name = 'bookings' AND column_name = 'agreed_price'
    ) THEN
        ALTER TABLE bookings ALTER COLUMN agreed_price DROP NOT NULL;
    END IF;
END $$;
