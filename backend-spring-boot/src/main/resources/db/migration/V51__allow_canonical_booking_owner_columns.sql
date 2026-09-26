-- V51: Allow the canonical Booking entity to insert rows while legacy owner
-- columns remain present for older database consumers.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = current_schema() AND table_name = 'bookings' AND column_name = 'shipper_id'
    ) THEN
        ALTER TABLE bookings ALTER COLUMN shipper_id DROP NOT NULL;
    END IF;
END $$;
