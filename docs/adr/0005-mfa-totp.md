# MFA via TOTP — all accounts, opt-in + role-mandatory (esp. ADMIN)

**Status:** accepted (2026-06-25) · robustness Tier 2a.

Adds a second authentication factor (TOTP, RFC 6238) to the template. A stolen password (or a compromised social-IdP session) is no longer sufficient on its own for accounts that have MFA.

## Decision

- **Applies to all account types** (LOCAL email+password AND social/OAuth). The second-factor gate is inserted in both token-issuing paths: `AuthService.authenticateAndGenerateTokens` (password) and `OAuth2SuccessHandler` (social).
- **Opt-in per user, and role-mandatory enforceable.** A configurable `app.security.mfa.required-roles` (default `[ADMIN]`) forces MFA for holders of those roles: at login such a user without MFA gets a restricted **enrol-only** token and must complete enrolment before receiving a full session.
- **Two-step login flow.** First factor OK -> if MFA active, issue a short-lived one-time **MFA challenge token** (not a session) -> `POST /auth/mfa/verify` with a TOTP or recovery code -> real JWT + refresh cookies issued.
- **Enrolment** (`POST /auth/mfa/enroll`) returns an `otpauth://` provisioning URI (for a QR code) and a one-time set of **recovery codes**; `POST /auth/mfa/activate` verifies a code to turn MFA on. `POST /auth/mfa/disable` requires re-authentication.
- **Secrets at rest:** the TOTP secret is stored server-side as base32 (it must be recoverable to verify codes; encryption-at-rest is a noted follow-up). **Recovery codes are hashed** (SHA-256 via the existing `TokenHasher`) and single-use, matching how verification/refresh/reset tokens are already stored.
- **Schema:** Flyway **V3** adds `users.mfa_enabled`, `users.totp_secret`, and an `mfa_recovery_codes` table.

## Consequences

- Users without MFA log in exactly as before — no regression.
- Social-login users can now carry app-level MFA; the OAuth success path becomes a challenge-aware branch rather than always issuing tokens.
- Role-mandatory MFA introduces a forced-enrolment state (restricted token) — a deliberate UX cost for privileged accounts.
- TOTP-only this iteration; WebAuthn/passkeys and SMS/email OTP remain out of scope (see `robustness-roadmap-PRD.md`).
