CREATE TABLE company_memberships (
 id BIGSERIAL PRIMARY KEY,
 carrier_company_id BIGINT NOT NULL REFERENCES carrier_companies(id) ON DELETE CASCADE,
 user_id BIGINT NOT NULL REFERENCES users(id),
 role VARCHAR(30) NOT NULL CHECK (role IN ('CARRIER_OWNER','FLEET_MANAGER','DISPATCHER')),
 status VARCHAR(20) NOT NULL CHECK (status IN ('ACTIVE','INACTIVE','REMOVED')),
 joined_at TIMESTAMP NOT NULL,
 invited_by BIGINT REFERENCES users(id),
 role_changed_by BIGINT REFERENCES users(id),
 role_changed_at TIMESTAMP,
 status_changed_by BIGINT REFERENCES users(id),
 status_changed_at TIMESTAMP,
 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 CONSTRAINT uq_company_membership UNIQUE(carrier_company_id, user_id),
 CONSTRAINT ck_owner_membership_active CHECK (role <> 'CARRIER_OWNER' OR status = 'ACTIVE')
);
CREATE INDEX idx_membership_user ON company_memberships(user_id);
CREATE INDEX idx_membership_company_role_status ON company_memberships(carrier_company_id, role, status);
CREATE INDEX idx_membership_inviter ON company_memberships(invited_by);
CREATE INDEX idx_membership_role_actor ON company_memberships(role_changed_by);
CREATE INDEX idx_membership_status_actor ON company_memberships(status_changed_by);

