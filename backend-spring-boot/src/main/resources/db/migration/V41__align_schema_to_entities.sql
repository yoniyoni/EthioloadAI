-- V41: Align DB schema to JPA entity definitions.
-- The entities were redesigned after the initial migration history was applied.
-- This migration adds missing columns, adds FK aliases, and fixes CHECK constraints
-- so the Hibernate @Table validation passes and all service layers work correctly.
-- All changes use ADD COLUMN IF NOT EXISTS / DROP CONSTRAINT IF EXISTS for idempotency.

-- ────────────────────────────────────────────────────────────────────────────
-- 1. FREIGHT — add columns introduced in the extended entity design
-- ────────────────────────────────────────────────────────────────────────────

ALTER TABLE freight
    ADD COLUMN IF NOT EXISTS service_type      VARCHAR(20)  NOT NULL DEFAULT 'intercity',
    ADD COLUMN IF NOT EXISTS price_type        VARCHAR(20)  NOT NULL DEFAULT 'negotiable',
    ADD COLUMN IF NOT EXISTS bid_deadline      TIMESTAMP,
    ADD COLUMN IF NOT EXISTS city              VARCHAR(255),
    ADD COLUMN IF NOT EXISTS pickup_area       VARCHAR(255),
    ADD COLUMN IF NOT EXISTS dropoff_area      VARCHAR(255),
    ADD COLUMN IF NOT EXISTS preferred_date    DATE,
    ADD COLUMN IF NOT EXISTS items_description TEXT,
    ADD COLUMN IF NOT EXISTS vehicle_type_needed VARCHAR(255);

-- Fix freight status CHECK: entities write lowercase via converter
ALTER TABLE freight DROP CONSTRAINT IF EXISTS freight_status_check;
ALTER TABLE freight DROP CONSTRAINT IF EXISTS freight_status_chk;
ALTER TABLE freight ADD CONSTRAINT freight_status_check
    CHECK (status IN ('posted','matched','in_transit','delivered','completed','cancelled',
                      'POSTED','MATCHED','IN_TRANSIT','DELIVERED','COMPLETED','CANCELLED'));

-- Fix material_type CHECK to allow more values the frontend uses
ALTER TABLE freight DROP CONSTRAINT IF EXISTS freight_material_type_check;
ALTER TABLE freight ADD CONSTRAINT freight_material_type_check
    CHECK (material_type IN ('grain','cement','construction','perishables','electronics',
                             'livestock','fuel','general','other',
                             'fragile','hazardous','refrigerated'));

-- Fix urgency_level CHECK
ALTER TABLE freight DROP CONSTRAINT IF EXISTS freight_urgency_level_check;
ALTER TABLE freight ADD CONSTRAINT freight_urgency_level_check
    CHECK (urgency_level IN ('low','normal','high','urgent','express'));

-- ────────────────────────────────────────────────────────────────────────────
-- 2. BIDS — entity uses different column names than the legacy schema
-- ────────────────────────────────────────────────────────────────────────────

-- Add FK alias column: entity uses cargo_request_id, DB has freight_id
ALTER TABLE bids ADD COLUMN IF NOT EXISTS cargo_request_id BIGINT;
-- Sync existing rows
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = current_schema() AND table_name = 'bids' AND column_name = 'freight_id'
    ) THEN
        UPDATE bids SET cargo_request_id = freight_id WHERE cargo_request_id IS NULL;
    END IF;
END $$;
-- Add FK constraint for the alias column
DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.table_constraints
    WHERE table_name='bids' AND constraint_name='fk_bids_cargo_request_id'
  ) THEN
    ALTER TABLE bids ADD CONSTRAINT fk_bids_cargo_request_id
        FOREIGN KEY (cargo_request_id) REFERENCES freight(id) ON DELETE CASCADE;
  END IF;
END$$;

-- Add new bid columns the entity needs
ALTER TABLE bids
    ADD COLUMN IF NOT EXISTS amount            NUMERIC(15,2),
    ADD COLUMN IF NOT EXISTS note              TEXT,
    ADD COLUMN IF NOT EXISTS available_datetime TIMESTAMP,
    ADD COLUMN IF NOT EXISTS ai_score          NUMERIC(5,4),
    ADD COLUMN IF NOT EXISTS is_recommended    BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS distance_km       NUMERIC(10,2),
    ADD COLUMN IF NOT EXISTS counter_amount    NUMERIC(15,2),
    ADD COLUMN IF NOT EXISTS counter_note      TEXT,
    ADD COLUMN IF NOT EXISTS counter_by        VARCHAR(10),
    ADD COLUMN IF NOT EXISTS counter_at        TIMESTAMP;

