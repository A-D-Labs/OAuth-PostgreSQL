-- V7 — Provider rationalization: Google + Microsoft only (PRD #62, ADR-0009).
--
-- The template previously carried half-wired scaffolding for four social providers
-- (Google, Spotify, Apple, SoundCloud) but only Google was actually wired into the
-- login flow. This migration rationalizes the persisted provider surface to the two
-- providers the template now supports — GOOGLE and MICROSOFT — by:
--   * dropping the unused spotify_id / apple_id / soundcloud_id columns + unique constraints,
--   * adding microsoft_id + its unique constraint (mirrors google_id),
--   * narrowing the primary_provider CHECK to the rationalized set.
--
-- Destructive column drops are intentional and safe: this is a data-less template.
-- V1 is already applied and immutable, hence a forward migration rather than an edit.

ALTER TABLE users DROP CONSTRAINT IF EXISTS uk_users_spotify;
ALTER TABLE users DROP CONSTRAINT IF EXISTS uk_users_apple;
ALTER TABLE users DROP CONSTRAINT IF EXISTS uk_users_soundcloud;

ALTER TABLE users DROP COLUMN IF EXISTS spotify_id;
ALTER TABLE users DROP COLUMN IF EXISTS apple_id;
ALTER TABLE users DROP COLUMN IF EXISTS soundcloud_id;

ALTER TABLE users ADD COLUMN microsoft_id VARCHAR(255);
ALTER TABLE users ADD CONSTRAINT uk_users_microsoft UNIQUE (microsoft_id);

-- Narrow the primary_provider check to the rationalized provider set.
ALTER TABLE users DROP CONSTRAINT IF EXISTS ck_users_primary_provider;
ALTER TABLE users ADD CONSTRAINT ck_users_primary_provider
    CHECK (primary_provider IN ('GOOGLE', 'MICROSOFT', 'LOCAL'));
