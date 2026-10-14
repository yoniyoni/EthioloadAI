-- Phase 1 only. Existing users and fleet relationships are unchanged.
CREATE TABLE carrier_companies (
    id BIGSERIAL PRIMARY KEY,
    company_name VARCHAR(255) NOT NULL CHECK (length(trim(company_name)) > 0),
    legal_name VARCHAR(255),
    tin_number VARCHAR(50),
    business_registration_number VARCHAR(100),
    phone VARCHAR(20),
    email VARCHAR(255),
    business_address VARCHAR(1000),
    city VARCHAR(100),
    owner_id BIGINT NOT NULL REFERENCES users(id),
    verification_status VARCHAR(40) NOT NULL DEFAULT 'DRAFT',
    review_message VARCHAR(2000),
    reviewed_by BIGINT REFERENCES users(id),
    reviewed_at TIMESTAMP,
    submitted_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_carrier_tin UNIQUE (tin_number),
    CONSTRAINT uq_carrier_registration UNIQUE (business_registration_number),
    CONSTRAINT ck_carrier_tin_normalized CHECK (tin_number IS NULL OR
        (tin_number = upper(trim(tin_number)) AND length(tin_number) > 0)),
    CONSTRAINT ck_carrier_registration_normalized CHECK (business_registration_number IS NULL OR
        (business_registration_number = upper(trim(business_registration_number)) AND length(business_registration_number) > 0)),
    CONSTRAINT ck_carrier_status CHECK (verification_status IN
        ('DRAFT', 'PENDING', 'APPROVED', 'REJECTED', 'MORE_INFORMATION_REQUIRED')),
    CONSTRAINT ck_carrier_no_self_review CHECK (reviewed_by IS NULL OR reviewed_by <> owner_id),
    CONSTRAINT ck_carrier_submission CHECK (verification_status = 'DRAFT' OR submitted_at IS NOT NULL),
    CONSTRAINT ck_carrier_review CHECK (
        (verification_status IN ('DRAFT', 'PENDING') AND reviewed_by IS NULL AND reviewed_at IS NULL AND review_message IS NULL)
        OR (verification_status IN ('APPROVED', 'REJECTED', 'MORE_INFORMATION_REQUIRED')
            AND reviewed_by IS NOT NULL AND reviewed_at IS NOT NULL)),
    CONSTRAINT ck_carrier_review_reason CHECK (verification_status NOT IN ('REJECTED', 'MORE_INFORMATION_REQUIRED')
        OR (review_message IS NOT NULL AND length(trim(review_message)) > 0))
);
CREATE INDEX idx_carrier_owner_created ON carrier_companies(owner_id, created_at DESC, id DESC);
CREATE INDEX idx_carrier_status_created ON carrier_companies(verification_status, created_at DESC, id DESC);
CREATE INDEX idx_carrier_created ON carrier_companies(created_at DESC, id DESC);
CREATE INDEX idx_carrier_reviewer ON carrier_companies(reviewed_by);

CREATE TABLE carrier_company_documents (
    id BIGSERIAL PRIMARY KEY,
    carrier_company_id BIGINT NOT NULL REFERENCES carrier_companies(id),
    document_type VARCHAR(40) NOT NULL,
    storage_key VARCHAR(500) NOT NULL UNIQUE,
    original_name VARCHAR(255) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    file_size BIGINT NOT NULL CHECK (file_size > 0 AND file_size <= 5242880),
    verification_status VARCHAR(40) NOT NULL DEFAULT 'DRAFT',
    review_message VARCHAR(2000),
    reviewed_by BIGINT REFERENCES users(id),
    reviewed_at TIMESTAMP,
    uploaded_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_carrier_document_type UNIQUE (carrier_company_id, document_type),
    CONSTRAINT ck_carrier_document_type CHECK (document_type IN
        ('BUSINESS_LICENSE', 'BUSINESS_REGISTRATION', 'TIN_CERTIFICATE', 'OWNER_IDENTIFICATION', 'OTHER')),
    CONSTRAINT ck_carrier_document_status CHECK (verification_status IN
        ('DRAFT', 'PENDING', 'APPROVED', 'REJECTED', 'MORE_INFORMATION_REQUIRED')),
    CONSTRAINT ck_carrier_document_content_type CHECK (content_type IN ('application/pdf', 'image/jpeg', 'image/png')),
    CONSTRAINT ck_carrier_document_review CHECK (
        (verification_status IN ('DRAFT', 'PENDING') AND reviewed_by IS NULL AND reviewed_at IS NULL AND review_message IS NULL)
        OR (verification_status IN ('APPROVED', 'REJECTED', 'MORE_INFORMATION_REQUIRED')
            AND reviewed_by IS NOT NULL AND reviewed_at IS NOT NULL)),
    CONSTRAINT ck_carrier_document_reason CHECK (verification_status NOT IN ('REJECTED', 'MORE_INFORMATION_REQUIRED')
        OR (review_message IS NOT NULL AND length(trim(review_message)) > 0))
);
-- uq_carrier_document_type also indexes company document lookups.
CREATE INDEX idx_carrier_document_reviewer ON carrier_company_documents(reviewed_by);

