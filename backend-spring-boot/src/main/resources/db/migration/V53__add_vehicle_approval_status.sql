-- V53: Add independent vehicle approval state.
-- Existing vehicles require explicit Admin review before bidding, so they start
-- UNDER_REVIEW rather than being implicitly approved.
ALTER TABLE vehicles
    ADD COLUMN IF NOT EXISTS approval_status VARCHAR(20) NOT NULL DEFAULT 'UNDER_REVIEW';

UPDATE vehicles
SET approval_status = 'UNDER_REVIEW'
WHERE approval_status IS NULL;

ALTER TABLE vehicles
    DROP CONSTRAINT IF EXISTS vehicles_approval_status_check;

ALTER TABLE vehicles
    ADD CONSTRAINT vehicles_approval_status_check
    CHECK (approval_status IN ('UNDER_REVIEW', 'APPROVED', 'REJECTED', 'SUSPENDED'));