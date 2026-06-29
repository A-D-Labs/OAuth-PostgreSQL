# Multi-device sessions — per-session refresh tokens (supersedes "one Refresh Token per User")

**Status:** accepted (2026-06-25) · robustness Tier 2b.

The template stored exactly one `refresh_tokens` row per user (`@OneToOne`), so logging in on a second device evicted the first. This ADR moves to **one refresh-token row per session**, enabling concurrent devices, per-device visibility, and per-device revocation.

## Decision

- `RefreshToken` becomes `@ManyToOne User` with a unique **`session_id`** and device metadata (`user_agent`, `ip_address`, `created_at`, `last_used_at`). A fresh login creates a NEW session row instead of overwriting the user's single row. Migration **V6** adds the columns and drops the one-row-per-user constraint.
- **Reuse detection stays per session.** The existing `previous_token` rotation/replay semantics are already per-row, so a stolen-token replay revokes only that session's family — not every device (an improvement the PRD explicitly called for).
- **Sessions live in PostgreSQL** (durable across restarts; "list my sessions" is a plain indexed query). See ADR-0008 for why Redis is NOT used for session state.
- New endpoints: `GET /api/user/sessions` (list active sessions with device metadata, current session flagged) and `DELETE /api/user/sessions/{sessionId}` (revoke one device). The admin force-logout (#34) and the per-user revocation epoch (#33) still apply across all sessions.
- The refresh absolute-lifetime cap (#35) and idle timeout now apply **per session**.

## Performance

No regression (the operator's condition for approving this): access-token validation stays on the stateless JWT fast-path (only the per-user epoch check from #33, no per-request session lookup); session rows are read only on `POST /refresh-token` and the (infrequent) list-sessions call, both indexed. Multi-device adds one indexed row per active login.

## Consequences

- Supersedes the CONTEXT.md "one active Refresh Token per User" statement.
- Concurrent logins coexist; a lost device can be revoked without touching others.
- `RefreshTokenService` and `RefreshTokenRepository` are rewritten; existing reuse-detection and absolute-lifetime tests are re-expressed at session granularity and must stay green.
