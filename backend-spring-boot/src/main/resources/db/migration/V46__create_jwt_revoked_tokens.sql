-- Persist only hashes of revoked JWTs, bounded by each token's expiration.
CREATE TABLE jwt_revoked_tokens (
    token_hash VARCHAR(64) PRIMARY KEY,
    expires_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_jwt_revoked_tokens_expires_at
    ON jwt_revoked_tokens (expires_at);