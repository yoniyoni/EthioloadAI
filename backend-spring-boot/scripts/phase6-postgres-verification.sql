-- ============================================================
-- Phase 6 PostgreSQL Verification Script
-- EthioLoadAI / Freight-Link
-- Run against PostgreSQL with: psql -d <db> -f phase6-postgres-verification.sql
-- ============================================================
-- Each check prints a label and PASS/FAIL. Count PASSes at end.

\set ON_ERROR_STOP off

DO $$
DECLARE
    v_check_count INT := 0;
    v_pass_count  INT := 0;
    v_rec         RECORD;
    v_count       INT;
BEGIN
    RAISE NOTICE '=== Phase 6 PostgreSQL Verification ===';
    RAISE NOTICE '';

    -- ─────────────────────────────────────────────────────────
    -- SECTION 1: Flyway migration history
    -- ─────────────────────────────────────────────────────────
    RAISE NOTICE '-- Section 1: Flyway migration history';

    -- Check 1: V66 still present and succeeded
    v_check_count := v_check_count + 1;
    SELECT COUNT(*) INTO v_count FROM flyway_schema_history
      WHERE version = '66' AND success = true;
    IF v_count = 1 THEN
        v_pass_count := v_pass_count + 1;
        RAISE NOTICE '  [PASS] V66 migration present and successful';
    ELSE
        RAISE NOTICE '  [FAIL] V66 migration missing or failed';
    END IF;

    -- Check 2: V67 present and succeeded
    v_check_count := v_check_count + 1;
    SELECT COUNT(*) INTO v_count FROM flyway_schema_history
      WHERE version = '67' AND success = true;
    IF v_count = 1 THEN
        v_pass_count := v_pass_count + 1;
        RAISE NOTICE '  [PASS] V67 migration present and successful';
    ELSE
        RAISE NOTICE '  [FAIL] V67 migration missing or failed';
    END IF;

    -- Check 3: V68 present and succeeded
    v_check_count := v_check_count + 1;
    SELECT COUNT(*) INTO v_count FROM flyway_schema_history
      WHERE version = '68' AND success = true;
    IF v_count = 1 THEN
        v_pass_count := v_pass_count + 1;
        RAISE NOTICE '  [PASS] V68 migration present and successful';
    ELSE
        RAISE NOTICE '  [FAIL] V68 migration missing or failed';
    END IF;

    -- Check 4: No failed migrations
    v_check_count := v_check_count + 1;
    SELECT COUNT(*) INTO v_count FROM flyway_schema_history WHERE success = false;
    IF v_count = 0 THEN
        v_pass_count := v_pass_count + 1;
        RAISE NOTICE '  [PASS] No failed migrations';
    ELSE
        RAISE NOTICE '  [FAIL] % failed migration(s) detected', v_count;
    END IF;

    -- ─────────────────────────────────────────────────────────
    -- SECTION 2: dispatch_assignments table structure
    -- ─────────────────────────────────────────────────────────
    RAISE NOTICE '';
    RAISE NOTICE '-- Section 2: dispatch_assignments table';

    -- Check 5: Table exists
    v_check_count := v_check_count + 1;
    SELECT COUNT(*) INTO v_count
      FROM information_schema.tables
      WHERE table_name = 'dispatch_assignments' AND table_schema = 'public';
    IF v_count = 1 THEN
        v_pass_count := v_pass_count + 1;
        RAISE NOTICE '  [PASS] dispatch_assignments table exists';
    ELSE
        RAISE NOTICE '  [FAIL] dispatch_assignments table missing';
    END IF;

    -- Check 6: freight_id FK exists
    v_check_count := v_check_count + 1;
    SELECT COUNT(*) INTO v_count
      FROM information_schema.columns
      WHERE table_name = 'dispatch_assignments' AND column_name = 'freight_id';
    IF v_count = 1 THEN
        v_pass_count := v_pass_count + 1;
        RAISE NOTICE '  [PASS] freight_id column present';
    ELSE
        RAISE NOTICE '  [FAIL] freight_id column missing';
    END IF;

    -- Check 7: carrier_offer_id column
    v_check_count := v_check_count + 1;
    SELECT COUNT(*) INTO v_count
      FROM information_schema.columns
      WHERE table_name = 'dispatch_assignments' AND column_name = 'carrier_offer_id';
    IF v_count = 1 THEN
        v_pass_count := v_pass_count + 1;
        RAISE NOTICE '  [PASS] carrier_offer_id column present';
    ELSE
        RAISE NOTICE '  [FAIL] carrier_offer_id column missing';
    END IF;

    -- Check 8: carrier_company_id column
    v_check_count := v_check_count + 1;
    SELECT COUNT(*) INTO v_count
      FROM information_schema.columns
      WHERE table_name = 'dispatch_assignments' AND column_name = 'carrier_company_id';
    IF v_count = 1 THEN
        v_pass_count := v_pass_count + 1;
        RAISE NOTICE '  [PASS] carrier_company_id column present';
    ELSE
        RAISE NOTICE '  [FAIL] carrier_company_id column missing';
    END IF;

    -- Check 9: driver_user_id column
    v_check_count := v_check_count + 1;
    SELECT COUNT(*) INTO v_count
      FROM information_schema.columns
      WHERE table_name = 'dispatch_assignments' AND column_name = 'driver_user_id';
    IF v_count = 1 THEN
        v_pass_count := v_pass_count + 1;
        RAISE NOTICE '  [PASS] driver_user_id column present';
    ELSE
        RAISE NOTICE '  [FAIL] driver_user_id column missing';
    END IF;

    -- Check 10: vehicle_id column
    v_check_count := v_check_count + 1;
    SELECT COUNT(*) INTO v_count
      FROM information_schema.columns
      WHERE table_name = 'dispatch_assignments' AND column_name = 'vehicle_id';
    IF v_count = 1 THEN
        v_pass_count := v_pass_count + 1;
        RAISE NOTICE '  [PASS] vehicle_id column present';
    ELSE
        RAISE NOTICE '  [FAIL] vehicle_id column missing';
    END IF;

    -- Check 11: agreed_amount column (NOT NULL)
    v_check_count := v_check_count + 1;
    SELECT COUNT(*) INTO v_count
      FROM information_schema.columns
      WHERE table_name = 'dispatch_assignments'
        AND column_name = 'agreed_amount'
        AND is_nullable = 'NO';
    IF v_count = 1 THEN
        v_pass_count := v_pass_count + 1;
        RAISE NOTICE '  [PASS] agreed_amount is NOT NULL';
    ELSE
        RAISE NOTICE '  [FAIL] agreed_amount is nullable or missing';
    END IF;

    -- Check 12: status column with CHECK constraint
    v_check_count := v_check_count + 1;
    SELECT COUNT(*) INTO v_count
      FROM information_schema.check_constraints cc
      JOIN information_schema.constraint_column_usage ccu
        ON cc.constraint_name = ccu.constraint_name
      WHERE ccu.table_name = 'dispatch_assignments'
        AND ccu.column_name = 'status';
    IF v_count > 0 THEN
        v_pass_count := v_pass_count + 1;
        RAISE NOTICE '  [PASS] status CHECK constraint exists';
    ELSE
        RAISE NOTICE '  [FAIL] status CHECK constraint missing';
    END IF;

    -- Check 13: version column for optimistic locking
    v_check_count := v_check_count + 1;
    SELECT COUNT(*) INTO v_count
      FROM information_schema.columns
      WHERE table_name = 'dispatch_assignments' AND column_name = 'version';
    IF v_count = 1 THEN
        v_pass_count := v_pass_count + 1;
        RAISE NOTICE '  [PASS] version column present';
    ELSE
        RAISE NOTICE '  [FAIL] version column missing';
    END IF;

    -- Check 14: booking_id column
    v_check_count := v_check_count + 1;
    SELECT COUNT(*) INTO v_count
      FROM information_schema.columns
      WHERE table_name = 'dispatch_assignments' AND column_name = 'booking_id';
    IF v_count = 1 THEN
        v_pass_count := v_pass_count + 1;
        RAISE NOTICE '  [PASS] booking_id column present';
    ELSE
        RAISE NOTICE '  [FAIL] booking_id column missing';
    END IF;

    -- ─────────────────────────────────────────────────────────
    -- SECTION 3: Indexes on dispatch_assignments
    -- ─────────────────────────────────────────────────────────
    RAISE NOTICE '';
    RAISE NOTICE '-- Section 3: Indexes';

    -- Check 15: uq_active_dispatch_per_freight (partial unique index)
    v_check_count := v_check_count + 1;
    SELECT COUNT(*) INTO v_count
      FROM pg_indexes
      WHERE tablename = 'dispatch_assignments'
        AND indexname = 'uq_active_dispatch_per_freight';
    IF v_count = 1 THEN
        v_pass_count := v_pass_count + 1;
        RAISE NOTICE '  [PASS] uq_active_dispatch_per_freight partial index exists';
    ELSE
        RAISE NOTICE '  [FAIL] uq_active_dispatch_per_freight partial index missing';
    END IF;

    -- Check 16: uq_pending_dispatch_per_driver
    v_check_count := v_check_count + 1;
    SELECT COUNT(*) INTO v_count
      FROM pg_indexes
      WHERE tablename = 'dispatch_assignments'
        AND indexname = 'uq_pending_dispatch_per_driver';
    IF v_count = 1 THEN
        v_pass_count := v_pass_count + 1;
        RAISE NOTICE '  [PASS] uq_pending_dispatch_per_driver partial index exists';
    ELSE
        RAISE NOTICE '  [FAIL] uq_pending_dispatch_per_driver partial index missing';
    END IF;

    -- Check 17: uq_pending_dispatch_per_vehicle
    v_check_count := v_check_count + 1;
    SELECT COUNT(*) INTO v_count
      FROM pg_indexes
      WHERE tablename = 'dispatch_assignments'
        AND indexname = 'uq_pending_dispatch_per_vehicle';
    IF v_count = 1 THEN
        v_pass_count := v_pass_count + 1;
        RAISE NOTICE '  [PASS] uq_pending_dispatch_per_vehicle partial index exists';
    ELSE
        RAISE NOTICE '  [FAIL] uq_pending_dispatch_per_vehicle partial index missing';
    END IF;

    -- Check 18: freight_id index
    v_check_count := v_check_count + 1;
    SELECT COUNT(*) INTO v_count
      FROM pg_indexes
      WHERE tablename = 'dispatch_assignments'
        AND indexname = 'idx_dispatch_freight_id';
    IF v_count = 1 THEN
        v_pass_count := v_pass_count + 1;
        RAISE NOTICE '  [PASS] idx_dispatch_freight_id index exists';
    ELSE
        RAISE NOTICE '  [FAIL] idx_dispatch_freight_id index missing';
    END IF;

    -- ─────────────────────────────────────────────────────────
    -- SECTION 4: bookings table Phase 6 extensions
    -- ─────────────────────────────────────────────────────────
    RAISE NOTICE '';
    RAISE NOTICE '-- Section 4: Bookings Phase 6 columns';

    -- Check 19: carrier_offer_id in bookings
    v_check_count := v_check_count + 1;
    SELECT COUNT(*) INTO v_count
      FROM information_schema.columns
      WHERE table_name = 'bookings' AND column_name = 'carrier_offer_id';
    IF v_count = 1 THEN
        v_pass_count := v_pass_count + 1;
        RAISE NOTICE '  [PASS] bookings.carrier_offer_id present';
    ELSE
        RAISE NOTICE '  [FAIL] bookings.carrier_offer_id missing';
    END IF;

    -- Check 20: dispatch_assignment_id in bookings
    v_check_count := v_check_count + 1;
    SELECT COUNT(*) INTO v_count
      FROM information_schema.columns
      WHERE table_name = 'bookings' AND column_name = 'dispatch_assignment_id';
    IF v_count = 1 THEN
        v_pass_count := v_pass_count + 1;
        RAISE NOTICE '  [PASS] bookings.dispatch_assignment_id present';
    ELSE
        RAISE NOTICE '  [FAIL] bookings.dispatch_assignment_id missing';
    END IF;

    -- Check 21: carrier_company_id in bookings
    v_check_count := v_check_count + 1;
    SELECT COUNT(*) INTO v_count
      FROM information_schema.columns
      WHERE table_name = 'bookings' AND column_name = 'carrier_company_id';
    IF v_count = 1 THEN
        v_pass_count := v_pass_count + 1;
        RAISE NOTICE '  [PASS] bookings.carrier_company_id present';
    ELSE
        RAISE NOTICE '  [FAIL] bookings.carrier_company_id missing';
    END IF;

    -- Check 22: unique index on bookings.dispatch_assignment_id
    v_check_count := v_check_count + 1;
    SELECT COUNT(*) INTO v_count
      FROM pg_indexes
      WHERE tablename = 'bookings'
        AND indexname = 'uq_booking_dispatch_assignment';
    IF v_count = 1 THEN
        v_pass_count := v_pass_count + 1;
        RAISE NOTICE '  [PASS] uq_booking_dispatch_assignment unique index exists';
    ELSE
        RAISE NOTICE '  [FAIL] uq_booking_dispatch_assignment unique index missing';
    END IF;

    -- ─────────────────────────────────────────────────────────
    -- SECTION 5: Business integrity checks
    -- ─────────────────────────────────────────────────────────
    RAISE NOTICE '';
    RAISE NOTICE '-- Section 5: Business integrity';

    -- Check 23: No duplicate active dispatch per freight
    v_check_count := v_check_count + 1;
    SELECT COUNT(*) INTO v_count FROM (
        SELECT freight_id, COUNT(*) AS cnt
          FROM dispatch_assignments
         WHERE status IN ('ASSIGNED', 'DRIVER_ACCEPTED')
         GROUP BY freight_id
        HAVING COUNT(*) > 1
    ) violations;
    IF v_count = 0 THEN
        v_pass_count := v_pass_count + 1;
        RAISE NOTICE '  [PASS] No duplicate active dispatch per freight';
    ELSE
        RAISE NOTICE '  [FAIL] % freight(s) have duplicate active dispatches', v_count;
    END IF;

    -- Check 24: No duplicate booking per dispatch assignment
    v_check_count := v_check_count + 1;
    SELECT COUNT(*) INTO v_count FROM (
        SELECT dispatch_assignment_id, COUNT(*) AS cnt
          FROM bookings
         WHERE dispatch_assignment_id IS NOT NULL
         GROUP BY dispatch_assignment_id
        HAVING COUNT(*) > 1
    ) violations;
    IF v_count = 0 THEN
        v_pass_count := v_pass_count + 1;
        RAISE NOTICE '  [PASS] No duplicate bookings per dispatch assignment';
    ELSE
        RAISE NOTICE '  [FAIL] % dispatch(es) have duplicate bookings', v_count;
    END IF;

    -- Check 25: All DRIVER_ACCEPTED dispatches have a booking_id
    v_check_count := v_check_count + 1;
    SELECT COUNT(*) INTO v_count
      FROM dispatch_assignments
     WHERE status = 'DRIVER_ACCEPTED' AND booking_id IS NULL;
    IF v_count = 0 THEN
        v_pass_count := v_pass_count + 1;
        RAISE NOTICE '  [PASS] All DRIVER_ACCEPTED dispatches have booking_id set';
    ELSE
        RAISE NOTICE '  [FAIL] % DRIVER_ACCEPTED dispatch(es) missing booking_id', v_count;
    END IF;

    -- Check 26: All DRIVER_ACCEPTED dispatches have responded_at set
    v_check_count := v_check_count + 1;
    SELECT COUNT(*) INTO v_count
      FROM dispatch_assignments
     WHERE status = 'DRIVER_ACCEPTED' AND responded_at IS NULL;
    IF v_count = 0 THEN
        v_pass_count := v_pass_count + 1;
        RAISE NOTICE '  [PASS] All DRIVER_ACCEPTED dispatches have responded_at set';
    ELSE
        RAISE NOTICE '  [FAIL] % DRIVER_ACCEPTED dispatch(es) missing responded_at', v_count;
    END IF;

    -- Check 27: No dispatch cross-company violation
    --   (driver must belong to dispatch.carrier_company_id)
    v_check_count := v_check_count + 1;
    SELECT COUNT(*) INTO v_count FROM (
        SELECT da.id
          FROM dispatch_assignments da
         WHERE NOT EXISTS (
             SELECT 1 FROM carrier_drivers cd
              WHERE cd.carrier_company_id = da.carrier_company_id
                AND cd.driver_user_id    = da.driver_user_id
                AND cd.status            = 'ACTIVE'
         )
           AND da.status NOT IN ('CANCELLED', 'SUPERSEDED')
    ) violations;
    IF v_count = 0 THEN
        v_pass_count := v_pass_count + 1;
        RAISE NOTICE '  [PASS] No cross-company driver violations in dispatch_assignments';
    ELSE
        RAISE NOTICE '  [FAIL] % cross-company driver violation(s) detected', v_count;
    END IF;

    -- Check 28: No dispatch vehicle cross-company violation
    v_check_count := v_check_count + 1;
    SELECT COUNT(*) INTO v_count FROM (
        SELECT da.id
          FROM dispatch_assignments da
          JOIN vehicles v ON v.id = da.vehicle_id
         WHERE v.carrier_company_id != da.carrier_company_id
           AND da.status NOT IN ('CANCELLED', 'SUPERSEDED')
    ) violations;
    IF v_count = 0 THEN
        v_pass_count := v_pass_count + 1;
        RAISE NOTICE '  [PASS] No cross-company vehicle violations in dispatch_assignments';
    ELSE
        RAISE NOTICE '  [FAIL] % cross-company vehicle violation(s) detected', v_count;
    END IF;

    -- Check 29: Booking agreed price == dispatch agreed_amount (where linked)
    v_check_count := v_check_count + 1;
    SELECT COUNT(*) INTO v_count FROM (
        SELECT b.id
          FROM bookings b
          JOIN dispatch_assignments da ON da.id = b.dispatch_assignment_id
         WHERE ABS(b.estimated_price - da.agreed_amount) > 0.01
    ) violations;
    IF v_count = 0 THEN
        v_pass_count := v_pass_count + 1;
        RAISE NOTICE '  [PASS] Booking prices match dispatch agreed amounts';
    ELSE
        RAISE NOTICE '  [FAIL] % booking(s) with price mismatch vs dispatch', v_count;
    END IF;

    -- Check 30: No orphan dispatch (freight must exist)
    v_check_count := v_check_count + 1;
    SELECT COUNT(*) INTO v_count FROM (
        SELECT da.id FROM dispatch_assignments da
        LEFT JOIN freight f ON f.id = da.freight_id
        WHERE f.id IS NULL
    ) orphans;
    IF v_count = 0 THEN
        v_pass_count := v_pass_count + 1;
        RAISE NOTICE '  [PASS] No orphan dispatch assignments';
    ELSE
        RAISE NOTICE '  [FAIL] % orphan dispatch assignment(s) found', v_count;
    END IF;

    -- ─────────────────────────────────────────────────────────
    -- SECTION 6: Phase 1-5 backward compatibility
    -- ─────────────────────────────────────────────────────────
    RAISE NOTICE '';
    RAISE NOTICE '-- Section 6: Phase 1-5 backward compatibility';

    -- Check 31: carrier_companies table still intact
    v_check_count := v_check_count + 1;
    SELECT COUNT(*) INTO v_count
      FROM information_schema.tables
      WHERE table_name = 'carrier_companies' AND table_schema = 'public';
    IF v_count = 1 THEN
        v_pass_count := v_pass_count + 1;
        RAISE NOTICE '  [PASS] carrier_companies table intact (Phase 1)';
    ELSE
        RAISE NOTICE '  [FAIL] carrier_companies table missing';
    END IF;

    -- Check 32: company_memberships table still intact (Phase 2)
    v_check_count := v_check_count + 1;
    SELECT COUNT(*) INTO v_count
      FROM information_schema.tables
      WHERE table_name = 'company_memberships' AND table_schema = 'public';
    IF v_count = 1 THEN
        v_pass_count := v_pass_count + 1;
        RAISE NOTICE '  [PASS] company_memberships table intact (Phase 2)';
    ELSE
        RAISE NOTICE '  [FAIL] company_memberships table missing';
    END IF;

    -- Check 33: carrier_drivers table intact (Phase 4)
    v_check_count := v_check_count + 1;
    SELECT COUNT(*) INTO v_count
      FROM information_schema.tables
      WHERE table_name = 'carrier_drivers' AND table_schema = 'public';
    IF v_count = 1 THEN
        v_pass_count := v_pass_count + 1;
        RAISE NOTICE '  [PASS] carrier_drivers table intact (Phase 4)';
    ELSE
        RAISE NOTICE '  [FAIL] carrier_drivers table missing';
    END IF;

    -- Check 34: carrier_offers table intact (Phase 5)
    v_check_count := v_check_count + 1;
    SELECT COUNT(*) INTO v_count
      FROM information_schema.tables
      WHERE table_name = 'carrier_offers' AND table_schema = 'public';
    IF v_count = 1 THEN
        v_pass_count := v_pass_count + 1;
        RAISE NOTICE '  [PASS] carrier_offers table intact (Phase 5)';
    ELSE
        RAISE NOTICE '  [FAIL] carrier_offers table missing';
    END IF;

    -- Check 35: freight.carrier_award_offer_id still present (Phase 5)
    v_check_count := v_check_count + 1;
    SELECT COUNT(*) INTO v_count
      FROM information_schema.columns
      WHERE table_name = 'freight' AND column_name = 'carrier_award_offer_id';
    IF v_count = 1 THEN
        v_pass_count := v_pass_count + 1;
        RAISE NOTICE '  [PASS] freight.carrier_award_offer_id intact (Phase 5)';
    ELSE
        RAISE NOTICE '  [FAIL] freight.carrier_award_offer_id missing';
    END IF;

    -- ─────────────────────────────────────────────────────────
    -- SUMMARY
    -- ─────────────────────────────────────────────────────────
    RAISE NOTICE '';
    RAISE NOTICE '=== RESULT: %/% checks PASSED ===', v_pass_count, v_check_count;

    IF v_pass_count = v_check_count THEN
        RAISE NOTICE 'Phase 6 PostgreSQL verification: PASS';
    ELSE
        RAISE NOTICE 'Phase 6 PostgreSQL verification: FAIL (% check(s) failed)',
            v_check_count - v_pass_count;
        RAISE EXCEPTION 'Phase 6 PostgreSQL verification failed — % of % checks passed',
            v_pass_count, v_check_count;
    END IF;
END $$;
