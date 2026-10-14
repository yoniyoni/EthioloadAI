-- V6: Add missing business fields to the freight table.
--
-- The Spring Boot freight table was created with the core fields only.
-- These columns are required for the bidding system (price_type, bid_deadline),
-- intracity service support (service_type, city, pickup_area, dropoff_area,
-- preferred_date, items_description, vehicle_type_needed).
--
-- All new columns are nullable or have defaults so existing rows are unaffected.
-- H2 compatible syntax: add columns individually with constraints

ALTER TABLE freight ADD COLUMN service_type VARCHAR(20) NOT NULL DEFAULT 'intercity';
ALTER TABLE freight ADD CONSTRAINT ck_freight_service_type CHECK (service_type IN ('intercity', 'intracity'));

ALTER TABLE freight ADD COLUMN price_type VARCHAR(20) NOT NULL DEFAULT 'negotiable';
ALTER TABLE freight ADD CONSTRAINT ck_freight_price_type CHECK (price_type IN ('negotiable', 'fixed'));

ALTER TABLE freight ADD COLUMN bid_deadline TIMESTAMP;
ALTER TABLE freight ADD COLUMN city VARCHAR(255);
ALTER TABLE freight ADD COLUMN pickup_area VARCHAR(255);
ALTER TABLE freight ADD COLUMN dropoff_area VARCHAR(255);
ALTER TABLE freight ADD COLUMN preferred_date DATE;
ALTER TABLE freight ADD COLUMN items_description TEXT;
ALTER TABLE freight ADD COLUMN vehicle_type_needed VARCHAR(255);

-- Index for intracity city-based browsing (H2 compatible - no WHERE clause)
CREATE INDEX idx_freight_city ON freight (city);
CREATE INDEX idx_freight_service_type ON freight (service_type);
CREATE INDEX idx_freight_price_type ON freight (price_type);
CREATE INDEX idx_freight_bid_deadline ON freight (bid_deadline);
