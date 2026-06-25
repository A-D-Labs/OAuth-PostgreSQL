# PRD — Tier 2b (multi-device sessions) + Tier 3a (Redis rate-limit)

**Epic:** robustness (final deferred slice) · **Sprint:** `sprint:2026-W26`
**feat:** `feat:multi-device-sessions`, `feat:redis` · **Branch:** `feature/tier2b-3a-multidevice-redis` → `dev`
**Plan:** `.planning/tier2b-3a-multidevice-redis.md` · **ADRs:** 0007 (multi-device), 0008 (Redis boundary)
**Status:** specced (stage 2). Scope + design grilled 2026-06-25 (cc-bridge d164a75e).

## Problem

A user gets exactly one refresh token, so a second login evicts the first — no concurrent devices, no per-device visibility/revocation. Rate-limit state is in-memory per-instance, blocking horizontal scaling. This iteration closes both, keeping sessions durable in Postgres and making Redis an opt-in rate-limit backend.

## Goals

- Concurrent logins across devices (one session each), with list + per-device revoke.
- Reuse detection scoped per session (a compromised device doesn't force-logout the others).
- Rate-limit state movable to Redis (profile-activated) for multi-replica deploys, in-memory by default.
- **No performance regression** (operator condition): stateless JWT fast-path unchanged; Redis off the auth-critical path; default profile in-memory.

## Non-goals (this iteration)

- 3b finish OAuth providers + Apple secret rotation (next iteration).
- Moving session state into Redis (rejected — ADR-0008).
- Observability (Tier 3c).

## Feature A — Multi-device sessions (`feat:multi-device-sessions`)

### S1 — Token model migration V6 + entity/repo rewrite (foundation)
- **AC1** Flyway **V6**: `refresh_tokens` gains `session_id` (unique, not null), `user_agent`, `ip_address`, `last_used_at`; the one-row-per-user constraint on `user_id` is dropped (now many rows per user). `token` stays unique; `previous_token` + `created_at` (from #35) retained.
- **AC2** `RefreshToken` → `@ManyToOne User`; `RefreshTokenRepository` exposes `List<RefreshToken> findByUser`, `findBySessionId`, `deleteBySessionId`. `ddl-auto: validate` passes on real Postgres.
- **AC3** No behaviour change to other features (compiles, existing suite adapted/green).

### S2 — Per-session generate/rotate
- **AC1** Each login creates a NEW session row (new `session_id`, captured device metadata) instead of overwriting the user's row.
- **AC2** Rotation, per-session reuse detection (`previous_token`), and the absolute-lifetime cap (#35) all operate within a single session; `last_used_at` updated on refresh.
- **AC3** A stolen-token replay revokes only that session (not every device); `RefreshTokenReuseIT` passes at session granularity.

### S3 — List my sessions
- **AC1** `GET /api/user/sessions` returns the authenticated user's active sessions with device metadata (user-agent, IP, created/last-used), the current session flagged.
- **AC2** No raw token material is exposed.

### S4 — Revoke a single device
- **AC1** `DELETE /api/user/sessions/{sessionId}` (authenticated) deletes that session; other sessions keep working.
- **AC2** A user cannot revoke another user's session (404/403). Audited.

## Feature B — Redis rate-limit (`feat:redis`)

### S5 — RateLimitStore abstraction + Caffeine default (refactor)
- **AC1** `RateLimitService` works through a `RateLimitStore` interface; default implementation is Caffeine in-memory.
- **AC2** Pure refactor — no behaviour change; existing `RateLimitServiceTest` + rate-limit ITs stay green; `test` profile stays in-memory.

### S6 — Redis-backed store, profile-activated
- **AC1** A `redis` profile/property switches the store to a Redis-backed bucket implementation (`bucket4j-redis`).
- **AC2** With no Redis configured (default/test), the app runs exactly as today (in-memory). Documented how to enable.
- **AC3** Redis is never on the JWT validation path; default-profile latency unchanged.

## Testing

- New `*IT` on real Postgres: two concurrent logins both valid; list shows both; revoking one leaves the other working; per-session reuse detection; absolute cap per session.
- Existing suite adapted to the new model and kept green; rate-limit refactor proven behaviour-preserving.
- TDD throughout; `./mvnw -B verify` green (CI-gated) before each feature→dev PR.

## Rollout

Per-issue PRs into `dev` (CI-gated). Order: S1 (foundation) → S2 → S3/S4; S5 → S6 (parallelizable with the session work — different modules). `#25` already merged. `test`/`main` promotions remain Diangelo-gated.
