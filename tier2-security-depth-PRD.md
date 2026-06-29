# PRD — Tier 2: Security depth (MFA + access-token revocation)

**Epic:** robustness (Tier 2 of 3, iteration 1) · **Sprint:** `sprint:2026-W26`
**feat:** `feat:mfa`, `feat:token-revocation` · **Branch:** `feature/tier2-security-depth` → `dev`
**Plan:** `.planning/tier2-security-depth.md` · **ADRs:** 0005 (MFA), 0006 (revocation/lifecycle)
**Status:** specced (stage 2). Grilled 2026-06-25 (cc-bridge d164a75e).

## Problem

A stolen password (or hijacked social-IdP session) currently grants full access, and a stateless JWT stays valid until expiry with no way for an admin to revoke it immediately. Refresh tokens can also be renewed indefinitely. This iteration closes those gaps with MFA and server-side revocation + lifecycle caps. Multi-device sessions (2b) are deferred to the Redis iteration.

## Goals

- TOTP-based MFA for **all** account types, opt-in and **role-mandatory enforceable** (default `ADMIN`).
- Immediate, low-cost revocation of a user's access (admin ban / forced-logout).
- Hard caps on refresh-token lifetime (absolute + idle), no indefinite renewal.
- No regression for users without MFA; every change TDD-tested on real Postgres behind the CI gate.

## Non-goals (this iteration)

- 2b multi-device / per-session tokens + Redis (next iteration).
- WebAuthn/passkeys, SMS/email OTP (TOTP only).
- TOTP-secret encryption-at-rest (follow-up).
- Per-token jti deny-list (deferred with 2b/Redis; `jti` claim is added now to enable it later).

## Feature A — MFA / TOTP (`feat:mfa`)

### S1 — MFA data model + migration V3
- **AC1** Flyway V3 adds `users.mfa_enabled` (bool, default false), `users.totp_secret` (nullable), and `mfa_recovery_codes` (id, user_id, code_hash, used_at).
- **AC2** `ddl-auto: validate` passes against the entities on real Postgres.

### S2 — Enrol + activate (QR + recovery codes)
- **AC1** `POST /auth/mfa/enroll` (authenticated) generates a TOTP secret, returns an `otpauth://` provisioning URI (for QR) and a one-time set of recovery codes (shown once).
- **AC2** Recovery codes are stored hashed (SHA-256 via `TokenHasher`), single-use.
- **AC3** `POST /auth/mfa/activate` verifies a current TOTP code and sets `mfa_enabled = true`. MFA is not active until activation succeeds.

### S3 — Login challenge flow (LOCAL + social)
- **AC1** When MFA is active, `AuthService` (password) and `OAuth2SuccessHandler` (social) do NOT issue JWT+refresh directly — they issue a short-lived one-time **MFA challenge token**.
- **AC2** `POST /auth/mfa/verify` accepts a valid TOTP code OR an unused recovery code and then issues the real JWT + refresh cookies; a used recovery code is marked used.
- **AC3** A wrong/expired code does not issue a session; failures are rate-limited and audited.
- **AC4** Users without MFA are unaffected (single-step login as today).

### S4 — Disable after re-auth
- **AC1** `POST /auth/mfa/disable` requires re-authentication (fresh password/TOTP) and clears `totp_secret`, recovery codes, and `mfa_enabled`.
- **AC2** Audited as a security event.

### S5 — Role-mandatory enforcement + forced enrolment
- **AC1** `app.security.mfa.required-roles` (default `[ADMIN]`) is configurable.
- **AC2** A user holding a required role without active MFA receives, at login, a restricted **enrol-only** token that permits only the enrol/activate endpoints — no full session until MFA is active.
- **AC3** Once enrolled, the user proceeds through the normal challenge flow.

## Feature B — Access-token revocation + lifecycle (`feat:token-revocation`)

### S6 — jti + token epoch + filter check
- **AC1** Access-token JWTs gain a `jti` (UUID) claim; `iat` retained.
- **AC2** `users.tokens_invalid_before` (Instant, migration V4) added.
- **AC3** `JwtAuthenticationFilter` rejects any access token whose `iat` < the user's `tokens_invalid_before`.
- **AC4** Normal tokens (issued after the epoch) validate unchanged.

### S7 — Admin immediate-revoke endpoint
- **AC1** An admin-only endpoint sets a target user's `tokens_invalid_before = now` and deletes their refresh token(s).
- **AC2** After revoke, the target's existing access tokens are rejected on the next request; refresh is rejected too. Audited.

### S8 — Refresh absolute-lifetime cap + idle timeout
- **AC1** A refresh family records its origin time (migration V4); `app.security.refresh.absolute-expiration` (default 30 days) caps total age regardless of rotation.
- **AC2** `RefreshTokenService.refreshToken` rejects + deletes a token past the absolute cap (re-auth required).
- **AC3** The existing refresh expiry remains the idle timeout (unused → lapses); reuse-detection semantics unchanged.

## Testing

- New `*IT` integration tests on real Postgres (the regression net): MFA-enabled login is blocked without a code and succeeds with one; recovery code is single-use; social-login MFA gate; role-forced enrolment; revoked user's access+refresh rejected; refresh past absolute cap rejected.
- Existing suite stays green (no regression for non-MFA users). TDD: red test first, then implement, then `./mvnw -B verify` green before each feature→dev PR (CI-gated).

## Rollout

Per-issue PRs into `dev` (CI-gated). Suggested order: S1 → S2 → S3 → S4 → S5 (MFA), and S6 → S7 → S8 (revocation) can proceed in parallel with MFA since they touch different code (JWT filter / refresh service vs MFA endpoints). `test`/`main` promotions remain Diangelo-gated.
