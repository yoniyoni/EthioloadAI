-- Create freight table matching Laravel schema
CREATE TABLE IF NOT EXISTS freight (
    id BIGSERIAL PRIMARY KEY,
    shipper_id BIGINT NOT NULL,
    pickup_location VARCHAR(255) NOT NULL,
    pickup_latitude DECIMAL(10, 7),
    pickup_longitude DECIMAL(10, 7),
    destination VARCHAR(255) NOT NULL,
    destination_latitude DECIMAL(10, 7),
    destination_longitude DECIMAL(10, 7),
    material_type VARCHAR(50) NOT NULL CHECK (material_type IN ('grain', 'cement', 'construction', 'perishables', 'electronics', 'livestock', 'fuel', 'general', 'other')),
    cargo_description TEXT,
    weight_tons DECIMAL(10, 2) NOT NULL CHECK (weight_tons >= 0.1),
    volume_m3 DECIMAL(10, 2),
    budget DECIMAL(15, 2) NOT NULL CHECK (budget >= 1),
    distance_km DECIMAL(10, 2),
    deadline DATE,
    urgency_level VARCHAR(20) NOT NULL CHECK (urgency_level IN ('low', 'normal', 'high', 'urgent')),
    status VARCHAR(20) NOT NULL DEFAULT 'posted' CHECK (status IN ('posted', 'matched', 'in_transit', 'delivered', 'completed', 'cancelled')),
    matched_driver_id BIGINT,
    matched_vehicle_id BIGINT,
    matched_price DECIMAL(15, 2),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_freight_shipper_id FOREIGN KEY (shipper_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_freight_matched_driver_id FOREIGN KEY (matched_driver_id) REFERENCES users(id) ON DELETE SET NULL,
    CONSTRAINT fk_freight_matched_vehicle_id FOREIGN KEY (matched_vehicle_id) REFERENCES vehicles(id) ON DELETE SET NULL
);

-- Create indexes for performance
CREATE INDEX idx_freight_shipper_id ON freight(shipper_id);
CREATE INDEX idx_freight_status ON freight(status);
CREATE INDEX idx_freight_material_type ON freight(material_type);
CREATE INDEX idx_freight_matched_driver_id ON freight(matched_driver_id) WHERE matched_driver_id IS NOT NULL;
CREATE INDEX idx_freight_matched_vehicle_id ON freight(matched_vehicle_id) WHERE matched_vehicle_id IS NOT NULL;
CREATE INDEX idx_freight_deadline ON freight(deadline) WHERE deadline IS NOT NULL;
CREATE INDEX idx_freight_pickup_location ON freight(pickup_location);
CREATE INDEX idx_freight_destination ON freight(destination);
