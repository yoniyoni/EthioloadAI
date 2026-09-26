-- V12: Create the driver_documents table.
--
-- Supports the driver KYC (Know Your Customer) document verification workflow.
--
-- document_type values (5 required types):
--   license              — Driver's driving license
--   national_id          — National ID or Kebele ID
--   vehicle_registration — Vehicle registration certificate
--   insurance            — Insurance certificate
--   tin                  — Tax Identification Number certificate
--
-- Workflow:
--   1. Driver uploads document → status = 'pending'
--   2. Admin reviews → status = 'approved' or 'rejected' (with rejection_reason)
--   3. When all 5 types are approved → driver.is_active = true (application layer)
--
-- unique(user_id, document_type): one document slot per type per driver.
-- Re-uploading replaces the existing row for that type (application handles upsert).
--
-- file_path: server-side storage path. Never expose raw storage paths in API responses.
-- original_name: the filename the driver gave to the upload (for display only).

CREATE TABLE driver_documents (
    id               BIGSERIAL PRIMARY KEY,
    user_id          BIGINT        NOT NULL REFERENCES users(id)  ON DELETE CASCADE,
    document_type    VARCHAR(50)   NOT NULL
        CHECK (document_type IN ('license', 'national_id', 'vehicle_registration', 'insurance', 'tin')),
    file_path        VARCHAR(500)  NOT NULL,
    original_name    VARCHAR(255)  NOT NULL,
    status           VARCHAR(20)   NOT NULL DEFAULT 'pending'
        CHECK (status IN ('pending', 'approved', 'rejected')),
    rejection_reason TEXT,
    reviewed_by      BIGINT                 REFERENCES users(id)  ON DELETE SET NULL,
    reviewed_at      TIMESTAMP,
    created_at       TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- One active document record per type per driver
    CONSTRAINT driver_docs_user_type_unique UNIQUE (user_id, document_type)
);

CREATE INDEX idx_driver_docs_user_id     ON driver_documents (user_id);
CREATE INDEX idx_driver_docs_status      ON driver_documents (status);
-- Admin review queue: all pending documents (H2 compatible - no WHERE clause)
CREATE INDEX idx_driver_docs_pending ON driver_documents (status, created_at);
