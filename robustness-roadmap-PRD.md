# PRD — Robustness roadmap (OAuth template, post-hardening)

**Source:** session a3be647d robustness review (2026-06-09), grounded in the code at `origin/dev`/`main` @ 13f98c0. Builds on the security-hardening pass (commit e003fa1) and the reuse-detection + typed-error pass (commit d8078a4).
**Stage:** backlog PRD — intended to be opened in a future cc-bridge session and built tier by tier.
**Status:** proposed. Nothing here is started.

## Problem Statement

The template now has a solid security foundation: OAuth + email/password auth, JWT-in-cookie + rotating refresh tokens hashed at rest, refresh-token reuse detection, anti-enumeration, CSRF protection, fail-fast JWT secret, proxy-aware rate limiting, RBAC, audit logging, i18n, health indicators, and typed error handling — all green on real PostgreSQL. It is released to `test` and `main`.

What it is **not yet** is operationally robust or feature-complete as a reusable auth backend. There is no automated build/test gate (a red commit can reach `main`), no MFA, only one of four OAuth providers is wired, sessions are single-device, and all rate-limit/session state is in-memory per-instance (blocks horizontal scaling). This PRD captures the prioritized work to close those gaps.

## Solution

Ship the gaps as independent, vertically-sliced feature branches into `dev`, in priority tiers. Each tier is independently valuable and independently shippable; later tiers assume earlier ones but do not hard-require them. CI/CD comes first because it makes every subsequent change safe to land.

## Already done (do NOT re-do — verified in code)

CSRF (`CookieCsrfTokenRepository`), DB + Auth health indicators, SHA-256 token hashing at rest, refresh-token reuse detection (`previous_token`, V2 migration), anti-enumeration on register/resend/forgot-password, fail-fast JWT secret (`@PostConstruct`, ≥32 bytes), proxy-aware rate limiting (5-attempt threshold, self-evicting map), typed exception hierarchy → HTTP status + i18n. These are the foundation this PRD builds on, not work items.

## Tiers & User Stories

### Tier 1 — CI/CD (do first; cheap, highest leverage)

1. As a maintainer, I want every PR to `dev` to run `./mvnw verify` against a PostgreSQL service container in GitHub Actions, so that a failing build/test can never be merged.
2. As a maintainer, I want the workflow to cache Maven dependencies, so that CI is fast enough to gate every PR.
3. As a maintainer, I want Dependabot enabled for Maven + GitHub Actions, so that dependency updates are surfaced automatically.
4. As a maintainer, I want a dependency-vulnerability scan (OWASP dependency-check or Trivy) in CI, so that known-vulnerable libraries fail the build.
5. As a maintainer, I want a branch-protection-compatible status check on `dev`, so that the green gate is enforced, not advisory.

### Tier 2 — Security depth

**2a. MFA / TOTP (two-factor auth)**
6. As a user with a password account, I want to enrol an authenticator app via QR code, so that my account requires a second factor.
7. As a user, I want one-time recovery codes at enrolment, so that I can regain access if I lose my device.
8. As a user, I want login to require my TOTP code when MFA is enabled, so that a stolen password alone is insufficient.
9. As a user, I want to disable MFA after re-authenticating, so that I stay in control of my security settings.

**2b. Multi-device sessions (per-session refresh tokens)**
10. As a user, I want to be logged in on multiple devices at once, so that logging in on my phone doesn't kick out my laptop. (Today: one refresh-token row per user — devices evict each other.)
11. As a user, I want to see my active sessions and revoke a single device, so that I can sign out a lost device without touching the others.
12. As a maintainer, I want reuse detection scoped per session/family rather than per user, so that one compromised device doesn't force-logout every device.

**2c. Access-token revocation + lifecycle caps**
13. As an admin, I want to revoke a user's access immediately (ban/forced-logout), so that a stateless JWT can't remain valid until its 30-min expiry. (Needs short-lived token + deny-list or a server-side session check.)
14. As a maintainer, I want a refresh-token absolute-lifetime cap and idle timeout, so that sessions can't be renewed indefinitely.

### Tier 3 — Completeness & operations

