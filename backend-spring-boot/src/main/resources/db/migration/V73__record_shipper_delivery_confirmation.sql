ALTER TABLE trips
    ADD COLUMN shipper_delivery_confirmed_by BIGINT
        REFERENCES users(id) ON DELETE RESTRICT,
    ADD COLUMN shipper_delivery_confirmed_at TIMESTAMP,
    ADD CONSTRAINT ck_trip_shipper_delivery_confirmation
        CHECK ((shipper_delivery_confirmed_by IS NULL) = (shipper_delivery_confirmed_at IS NULL));
