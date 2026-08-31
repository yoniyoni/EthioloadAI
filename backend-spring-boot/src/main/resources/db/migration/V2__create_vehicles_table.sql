-- Create vehicles table matching Laravel schema
CREATE TABLE IF NOT EXISTS vehicles (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    fleet_owner_id BIGINT,
    truck_type VARCHAR(255) NOT NULL,
    vehicle_category VARCHAR(50) CHECK (vehicle_category IN ('heavy', 'light')),
    plate_number VARCHAR(255) NOT NULL UNIQUE,
    capacity DOUBLE PRECISION NOT NULL,
    current_city VARCHAR(255),
    latitude DECIMAL(10, 7),
    longitude DECIMAL(10, 7),
    last_location_at TIMESTAMP,
    availability_status VARCHAR(50) NOT NULL DEFAULT 'available',
    rating DECIMAL(3, 2),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_vehicles_user_id FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_vehicles_fleet_owner_id FOREIGN KEY (fleet_owner_id) REFERENCES users(id) ON DELETE SET NULL
);

-- Create indexes for performance
CREATE INDEX idx_vehicles_user_id ON vehicles(user_id);
CREATE INDEX idx_vehicles_fleet_owner_id ON vehicles(fleet_owner_id);
CREATE INDEX idx_vehicles_plate_number ON vehicles(plate_number);
CREATE INDEX idx_vehicles_current_city ON vehicles(current_city);
CREATE INDEX idx_vehicles_availability_status ON vehicles(availability_status);
CREATE INDEX idx_vehicles_location ON vehicles(latitude, longitude);
