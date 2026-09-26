-- Complete the Spring KYC entity columns missing from the legacy table.
ALTER TABLE driver_documents
    ADD COLUMN IF NOT EXISTS reviewed_by BIGINT,
    ADD COLUMN IF NOT EXISTS reviewed_at TIMESTAMP;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE table_name = 'driver_documents'
          AND constraint_name = 'fk_driver_documents_reviewed_by'
    ) THEN
        ALTER TABLE driver_documents
            ADD CONSTRAINT fk_driver_documents_reviewed_by
            FOREIGN KEY (reviewed_by) REFERENCES users(id) ON DELETE SET NULL;
    END IF;
END $$;
