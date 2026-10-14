-- Preserve owner_id as the immutable Phase 1 compatibility reference.
-- Idempotent data backfill; new companies create their membership in the application transaction.
INSERT INTO company_memberships(carrier_company_id, user_id, role, status, joined_at, created_at, updated_at)
SELECT c.id, c.owner_id, 'CARRIER_OWNER', 'ACTIVE', c.created_at, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM carrier_companies c
WHERE NOT EXISTS (
 SELECT 1 FROM company_memberships m WHERE m.carrier_company_id = c.id AND m.user_id = c.owner_id
);

