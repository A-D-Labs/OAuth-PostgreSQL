-- Tier 2c: per-user access-token revocation epoch. Any access token whose iat is
-- before this instant is rejected by the JWT filter (admin ban / forced-logout).
ALTER TABLE users ADD COLUMN tokens_invalid_before TIMESTAMP(6) WITH TIME ZONE;
