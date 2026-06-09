-- Reuse detection: remember the hash of the token we just rotated away so that a later
-- replay of it can be recognized as a stolen-token reuse and trigger family revocation.
ALTER TABLE refresh_tokens ADD COLUMN previous_token VARCHAR(64);
