-- V15: Create the platform_settings table and seed initial values.
--
-- Key-value store for admin-configurable platform parameters.
-- key:   unique string identifier (e.g. 'pricing.rate_min')
-- value: text representation of the setting value (parsed by application layer)
--
-- Initial seed values (matching Laravel reference):
--   pricing.rate_min              = 18   ETB/km/ton minimum rate
--   pricing.rate_max              = 28   ETB/km/ton maximum rate
--   pricing.intracity_rate_min    = 50   ETB/km intracity minimum
--   pricing.intracity_rate_max    = 100  ETB/km intracity maximum
--   pricing.commission_rate       = 0.10 platform commission (10%)
--   pricing.fleet_commission_rate = 0.05 fleet-owner commission (5%)

CREATE TABLE platform_settings (
    id         BIGSERIAL    PRIMARY KEY,
    setting_key        VARCHAR(255) NOT NULL UNIQUE,
    value      TEXT,
    created_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_platform_settings_key ON platform_settings (setting_key);

-- Seed default values. H2 compatible: use INSERT with explicit IDs.
INSERT INTO platform_settings (id, setting_key, value, created_at, updated_at) VALUES
    (1, 'pricing.rate_min',              '18',   CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (2, 'pricing.rate_max',              '28',   CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (3, 'pricing.intracity_rate_min',    '50',   CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (4, 'pricing.intracity_rate_max',    '100',  CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (5, 'pricing.commission_rate',       '0.10', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (6, 'pricing.fleet_commission_rate', '0.05', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
