# STATUS — OAuth-PostgreSQL robustness roadmap (session 6a699698, 2026-06-29)

## Where things stand right now

The prior session's "working tree resets at turn boundaries" blocker is **gone** in this
session — edits to existing files now persist through to commits/PRs. Wave 8 is being cleared.

### Just shipped this session
- **#48** Multi-device session endpoints (`GET/DELETE /api/user/sessions`): `sid` claim on the
  access token, `SessionController` + `SessionDto`, `RefreshTokenService.listSessions`, ITs.
  PR #58 — CI green on real Postgres — **merged to `dev`** (cac1400).
- **#49** Redis Tier 3a foundation: `RateLimitStore` abstraction + `CaffeineRateLimitStore`
  default (pure refactor, no behaviour change). 63 unit tests green locally (JDK 21).
  PR **#59 OPEN** — awaiting CI, then self-merge.

## Already on `dev` (from earlier sessions), all CI-green on real Postgres
- **Tier 1 CI/CD:** #15–#19 (`./mvnw verify` on postgres:16, Maven cache, Dependabot, Trivy gate,
  enforced required check). Repo on A-D-Labs. ADR-0004.
- **Tier 2 security:** MFA/TOTP #28–#32 (ADR-0005); revocation #33–#35 (ADR-0006).
- **Deps:** #25 Spring Boot 3.5.14 + overrides → 34 CVEs cleared, Trivy green.
- **Tier 2b multi-device foundation:** #47 per-session refresh tokens, Flyway V6, reuse detection,
  `revokeSession()`. ADR-0007.

## Remaining open work
- **#50** Redis Wave 9: profile-activated Redis-backed `RateLimitStore` (`bucket4j-redis`),
  depends on #49's abstraction. ADR-0008. Next up once #59 merges.

## Local build note (for future sessions)
Default local JDK is **25**, which Lombok can't compile (project targets 21). Use
`export JAVA_HOME=/opt/homebrew/Cellar/openjdk@21/21.0.11/libexec/openjdk.jdk/Contents/Home`
before `./mvnw`. CI uses JDK 21, so PRs verify correctly regardless.
