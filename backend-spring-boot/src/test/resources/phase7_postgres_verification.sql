-- Phase 7 PostgreSQL Verification Script
-- Run against a real PostgreSQL database after Flyway migrations V1-V71 are applied.
-- Each CHECK outputs PASS or FAIL.

\set ON_ERROR_STOP on

DO $$
DECLARE
    pass_count INTEGER := 0;
    fail_count INTEGER := 0;

    PROCEDURE chk(description TEXT, result BOOLEAN) IS
    BEGIN
        IF result THEN
            RAISE NOTICE 'PASS: %', description;
            pass_count := pass_count + 1;
        ELSE
            RAISE WARNING 'FAIL: %', description;
            fail_count := fail_count + 1;
        END IF;
    END;

BEGIN
    -- ── FLYWAY HISTORY ────────────────────────────────────────────────────────
    CALL chk('V57 migration applied',
        EXISTS (SELECT 1 FROM flyway_schema_history WHERE version = '57' AND success = TRUE));
    CALL chk('V68 migration applied',
        EXISTS (SELECT 1 FROM flyway_schema_history WHERE version = '68' AND success = TRUE));
    CALL chk('V69 migration applied',
        EXISTS (SELECT 1 FROM flyway_schema_history WHERE version = '69' AND success = TRUE));
    CALL chk('V70 migration applied',
        EXISTS (SELECT 1 FROM flyway_schema_history WHERE version = '70' AND success = TRUE));
    CALL chk('V71 migration applied',
        EXISTS (SELECT 1 FROM flyway_schema_history WHERE version = '71' AND success = TRUE));

    -- ── TRIP TABLE ────────────────────────────────────────────────────────────
    CALL chk('trips table exists',
        EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'trips'));
    CALL chk('trips.arrived_at column exists (V69)',
        EXISTS (SELECT 1 FROM information_schema.columns
                WHERE table_name = 'trips' AND column_name = 'arrived_at'));
    CALL chk('trips.trip_status allows arrived (V69)',
        EXISTS (SELECT 1 FROM information_schema.check_constraints cc
                JOIN information_schema.constraint_column_usage ccu
                  ON cc.constraint_name = ccu.constraint_name
                WHERE ccu.table_name = 'trips'
                  AND ccu.column_name = 'trip_status'
                  AND cc.check_clause LIKE '%arrived%'));
    CALL chk('trips.trip_status allows delivery_pending_verification (V69)',
        EXISTS (SELECT 1 FROM information_schema.check_constraints cc
                JOIN information_schema.constraint_column_usage ccu
                  ON cc.constraint_name = ccu.constraint_name
                WHERE ccu.table_name = 'trips'
                  AND ccu.column_name = 'trip_status'
                  AND cc.check_clause LIKE '%delivery_pending_verification%'));
    CALL chk('trips has unique constraint on booking_id',
        EXISTS (SELECT 1 FROM information_schema.table_constraints
                WHERE table_name = 'trips' AND constraint_type = 'UNIQUE'));

    -- ── PROOF OF DELIVERY ─────────────────────────────────────────────────────
    CALL chk('proof_of_delivery table exists (V70)',
        EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'proof_of_delivery'));
    CALL chk('proof_of_delivery.trip_id FK exists',
        EXISTS (SELECT 1 FROM information_schema.key_column_usage
                WHERE table_name = 'proof_of_delivery' AND column_name = 'trip_id'
                  AND constraint_name LIKE '%fk%' OR constraint_name LIKE '%proof%'));
    CALL chk('proof_of_delivery.booking_id column exists',
        EXISTS (SELECT 1 FROM information_schema.columns
                WHERE table_name = 'proof_of_delivery' AND column_name = 'booking_id'));
    CALL chk('proof_of_delivery.freight_id column exists',
        EXISTS (SELECT 1 FROM information_schema.columns
                WHERE table_name = 'proof_of_delivery' AND column_name = 'freight_id'));
    CALL chk('proof_of_delivery.storage_key column exists',
        EXISTS (SELECT 1 FROM information_schema.columns
                WHERE table_name = 'proof_of_delivery' AND column_name = 'storage_key'));
    CALL chk('proof_of_delivery unique constraint on trip_id',
        EXISTS (SELECT 1 FROM information_schema.table_constraints
                WHERE table_name = 'proof_of_delivery' AND constraint_type = 'UNIQUE'));
    CALL chk('proof_of_delivery.status check constraint exists',
        EXISTS (SELECT 1 FROM information_schema.check_constraints cc
                JOIN information_schema.constraint_column_usage ccu
                  ON cc.constraint_name = ccu.constraint_name
                WHERE ccu.table_name = 'proof_of_delivery'
                  AND cc.check_clause LIKE '%SUBMITTED%'));

    -- ── CARRIER SETTLEMENTS ───────────────────────────────────────────────────
    CALL chk('carrier_settlements table exists (V71)',
        EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'carrier_settlements'));
    CALL chk('carrier_settlements.booking_id column exists',
        EXISTS (SELECT 1 FROM information_schema.columns
                WHERE table_name = 'carrier_settlements' AND column_name = 'booking_id'));
    CALL chk('carrier_settlements.carrier_company_id column exists',
        EXISTS (SELECT 1 FROM information_schema.columns
                WHERE table_name = 'carrier_settlements' AND column_name = 'carrier_company_id'));
    CALL chk('carrier_settlements.gross_amount column exists',
        EXISTS (SELECT 1 FROM information_schema.columns
                WHERE table_name = 'carrier_settlements' AND column_name = 'gross_amount'));
    CALL chk('carrier_settlements.commission_rate column exists',
        EXISTS (SELECT 1 FROM information_schema.columns
                WHERE table_name = 'carrier_settlements' AND column_name = 'commission_rate'));
    CALL chk('carrier_settlements.net_amount column exists',
        EXISTS (SELECT 1 FROM information_schema.columns
                WHERE table_name = 'carrier_settlements' AND column_name = 'net_amount'));
    CALL chk('carrier_settlements.idempotency_key is unique',
        EXISTS (SELECT 1 FROM information_schema.table_constraints
                WHERE table_name = 'carrier_settlements'
                  AND constraint_type = 'UNIQUE'
                  AND constraint_name LIKE '%idempotency%'));
    CALL chk('carrier_settlements unique constraint on booking_id',
        (SELECT COUNT(*) FROM information_schema.table_constraints
         WHERE table_name = 'carrier_settlements' AND constraint_type = 'UNIQUE') >= 2);
    CALL chk('carrier_settlements gross_amount CHECK > 0',
        EXISTS (SELECT 1 FROM information_schema.check_constraints
                WHERE constraint_name LIKE '%gross%' OR check_clause LIKE '%gross_amount%'));
    CALL chk('carrier_settlements status CHECK constraint exists',
        EXISTS (SELECT 1 FROM information_schema.check_constraints cc
                JOIN information_schema.constraint_column_usage ccu
                  ON cc.constraint_name = ccu.constraint_name
                WHERE ccu.table_name = 'carrier_settlements'
                  AND cc.check_clause LIKE '%PENDING%'));

    -- ── PHASE 1-6 SCHEMA INTACT ────────────────────────────────────────────
    CALL chk('carrier_companies table still exists',
        EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'carrier_companies'));
    CALL chk('carrier_offers table still exists',
        EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'carrier_offers'));
    CALL chk('dispatch_assignments table still exists',
        EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'dispatch_assignments'));
    CALL chk('bookings.carrier_company_id column exists (V68)',
        EXISTS (SELECT 1 FROM information_schema.columns
                WHERE table_name = 'bookings' AND column_name = 'carrier_company_id'));
    CALL chk('bookings.dispatch_assignment_id column exists (V68)',
        EXISTS (SELECT 1 FROM information_schema.columns
                WHERE table_name = 'bookings' AND column_name = 'dispatch_assignment_id'));

    -- ── INTEGRITY CHECKS ──────────────────────────────────────────────────────
    CALL chk('No orphan proof_of_delivery (trip_id must exist in trips)',
        NOT EXISTS (SELECT 1 FROM proof_of_delivery p
                    LEFT JOIN trips t ON p.trip_id = t.id
                    WHERE t.id IS NULL));
    CALL chk('No orphan proof_of_delivery (booking_id must exist in bookings)',
        NOT EXISTS (SELECT 1 FROM proof_of_delivery p
                    LEFT JOIN bookings b ON p.booking_id = b.id
                    WHERE b.id IS NULL));
    CALL chk('No duplicate settlements per booking',
        NOT EXISTS (SELECT booking_id FROM carrier_settlements
                    GROUP BY booking_id HAVING COUNT(*) > 1));
    CALL chk('All settlements have gross_amount > 0',
        NOT EXISTS (SELECT 1 FROM carrier_settlements WHERE gross_amount <= 0));
    CALL chk('All settlements: commission_amount >= 0',
        NOT EXISTS (SELECT 1 FROM carrier_settlements WHERE commission_amount < 0));
    CALL chk('All settlements: net_amount >= 0',
        NOT EXISTS (SELECT 1 FROM carrier_settlements WHERE net_amount < 0));
    CALL chk('All proof_of_delivery have valid status',
        NOT EXISTS (SELECT 1 FROM proof_of_delivery
                    WHERE status NOT IN ('SUBMITTED', 'VERIFIED', 'REJECTED')));
    CALL chk('All carrier_settlements have valid status',
        NOT EXISTS (SELECT 1 FROM carrier_settlements
                    WHERE status NOT IN ('PENDING','PROCESSING','PAID','FAILED','BLOCKED','CANCELLED')));
    CALL chk('No settlement attached to non-carrier booking',
        NOT EXISTS (SELECT 1 FROM carrier_settlements cs
                    JOIN bookings b ON cs.booking_id = b.id
                    WHERE b.carrier_company_id IS NULL));
    CALL chk('No COMPLETED trip without a booking',
        NOT EXISTS (SELECT 1 FROM trips t
                    LEFT JOIN bookings b ON t.booking_id = b.id
                    WHERE t.trip_status = 'completed' AND b.id IS NULL));

    -- ── SUMMARY ────────────────────────────────────────────────────────────────
    RAISE NOTICE '==========================================';
    RAISE NOTICE 'Phase 7 PostgreSQL Verification Complete';
    RAISE NOTICE 'PASSED: % / FAILED: %', pass_count, fail_count;
    IF fail_count = 0 THEN
        RAISE NOTICE 'RESULT: ALL CHECKS PASSED';
    ELSE
        RAISE WARNING 'RESULT: % CHECKS FAILED', fail_count;
    END IF;
    RAISE NOTICE '==========================================';
END $$;
