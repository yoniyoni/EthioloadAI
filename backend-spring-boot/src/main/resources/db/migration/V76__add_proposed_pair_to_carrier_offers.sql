ALTER TABLE carrier_offers
    ADD COLUMN proposed_driver_user_id BIGINT REFERENCES users(id),
    ADD COLUMN proposed_vehicle_id BIGINT REFERENCES vehicles(id);

CREATE INDEX idx_carrier_offers_proposed_driver
    ON carrier_offers (proposed_driver_user_id)
    WHERE proposed_driver_user_id IS NOT NULL;

CREATE INDEX idx_carrier_offers_proposed_vehicle
    ON carrier_offers (proposed_vehicle_id)
    WHERE proposed_vehicle_id IS NOT NULL;
