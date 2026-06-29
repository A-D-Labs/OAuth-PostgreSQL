-- Tier 2b: multi-device sessions. One refresh_tokens row per device/session instead of per user.
-- Drop the one-row-per-user uniqueness; add a session id + device metadata.
ALTER TABLE refresh_tokens DROP CONSTRAINT uk_refresh_token_user;

ALTER TABLE refresh_tokens ADD COLUMN session_id   VARCHAR(64);
ALTER TABLE refresh_tokens ADD COLUMN user_agent   VARCHAR(512);
ALTER TABLE refresh_tokens ADD COLUMN ip_address   VARCHAR(64);
ALTER TABLE refresh_tokens ADD COLUMN last_used_at TIMESTAMP(6) WITH TIME ZONE;

-- Backfill a session id for any pre-existing row so the NOT NULL + UNIQUE can be added.
UPDATE refresh_tokens SET session_id = gen_random_uuid()::text WHERE session_id IS NULL;
ALTER TABLE refresh_tokens ALTER COLUMN session_id SET NOT NULL;
ALTER TABLE refresh_tokens ADD CONSTRAINT uk_refresh_token_session UNIQUE (session_id);

-- A user now has many rows; index the FK for "list my sessions".
CREATE INDEX idx_refresh_token_user ON refresh_tokens (user_id);
