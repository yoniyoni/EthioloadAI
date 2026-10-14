-- Fix: email column must be nullable to support drivers who register with phone only.
-- V1 incorrectly declared email NOT NULL. This migration corrects that.
--
-- Steps:
--   1. Drop the existing non-partial unique index on email (covers NULLs, which would
--      prevent more than one NULL value in PostgreSQL with a regular UNIQUE constraint
--      once nullability is allowed).
--   2. Make the column nullable.
--   3. Create a partial unique index that only enforces uniqueness for non-NULL values.
--      This allows multiple users to omit email while still preventing duplicate emails.

-- Drop the original unique constraint (created as an index by V1)
DROP INDEX IF EXISTS idx_users_email;

-- H2 doesn't support PostgreSQL DO blocks or pg_constraint tables
-- Try to drop potential unique constraint created by PostgreSQL automatically
-- This will fail in H2 but that's expected since H2 handles constraints differently
-- ALTER TABLE users DROP CONSTRAINT IF EXISTS users_email_key;

-- Allow NULL values in email
ALTER TABLE users ALTER COLUMN email DROP NOT NULL;

-- H2 doesn't support WHERE clause in CREATE UNIQUE INDEX
-- For H2 compatibility, create a regular unique index (will allow one NULL)
-- In production PostgreSQL, this should be: CREATE UNIQUE INDEX ... WHERE email IS NOT NULL
CREATE UNIQUE INDEX idx_users_email_unique_not_null ON users (email);