-- Sync amount from bid_amount for existing rows
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = current_schema() AND table_name = 'bids' AND column_name = 'bid_amount'
    ) THEN
        UPDATE bids SET amount = bid_amount WHERE amount IS NULL;
    END IF;
END $$;
ALTER TABLE bids ALTER COLUMN amount SET NOT NULL;
ALTER TABLE bids ALTER COLUMN amount SET DEFAULT 0;

-- Fix status constraint: entity uses lowercase + adds COUNTERED
ALTER TABLE bids DROP CONSTRAINT IF EXISTS bids_status_check;
ALTER TABLE bids ADD CONSTRAINT bids_status_check
    CHECK (status IN ('pending','accepted','rejected','expired','countered','withdrawn',
                      'PENDING','ACCEPTED','REJECTED','WITHDRAWN','EXPIRED','COUNTERED'));

-- ────────────────────────────────────────────────────────────────────────────
-- 3. BOOKINGS — entity uses different column names
-- ────────────────────────────────────────────────────────────────────────────

-- Add cargo_id alias (entity JoinColumn name is cargo_id, DB has freight_id)
ALTER TABLE bookings ADD COLUMN IF NOT EXISTS cargo_id BIGINT;
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = current_schema() AND table_name = 'bookings' AND column_name = 'freight_id'
    ) THEN
        UPDATE bookings SET cargo_id = freight_id WHERE cargo_id IS NULL;
    END IF;
END $$;
DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.table_constraints
    WHERE table_name='bookings' AND constraint_name='fk_bookings_cargo_id'
  ) THEN
    ALTER TABLE bookings ADD CONSTRAINT fk_bookings_cargo_id
        FOREIGN KEY (cargo_id) REFERENCES freight(id) ON DELETE CASCADE;
  END IF;
END$$;

-- Add new booking columns
ALTER TABLE bookings
    ADD COLUMN IF NOT EXISTS booking_status VARCHAR(20) NOT NULL DEFAULT 'pending',
    ADD COLUMN IF NOT EXISTS estimated_price NUMERIC(15,2),
    ADD COLUMN IF NOT EXISTS commission_fee  NUMERIC(15,2) NOT NULL DEFAULT 0;

-- Sync estimated_price from agreed_price for existing rows
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = current_schema() AND table_name = 'bookings' AND column_name = 'agreed_price'
    ) THEN
        UPDATE bookings SET estimated_price = agreed_price WHERE estimated_price IS NULL;
    END IF;
END $$;

-- Fix status constraint: entity uses lowercase
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = current_schema() AND table_name = 'bookings' AND column_name = 'status'
    ) THEN
        ALTER TABLE bookings DROP CONSTRAINT IF EXISTS bookings_status_check;
        ALTER TABLE bookings ADD CONSTRAINT bookings_status_check
            CHECK (status IN ('confirmed','pickup_scheduled','picked_up','in_transit',
                              'delivery_scheduled','delivered','completed','cancelled','disputed',
                              'CONFIRMED','PICKUP_SCHEDULED','PICKED_UP','IN_TRANSIT',
                              'DELIVERY_SCHEDULED','DELIVERED','COMPLETED','CANCELLED','DISPUTED',
                              'pending','accepted','PENDING','ACCEPTED'));
    END IF;
END $$;

ALTER TABLE bookings DROP CONSTRAINT IF EXISTS bookings_booking_status_check;
ALTER TABLE bookings ADD CONSTRAINT bookings_booking_status_check
    CHECK (booking_status IN ('pending','accepted','delivered','completed','confirmed','cancelled',
                              'PENDING','ACCEPTED','DELIVERED','COMPLETED','CONFIRMED','CANCELLED'));

-- ────────────────────────────────────────────────────────────────────────────
-- 4. TRIPS — entity has redesigned columns (trip_status, trip_type, jsonb route_data, etc.)
-- ────────────────────────────────────────────────────────────────────────────

