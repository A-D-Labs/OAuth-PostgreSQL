# Plan — Tier 2b (multi-device sessions) + Tier 3a (Redis rate-limit)

**Sprint:** `sprint:2026-W26` · **feat slugs:** `feat:multi-device-sessions`, `feat:redis` · **Epic:** robustness (final deferred slice)
**Source:** `robustness-roadmap-PRD.md` Tier 2b + 3a · stage-8 feedback iteration 2 ("do the next natural steps, run the full hl-dev-flow").
**Status:** plan locked (stage 1 complete; scope + design grilled via cc-bridge). Next: `/to-prd`.

## Scope decision (locked in grill)

This iteration ships **2b (multi-device sessions)** + **3a (Redis for rate-limit state)**, plus the already-merged **#25** (Spring Boot 3.5.14 CVE remediation). **3b (finish OAuth providers)** is deferred to a future iteration.

## Design decision (locked: Option A + performance guarantee)

- **Sessions live in PostgreSQL, not Redis.** Each login creates its own `refresh_tokens` row (a *Session*) keyed by a `session_id`, with device metadata. Durable across restarts; "list my sessions" is a plain SQL query.
- **Redis is used ONLY for rate-limit buckets**, behind the existing cache abstraction — **Caffeine in-memory by default**, Redis activated by profile/property. The `test` profile stays in-memory (ADR-0003: the suite must run on the headless box without Redis).
- **Performance guarantee (operator condition):** no regression.
  - Access-token validation stays on the **stateless JWT fast-path** — no DB/Redis lookup per request beyond the per-user epoch check already shipped in #33.
  - Refresh-token rows are queried only on `POST /refresh-token` (not a hot path), via an indexed `token` / `session_id` lookup.
  - Rate-limit default remains in-process Caffeine (zero added latency); Redis is opt-in for multi-replica deploys.
  - Multi-device adds one indexed row per active session; "list sessions" is per-user indexed.

## Domain-model change (breaks an invariant — ADR + CONTEXT update)

Today: `RefreshToken` is `@OneToOne` User — **one active Refresh Token per User**. 2b changes this to **many per user**: `@ManyToOne` User + a `session_id` + device metadata (user-agent, IP, created/last-used). Reuse detection stays **per-session** (the existing `previous_token` semantics are already per-row, so they carry down to session granularity cleanly). This supersedes the CONTEXT.md "one Refresh Token per User" note and warrants **ADR-0007**. Redis boundary is **ADR-0008**.

## Technical approach

- **Migration V6:** `refresh_tokens` gains `session_id` (unique), `user_agent`, `ip_address`, `created_at` already exists (#35), `last_used_at`; drop the one-row-per-user uniqueness on `user_id` (now many rows per user). Keep `token` unique, `previous_token` per-row.
- **Entity/repo:** `RefreshToken` → `@ManyToOne User`; `RefreshTokenRepository` gains `findByUser` → `List`, `findBySessionId`, `deleteBySessionId`. A new `Session` view/DTO for listing.
- **Service rewrite:** `RefreshTokenService.generateRefreshToken` creates a NEW row per login (new session_id) instead of reusing the user's single row; `refreshToken` rotates within the session and keeps absolute-lifetime + reuse detection per session; `revokeAllForUser` still deletes all; add `revokeSession(user, sessionId)`.
- **Endpoints:** `GET /api/user/sessions` (list my active sessions w/ device metadata, current flagged), `DELETE /api/user/sessions/{sessionId}` (revoke one device). Admin revoke (#34) still nukes all + sets epoch.
- **Redis (3a):** introduce a `RateLimitStore` abstraction; default Caffeine; `redis` profile/property switches to a Redis-backed bucket store (bucket4j-redis already in pom). Health/优雅 fallback. No behaviour change in default/test.
- **No behaviour regression:** existing single-device tests adapt (a user can still have a session); every change TDD with `*IT` on real Postgres, green before each feature→dev PR (CI-gated). The reuse-detection IT (`RefreshTokenReuseIT`) must stay green at session granularity.

## Issue slicing preview (finalised in stage 3)

`feat:multi-device-sessions`: (1) token-model migration V6 + entity/repo rewrite (foundation); (2) per-session generate/rotate in `RefreshTokenService` (reuse detection + absolute cap per session); (3) list-my-sessions endpoint; (4) revoke-single-device endpoint.
`feat:redis`: (5) `RateLimitStore` abstraction + Caffeine default (refactor, no behaviour change); (6) Redis-backed store activated by profile + docs.

ADRs: 0007 (multi-device token model), 0008 (Redis rate-limit boundary).

## Out of scope (this iteration)

- 3b finish OAuth providers + Apple secret rotation (next iteration).
- Moving session state itself into Redis (Option B rejected — durability + queryability).
- Observability (Tier 3c).
