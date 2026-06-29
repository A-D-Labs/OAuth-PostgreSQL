-- Tier 2c: refresh-token absolute-lifetime cap. Records the family's origin time so a token
-- can be rejected once it exceeds the absolute lifetime, regardless of rotation/idle timeout.
ALTER TABLE refresh_tokens ADD COLUMN created_at TIMESTAMP(6) WITH TIME ZONE;
