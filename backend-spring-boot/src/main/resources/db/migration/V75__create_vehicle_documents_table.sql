-- V75: Create vehicle_documents table for private vehicle document management.
CREATE TABLE vehicle_documents (
    id BIGSERIAL PRIMARY KEY,
    vehicle_id BIGINT NOT NULL REFERENCES vehicles(id) ON DELETE CASCADE,
    document_type VARCHAR(50) NOT NULL CHECK (document_type IN (
        'registration_certificate',
        'insurance_certificate',
        'roadworthiness_certificate',
        'ownership_document'
    )),
    document_label VARCHAR(100) NOT NULL,
    file_path VARCHAR(500) NOT NULL,
    original_name VARCHAR(255) NOT NULL,
    mime_type VARCHAR(100) NOT NULL,
    file_size BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'pending' CHECK (status IN ('pending', 'approved', 'rejected')),
    rejection_reason TEXT,
    reviewed_by BIGINT REFERENCES users(id) ON DELETE SET NULL,
    reviewed_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_vehicle_document_type UNIQUE (vehicle_id, document_type)
);

CREATE INDEX idx_vehicle_documents_vehicle_id ON vehicle_documents (vehicle_id);
CREATE INDEX idx_vehicle_documents_status ON vehicle_documents (status);
CREATE INDEX idx_vehicle_documents_reviewed_by ON vehicle_documents (reviewed_by);
