-- V19 originally created partitions only through the end of 2025.
-- Cover the gap and provision two future years without changing existing data.
-- Some legacy databases instead have the nonpartitioned table from V40.
DO $$
DECLARE
    partition_month DATE := DATE '2026-01-01';
    provision_until DATE := make_date(GREATEST(EXTRACT(YEAR FROM CURRENT_DATE)::integer, 2026) + 3, 1, 1);
BEGIN
    IF EXISTS (
        SELECT 1 FROM pg_class
        WHERE oid = 'vehicle_location_history'::regclass AND relkind = 'p'
    ) THEN
        WHILE partition_month < provision_until LOOP
            PERFORM create_monthly_location_partition(partition_month);
            partition_month := (partition_month + INTERVAL '1 month')::date;
        END LOOP;
    END IF;
END $$;
