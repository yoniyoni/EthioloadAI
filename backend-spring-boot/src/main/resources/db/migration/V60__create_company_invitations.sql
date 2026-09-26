CREATE TABLE company_invitations (
 id BIGSERIAL PRIMARY KEY,
 carrier_company_id BIGINT NOT NULL REFERENCES carrier_companies(id) ON DELETE CASCADE,
 invited_email VARCHAR(255) NOT NULL,
 invited_role VARCHAR(30) NOT NULL CHECK (invited_role IN ('FLEET_MANAGER','DISPATCHER')),
 token_hash VARCHAR(64) NOT NULL UNIQUE,
 status VARCHAR(20) NOT NULL CHECK (status IN ('PENDING','ACCEPTED','EXPIRED','REVOKED')),
 invited_by BIGINT NOT NULL REFERENCES users(id),
 expires_at TIMESTAMP NOT NULL,
 accepted_at TIMESTAMP,
 accepted_by BIGINT REFERENCES users(id),
 revoked_at TIMESTAMP,
 revoked_by BIGINT REFERENCES users(id),
 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 CONSTRAINT ck_invitation_email CHECK (invited_email = lower(trim(invited_email)) AND length(invited_email) > 3),
 CONSTRAINT ck_invitation_hash CHECK (token_hash ~ '^[a-f0-9]{64}$'),
 CONSTRAINT ck_invitation_expiry CHECK (expires_at > created_at),
 CONSTRAINT ck_invitation_acceptance CHECK (
   (status = 'ACCEPTED' AND accepted_at IS NOT NULL AND accepted_by IS NOT NULL)
   OR (status <> 'ACCEPTED' AND accepted_at IS NULL AND accepted_by IS NULL)),
 CONSTRAINT ck_invitation_revocation CHECK (
   (status = 'REVOKED' AND revoked_at IS NOT NULL AND revoked_by IS NOT NULL)
   OR (status <> 'REVOKED' AND revoked_at IS NULL AND revoked_by IS NULL))
);
-- Expired pending rows are retired transactionally when issuing a replacement.
CREATE UNIQUE INDEX uq_company_pending_invitation ON company_invitations(carrier_company_id, invited_email)
 WHERE status = 'PENDING';
CREATE INDEX idx_invitation_company_created ON company_invitations(carrier_company_id, created_at DESC, id DESC);
CREATE INDEX idx_invitation_email ON company_invitations(invited_email);
CREATE INDEX idx_invitation_status_expiry ON company_invitations(status, expires_at);
CREATE INDEX idx_invitation_inviter ON company_invitations(invited_by);
CREATE INDEX idx_invitation_acceptor ON company_invitations(accepted_by);
CREATE INDEX idx_invitation_revoker ON company_invitations(revoked_by);