**3a. Distributed state (Redis)**
15. As an operator, I want rate-limit (and session) state in Redis instead of in-memory, so that I can run more than one replica without per-instance gaps. (Pairs with 2b.)

**3b. Finish OAuth providers**
16. As a user, I want to sign in with GitHub, Facebook, and Apple, not just Google, so that I can use my preferred identity. (Others are configured-but-inactive in `SecurityConfig`.)
17. As a maintainer, I want Apple client-secret rotation automated, so that the Apple secret (a JWT expiring ≤6 months) doesn't silently break sign-in.

**3c. Observability**
18. As an operator, I want structured JSON logs with correlation IDs, so that I can trace a request across the auth flow.
19. As an operator, I want a Prometheus metrics dashboard + alerts on auth-failure/lockout spikes, so that abuse is detected early.

## Implementation Decisions

- **CI platform: GitHub Actions** (not Azure Pipelines). ADR-0002 currently records "no CI/CD for now"; this PRD reverses that — supersede ADR-0002 with a new ADR (e.g. 0004) when Tier 1 lands. Use a `postgres:16-alpine` **service container** mirroring the existing test DB (`:5433`, `oauth_template_test`, `test`/`test`), and run `./mvnw -B verify`. Surefire (`*Test`) + Failsafe (`*IT`) already split unit/integration.
- **MFA**: add a `totp_secret` + `mfa_enabled` + recovery-code store on the user (or a dedicated table). Use a vetted TOTP lib; never store recovery codes in plaintext (hash like other secrets). New Flyway migration (V3+).
- **Multi-device sessions**: replace the single-row-per-user refresh model with a `refresh_tokens`-per-session table keyed by a session/family id; carry the existing `previous_token` reuse-detection semantics down to family granularity. New Flyway migration; update `RefreshTokenService` + repository. This is the deepest, highest-risk change — gate behind integration tests like the existing `RefreshTokenReuseIT`.
- **Access-token revocation**: prefer a lightweight deny-list (jti) checked on each request, or shorten access-token TTL further and lean on refresh. Keep stateless-fast path; only consult the store on revocation checks.
- **Redis**: introduce `spring-data-redis`; move the rate-limit map and (with 2b) session lookups behind an interface so in-memory stays the default for single-instance/dev and Redis activates by profile/env.
- **Providers**: wire GitHub/Facebook/Apple `ClientRegistration`s + `SecurityConfig`; Apple needs the ES256 client-secret-JWT generator + a scheduled rotation job.
- **No behaviour regressions**: every tier keeps the existing suite green on real Postgres; new behaviour ships with new tests first (TDD), same flow as the rest of the repo.

## Testing Decisions

- A good test asserts **external behaviour** — an auth flow returns the right tokens/status, MFA blocks a code-less login, a revoked device's refresh token is rejected — not implementation details.
- The existing Testcontainers/`*IT` pattern (`BaseIntegrationTest`, `AuthFlowIT`, `RefreshTokenReuseIT`, `RoleProtection*IT`) is the regression net and the template for new ITs.
- Tier 1 (CI) is itself the meta-test: once green, it enforces every later tier's tests automatically.
- Each feature is TDD: red test first, then implement, then `./mvnw verify` green on real Postgres before the feature→dev PR.

## Out of Scope (for this PRD)

- Azure infra conversion (`azure/`) — still stale, still flagged.
- Any frontend/consuming-app UI for MFA enrolment or session management (this is a backend template; expose APIs only).
- Passwordless/WebAuthn/passkeys — a possible future PRD, not this one.
- Account-takeover ML/anomaly detection beyond simple rate-limit + lockout + alerting.

## Further Notes

- **Sequencing**: Tier 1 first — it protects everything after it. Then Tier 2a (MFA) or 2b (multi-device) by operator priority (security-surface vs UX/scale). Redis (3a) pairs naturally with multi-device (2b).
- **ADR follow-ups**: landing Tier 1 supersedes ADR-0002; multi-device sessions and access-token revocation each warrant their own ADR (token-model change, revocation strategy).
- **Branch/promotion policy unchanged**: feature/* → PR → `dev`; `test`/`main` remain Diangelo-gated (the `.claude/settings.json` deny rules stay in place; promotions happen out-of-band as with the 13f98c0 release).
