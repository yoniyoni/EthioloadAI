-- Align the legacy driver_documents storage column with the Spring entity.
-- Preserve existing file references when this migration runs.
ALTER TABLE driver_documents
    ADD COLUMN IF NOT EXISTS file_path VARCHAR(500);

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = current_schema() AND table_name = 'driver_documents' AND column_name = 'file_url'
    ) THEN
        UPDATE driver_documents
        SET file_path = file_url
        WHERE file_path IS NULL AND file_url IS NOT NULL;
    END IF;
END $$;

ALTER TABLE driver_documents
    ALTER COLUMN file_path SET NOT NULL;
