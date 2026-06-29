# Access-token revocation via per-user epoch + refresh lifecycle caps

**Status:** accepted (2026-06-25) · robustness Tier 2c.

Stateless JWT access tokens cannot be individually revoked before expiry. This adds immediate, low-cost revocation of a user's access plus hard caps on refresh-token lifetime.

## Decision

- **Per-user token epoch.** Add `users.tokens_invalid_before` (Instant). The JWT filter rejects any access token whose `iat` is before the user's epoch. An admin **ban / forced-logout** sets `tokens_invalid_before = now` and deletes the user's refresh token(s) — instantly invalidating every outstanding access token for that user. The check is one comparison against an already-loaded user, keeping the stateless fast-path intact.
- **`jti` claim.** Access tokens gain a `jti` (UUID). Not consulted for revocation this iteration (the epoch covers "revoke a user"), but it makes a future per-token deny-list (with Redis, alongside multi-device 2b) cheap to add.
- **Refresh lifecycle caps.** Add an **absolute-lifetime cap** on the refresh family (default 30 days, `app.security.refresh.absolute-expiration`, configurable) enforced in `RefreshTokenService.refreshToken` regardless of rotation — past the cap, refresh is rejected/deleted and the user must re-authenticate. The existing 7-day (30-day prod) refresh expiry remains the **idle timeout** (it already lapses if unused and is not extended beyond the absolute cap).
- **Schema:** Flyway **V4** adds `users.tokens_invalid_before` and a refresh-family `created_at` / `absolute_expiry`.

## Why not a full jti deny-list now

A deny-list shines for revoking individual tokens/sessions — which is really the multi-device (2b) concern, deferred to the Redis iteration. The per-user epoch satisfies this iteration's actual requirement (admin immediate revoke + forced logout) with no per-token store to maintain and no Redis dependency. Adding `jti` now keeps the deny-list path open without paying for it yet.

## Consequences

- Admin revoke is effective immediately, not "within the access-token TTL."
- One extra per-request comparison; no new store, no Redis required.
- Sessions can no longer be renewed indefinitely — the absolute cap forces periodic re-auth.
- Pairs cleanly with 2b/Redis later: the epoch stays, a jti deny-list can be layered on for per-device revocation.
