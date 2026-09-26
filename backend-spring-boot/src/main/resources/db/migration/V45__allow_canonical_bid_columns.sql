-- V45: Keep upgraded legacy bid schemas compatible with canonical JPA columns.
-- V41 copied legacy values into the canonical columns but left old required
-- columns in place. New writes use cargo_request_id and amount.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_name = 'bids'
          AND column_name = 'freight_id'
    ) THEN
        ALTER TABLE bids ALTER COLUMN freight_id DROP NOT NULL;
    END IF;

    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_name = 'bids'
          AND column_name = 'bid_amount'
    ) THEN
        ALTER TABLE bids ALTER COLUMN bid_amount DROP NOT NULL;
    END IF;
END $$;