ALTER TABLE trips
    ADD COLUMN IF NOT EXISTS trip_status    VARCHAR(20) NOT NULL DEFAULT 'ongoing',
    ADD COLUMN IF NOT EXISTS trip_type      VARCHAR(20) NOT NULL DEFAULT 'single',
    ADD COLUMN IF NOT EXISTS total_stops    INTEGER NOT NULL DEFAULT 1,
    ADD COLUMN IF NOT EXISTS completed_stops INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS destination    VARCHAR(255),
    ADD COLUMN IF NOT EXISTS route_data     JSONB NOT NULL DEFAULT '[]'::jsonb;

-- Populate destination from bookings/freight for existing rows
UPDATE trips t
SET destination = f.destination
FROM bookings b JOIN freight f ON b.cargo_id = f.id
WHERE t.booking_id = b.id AND t.destination IS NULL;

-- Fix trip status CHECK: entity uses ongoing/completed (lowercase)
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = current_schema() AND table_name = 'trips' AND column_name = 'status'
    ) THEN
        ALTER TABLE trips DROP CONSTRAINT IF EXISTS trips_status_check;
        ALTER TABLE trips ADD CONSTRAINT trips_status_check
            CHECK (status IN ('scheduled','in_progress','arrived','delivered','completed','cancelled','delayed',
                              'SCHEDULED','IN_PROGRESS','ARRIVED','DELIVERED','COMPLETED','CANCELLED','DELAYED',
                              'ongoing','ONGOING'));
    END IF;
END $$;

ALTER TABLE trips DROP CONSTRAINT IF EXISTS trips_trip_status_check;
ALTER TABLE trips ADD CONSTRAINT trips_trip_status_check
    CHECK (trip_status IN ('ongoing','completed','ONGOING','COMPLETED'));

-- ────────────────────────────────────────────────────────────────────────────
-- 5. TRIP_STOPS — entity uses cargo_request_id, stop_status, pickup_lat/lng
-- ────────────────────────────────────────────────────────────────────────────

ALTER TABLE trip_stops
    ADD COLUMN IF NOT EXISTS cargo_request_id BIGINT,
    ADD COLUMN IF NOT EXISTS stop_status      VARCHAR(20) NOT NULL DEFAULT 'pending',
    ADD COLUMN IF NOT EXISTS pickup_lat       NUMERIC(10,8),
    ADD COLUMN IF NOT EXISTS pickup_lng       NUMERIC(11,8);

-- Sync pickup_lat/lng from existing pickup_latitude/longitude columns
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = current_schema() AND table_name = 'trip_stops' AND column_name = 'pickup_latitude'
    ) THEN
        UPDATE trip_stops SET pickup_lat = pickup_latitude WHERE pickup_lat IS NULL AND pickup_latitude IS NOT NULL;
    END IF;
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = current_schema() AND table_name = 'trip_stops' AND column_name = 'pickup_longitude'
    ) THEN
        UPDATE trip_stops SET pickup_lng = pickup_longitude WHERE pickup_lng IS NULL AND pickup_longitude IS NOT NULL;
    END IF;
END $$;

ALTER TABLE trip_stops DROP CONSTRAINT IF EXISTS trip_stops_stop_status_check;
ALTER TABLE trip_stops ADD CONSTRAINT trip_stops_stop_status_check
    CHECK (stop_status IN ('pending','arrived','loaded','completed',
                           'PENDING','ARRIVED','LOADED','COMPLETED'));

-- ────────────────────────────────────────────────────────────────────────────
-- 6. PAYMENTS — entity redesigned with payment_status, commission_amount, etc.
-- ────────────────────────────────────────────────────────────────────────────

ALTER TABLE payments
    ADD COLUMN IF NOT EXISTS payment_status   VARCHAR(20) NOT NULL DEFAULT 'pending',
    ADD COLUMN IF NOT EXISTS commission_amount NUMERIC(15,2) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS driver_net_amount NUMERIC(15,2) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS paid_by          BIGINT,
    ADD COLUMN IF NOT EXISTS transaction_ref  VARCHAR(255);

-- Sync payment_status from existing status column
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = current_schema() AND table_name = 'payments' AND column_name = 'status'
    ) THEN
        UPDATE payments SET payment_status = LOWER(status) WHERE payment_status = 'pending' AND status != 'pending';
    END IF;
END $$;

ALTER TABLE payments DROP CONSTRAINT IF EXISTS payments_payment_status_check;
ALTER TABLE payments ADD CONSTRAINT payments_payment_status_check
    CHECK (payment_status IN ('pending','paid','failed','PENDING','PAID','FAILED'));
