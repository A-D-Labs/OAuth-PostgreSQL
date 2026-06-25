# Plan — Tier 2: Security depth (MFA + access-token revocation)

**Sprint:** `sprint:2026-W26` · **feat slugs:** `feat:mfa`, `feat:token-revocation` · **Epic:** robustness (Tier 2 of 3, iteration 1)
**Source:** `robustness-roadmap-PRD.md` Tier 2 + stage-8 feedback iteration 1 · grilled 2026-06-25 (cc-bridge d164a75e).
**Status:** plan locked (stage 1 complete). Next: `/to-prd`.

## Scope decision (locked in grill)

This iteration ships **2a (MFA/TOTP)** + **2c (access-token revocation + lifecycle caps)**. **2b (multi-device sessions) is DEFERRED** to a later iteration paired with Redis (Tier 3a), because it is the PRD's flagged deepest/highest-risk change (RefreshToken @OneToOne -> @OneToMany rewrite, breaks the documented "one Refresh Token per User" invariant).

## Decisions locked in grill (2026-06-25)

| # | Decision | Outcome |
|---|----------|---------|
| Scope | Which Tier-2 sub-features | 2a (MFA) + 2c (revocation + lifecycle); defer 2b. |
| MFA — accounts | LOCAL only vs all | **ALL accounts** (LOCAL + social). Challenge gate inserted in BOTH the password-login path (`AuthService`) and the OAuth2 success path (`OAuth2SuccessHandler`). |
| MFA — policy | Opt-in vs enforced | **Opt-in per user AND role-mandatory enforceable** (configurable required-roles, default `ADMIN`). A user holding a required role without MFA is forced to enrol before getting a full session. |
| MFA — flow | Login protocol | Two-step: credentials/OAuth OK -> if MFA enabled, issue a short-lived one-time **MFA challenge token** -> `POST /auth/mfa/verify` with TOTP (or recovery) code -> real JWT + refresh issued. |
| MFA — storage | Secrets at rest | TOTP secret stored server-side (base32; must be recoverable to verify — encryption-at-rest noted as a follow-up). **Recovery codes hashed** at rest (reuse `TokenHasher` SHA-256), one-time use. |
| Revocation | Strategy | **Per-user `tokensInvalidBefore` epoch** + add a **`jti`** claim to access tokens. Admin ban/forced-logout sets the epoch = now and deletes refresh token(s); JWT filter rejects access tokens with `iat < tokensInvalidBefore`. Instant, cheap, Redis-ready. No full jti deny-list this iteration. |
| Lifecycle caps | Refresh limits | **Absolute-lifetime cap** on the refresh family (default 30 days, configurable) enforced regardless of rotation; existing 7-day refresh expiry stays as the **idle timeout**. |

## Domain-model / terminology additions (CONTEXT.md updated this branch)

- **TOTP Secret** — per-user shared secret for time-based one-time passwords (RFC 6238).
- **Recovery Code** — one-time backup code (hashed at rest) to pass the MFA challenge when the authenticator is unavailable.
- **MFA Challenge** — the short-lived intermediate state between first-factor success and second-factor verification; not a full session.
- **Token Epoch (`tokensInvalidBefore`)** — per-user instant before which all access tokens are considered revoked.
- **Absolute Lifetime** — the hard ceiling on a refresh family's age, independent of rotation/idle timeout.

The CONTEXT.md "Refresh Token" entry keeps "one active Refresh Token per User" for now (2b deferred); a note flags that 2b will revise it.

## Technical approach

- **Migrations:** V3 (MFA: `users.mfa_enabled`, `users.totp_secret`, `mfa_recovery_codes` table) and V4 (revocation/lifecycle: `users.tokens_invalid_before`, `refresh_tokens.absolute_expiry` / family `created_at`).
- **TOTP lib:** a vetted library (e.g. `dev.samstevens.totp`) for secret gen, QR provisioning URI, and code verification with a small time-step window.
- **MFA endpoints:** `POST /auth/mfa/enroll` (returns QR/otpauth URI + recovery codes once), `POST /auth/mfa/activate` (verify a code to turn it on), `POST /auth/mfa/verify` (challenge step at login), `POST /auth/mfa/disable` (after re-auth). Role-forced enrolment uses a restricted "enrol-only" token.
- **JWT changes:** add `jti` (UUID) + keep `iat`; `JwtAuthenticationFilter` compares `iat` to the user's `tokensInvalidBefore`.
- **Admin revoke:** `AdminController` endpoint to set a user's epoch + drop refresh tokens (audited).
- **Refresh caps:** `RefreshTokenService.refreshToken` enforces absolute-lifetime; reject + delete past the cap.
- **No behaviour regressions:** users without MFA log in exactly as today; every change ships TDD with new `*IT` tests on real Postgres, green before each feature->dev PR (now gated by CI).

## Issue slicing preview (finalised in stage 3)

`feat:mfa`: (1) MFA data model + migration V3; (2) enrol + activate (QR + recovery codes); (3) login challenge flow for LOCAL + social; (4) disable-after-reauth; (5) role-mandatory enforcement + forced enrolment.
`feat:token-revocation`: (6) jti + `tokensInvalidBefore` + filter check; (7) admin immediate-revoke endpoint; (8) refresh absolute-lifetime cap + idle timeout.

ADRs: 0005 (MFA), 0006 (access-token revocation + lifecycle).

## Out of scope (this iteration)

- 2b multi-device sessions + Redis (next iteration).
- WebAuthn/passkeys, SMS/email OTP (TOTP only).
- TOTP-secret encryption-at-rest (follow-up; stored server-side for now).
- Per-token jti deny-list (deferred with 2b/Redis).
