# PRD — Stack upgrade to latest stable (runtime + dependencies)

- **Date:** 2026-09-05
- **Sprint:** `sprint:2026-W36`
- **Feature slug:** `stack-upgrade` (`feat:stack-upgrade`)
- **Driver:** hl_dev_flow_v2, cc-bridge session `714bba31`
- **Baseline:** dev HEAD `10e416d` / tag `v1.0.0` — Phase A verified GREEN (`.planning/BASELINE.md`)
- **Version intelligence:** `.planning/PHASE-B-VERSIONS.md` (authoritative Maven Central data)
- **Decision log:** `DECISIONS-LOG.md`

## Problem

The backend's runtime and dependency set have drifted behind current stable releases. Java 21, Spring
Boot 3.5.14, and several pinned libraries (jjwt 0.12.6, springdoc 2.8.5, bucket4j 8.0.1, caffeine 3.2.0,
PostgreSQL JDBC 42.7.11, Lombok 1.18.36) are no longer latest. Staying behind accrues CVE exposure and
upgrade debt, and the `<properties>` block carries hand-maintained CVE overrides (#25) that may now be
redundant against a newer Boot BOM. We want to move to the latest **stable** runtime + dependencies
without destabilising a currently-green, security-hardened auth backend.

## Goal

Bring the stack to latest stable — **Java 25 LTS**, **Spring Boot 3.5.16**, and latest-stable pinned
deps — with each upgrade proven green by `./mvnw verify` before it merges to `dev`, while keeping the
Trivy CVE gate clean and preserving all existing behaviour (auth flows, refresh rotation + reuse
detection, MFA, RBAC, i18n, rate limiting). Spring Boot 4 adoption is scoped but **gated** behind an
explicit go/no-go, not taken automatically.

## Current state

- Java 21; Spring Boot 3.5.14; Maven wrapper 3.9.9; maven-compiler-plugin 3.13.0; jib 3.4.4.
- Explicit pins: jjwt 0.12.6, springdoc 2.8.5, bucket4j 8.0.1, caffeine 3.2.0, totp 1.7.1, lombok 1.18.36.
- `<properties>` #25 CVE overrides above the Boot BOM: jackson-bom 2.21.4, netty 4.1.135.Final,
  tomcat 10.1.55, postgresql 42.7.11.
- 100 tests green (67 unit/slice + 33 integration on real PostgreSQL). CI on `dev` requires a green
  `build & test` check on PR merges (ADR-0004). Backend-only; no UI, no Node tooling.

## Desired state

- Java **25 LTS** runtime (jib base image `25-jre`, Lombok 1.18.48, JDK25-capable Mockito, compiler
  plugin 3.16.0).
- Spring Boot **3.5.16**; Maven wrapper **3.9.16** (Maven 4 still RC → skipped).
- PostgreSQL JDBC **42.7.13**; jjwt **0.13.0**; springdoc **2.9.0**; bucket4j **8.10.1**; caffeine **3.2.4**.
- #25 overrides pruned to only what the new BOM doesn't already satisfy, staying within SB-3.5-compatible
  series (netty 4.1.x, tomcat 10.1.x — NOT the 4.2/11.x SB4 lines). Trivy stays HIGH/CRITICAL-clean.
- A committed **Spring Boot 4 breaking-change scan** with a go/no-go recommendation (adoption deferred to
  an approved follow-up, not done in this sprint unless Diangelo says go).
- All 100 tests still green on the final stack; behaviour unchanged.

## Functional requirements

FR1. Every dependency/runtime change lands as its own PR to `dev` and merges only when `./mvnw verify`
is green on the feature branch (real PostgreSQL, unit + integration).
FR2. One issue per major-version boundary; trivial patch bumps grouped into a single housekeeping issue.
FR3. Java target moves to 25 with all coupled enablers (Lombok, Mockito, compiler plugin, jib base image).
FR4. Spring Boot moves to latest stable 3.5.x (3.5.16); springdoc stays on its SB3-compatible 2.x line (2.9.0).
FR5. #25 CVE overrides are re-evaluated against the resulting BOM: redundant ones removed, necessary ones
kept within compatible series; result verified CVE-clean.
FR6. Spring Boot 4 is investigated via a breaking-change scan producing a written go/no-go; no SB4 merge
without explicit approval.

## Non-functional requirements

- **Stable-only:** no `-RC`/`-M`/`-SNAPSHOT`/`-alpha`/`-beta` artifacts adopted.
- **No behavioural regression:** the 100-test suite is the contract; it must stay green.
- **Security posture preserved or improved:** Trivy HIGH/CRITICAL gate stays clean; auth-critical libs
  (jjwt) upgraded with extra scrutiny.
- **Reversibility:** `v1.0.0` tag is the rollback anchor; each PR is independently revertible.
- **Observability:** no reduction in Actuator/Prometheus/health surface.

## Constraints

- `dev`-only; `test`/`main`/`master` are deny-listed and untouched. `test → main` would be HITL anyway.
- A-D-Labs branch protection: feature→dev PRs need the green CI check.
- Leave Diangelo's stash (`pre-v2-upgrade-session 2026-09-05`) untouched.
- Every Phase B change edits `pom.xml` (and the wrapper touches `.mvn/`), so upgrades are a mostly
  **sequential** verify-gated ladder, not parallel — to avoid pom merge conflicts.

## Assumptions

- Spring Boot 3.5.16 supports JDK 25 (confirmed at PR time via release notes).
- The A-D-Labs CI runner can run on/target JDK 25 (CI workflow's `setup-java` bumped alongside the Java issue).
- The docker test-DB contract (PostgreSQL 16 on :5433) is unchanged by these upgrades.

## Non-goals

- Adopting Spring Boot 4 in this sprint (scan only; adoption is a separately-approved follow-up).
- Adopting Maven 4 (RC only).
- Chasing transitive deps to newest across major series (netty 4.2 / tomcat 11) on a SB 3.5 base.
- Any feature work, schema change, or new provider. Frontend/Node upgrades (N/A — none exist).

## Acceptance criteria

AC1. `pom.xml` shows Java 25 target, Spring Boot 3.5.16, wrapper 3.9.16, and the pinned-dep targets in
`.planning/PHASE-B-VERSIONS.md`, each merged via its own green PR.
AC2. `./mvnw verify` is green on JDK 25 on `dev` after the full ladder (100+ tests, 0 failures).
AC3. `mvn dependency:tree` / Trivy shows no HIGH/CRITICAL CVEs; #25 overrides reduced to the minimum
still-necessary set (documented).
AC4. A committed SB4 breaking-change scan report with an explicit go/no-go recommendation exists.
AC5. `DECISIONS-LOG.md` records the Node/frontend N/A verdicts and the Maven-4-skip.
AC6. No changes on `test`/`main`; no unrelated files touched; no secrets committed.

## Edge cases

- Lombok/Mockito incompatibility on JDK 25 → the Java issue must bump both; if the BOM's Mockito lags,
  add a minimal override (documented).
- jjwt 0.12→0.13 minor may shift API/signatures → compile + full auth-suite verify; fix call sites if needed.
- Pruning a #25 override the BOM doesn't actually cover → Trivy would re-flag; keep it and note why.
- Spring Boot 3.5.16 patch may itself move managed transitive versions → re-run Trivy after the SB bump.

## Security considerations

- jjwt is JWT sign/verify — treat 0.13.0 as security-critical (ADVANCED tier, full auth verify).
- The #25 override re-eval directly governs CVE exposure — ADVANCED tier, must end Trivy-clean.
- Java 25 + latest Boot generally *reduce* CVE surface; regression risk is behavioural, not security-loss.

## Migration considerations

- No DB schema/Flyway changes. `ddl-auto: validate` stays; SchemaValidationIT must stay green.
- Runtime migration only (JDK 21→25); no data migration.

## Observability requirements

- Actuator, Prometheus registry, custom health indicators unchanged and still green
  (DatabaseHealthIndicatorTest, /management/health).

## Test expectations

- The existing 100-test suite is the regression contract — green on the final stack, per PR.
- No new tests required by the upgrade itself, except: if a dep bump changes a call site, adjust the
  affected test honestly (no fake-green). SB4 scan is analysis, not code.
