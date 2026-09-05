# Phase A — Baseline Verification

**Purpose:** Prove the current `dev` HEAD is fully green BEFORE any Phase B upgrade touches the pom.
Per Diangelo's two-phase mission: no upgrading on a broken foundation.

## Verdict: ✅ GREEN — cleared to proceed to Phase B

| Field | Value |
|---|---|
| Commit | `10e416da7c7a6ba7a06fe6d87a104aa010523f1a` (dev HEAD) |
| Tag | `v1.0.0` (rollback anchor) |
| Command | `./mvnw -B -ntp verify` |
| Runtime | JDK **21.0.11** (openjdk@21, Homebrew) — the repo's declared `maven.compiler.release=21` |
| Maven wrapper | 3.9.9 |
| Test DB | PostgreSQL 16-alpine on `localhost:5433` (`oauth_template_test` / `test` / `test`) via `docker/compose/docker-compose.test.yml` |
| Run at | 2026-09-05T12:54:26Z → 12:55:09Z |
| Wall time | 42.8 s (`real`); Maven-reported total 41.931 s |
| Build result | **BUILD SUCCESS** (exit 0) |

## Test results

| Phase | Plugin | Tests | Failures | Errors | Skipped |
|---|---|---|---|---|---|
| Unit + slice/context (`*Test`) | Surefire | **67** | 0 | 0 | 0 |
| Integration on real PostgreSQL (`*IT`) | Failsafe 3.5.5 | **33** | 0 | 0 | 0 |
| **Total** | | **100** | **0** | **0** | **0** |

**Pass rate: 100% (100/100).**

### Integration suite (real Postgres, :5433)
RefreshAbsoluteLifetimeIT, OAuth2SuccessHandlerCallbackIT, AdminRevokeSessionsIT, MfaDisableIT,
RoleProtectionPremiumIT, MfaRoleEnforcementIT, MfaEnrollmentIT, TokenRevocationIT, SessionEndpointsIT,
AuthFlowIT, RefreshTokenFlowIT, OAuth2ProviderLoginIT, RefreshTokenReuseIT, PWResetFlowIT,
MfaLoginChallengeIT, RoleProtectionModeratorIT, RoleProtectionAdminIT, SchemaValidationIT — all green.
(Note: MFA/TOTP + admin-revoke-sessions ITs present → Tier-2 security work has already landed on dev since the W26 CI plan.)

## Environment notes carried into Phase B

- **JDK:** machine default `java` on PATH is **25.0.2**; baseline was pinned to **21** deliberately so the
  baseline reflects the repo's declared runtime, not a false-red/false-green from a newer JDK. The Phase B
  Java-LTS-target decision (21 vs 25) is under grill.
- **Maven wrapper** is 3.9.9 (mission brief guessed 3.9.16) — Phase B wrapper target verified against the
  Apache Maven release page at upgrade time.
- **PostgreSQL JDBC** is pinned to 42.7.11 via the `<properties>` #25 security override, not the Boot BOM default.
- **#25 security overrides** in `<properties>` (jackson-bom 2.21.4, netty 4.1.135.Final, tomcat 10.1.55,
  postgresql 42.7.11) sit ABOVE the Boot 3.5.14 BOM defaults to clear Trivy HIGH/CRITICAL CVEs; Phase B
  re-evaluates each against the post-upgrade BOM.

## Log
Full build log: `<bridge-session-dir>/baseline-verify.log`.

_Written by /hl_dev_flow_v2 Stage 1 (Phase A gate). Baseline green → Phase B upgrades may proceed once the
upgrade-shape grill is answered._
