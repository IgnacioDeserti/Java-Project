-- Supports "Sign in with Google": such accounts have no local password, and we track
-- which provider created the account (a user can still add a local password later via
-- the existing forgot-password flow, so this column is informational, not exclusive).

ALTER TABLE users ALTER COLUMN password DROP NOT NULL;

ALTER TABLE users ADD COLUMN auth_provider VARCHAR(20) NOT NULL DEFAULT 'LOCAL';
