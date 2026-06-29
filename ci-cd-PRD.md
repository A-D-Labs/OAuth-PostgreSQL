# PRD — Tier 1: CI/CD gate

**Epic:** robustness-roadmap (Tier 1 of 3) · **Sprint:** `sprint:2026-W26` · **feat:** `ci-cd`
**Branch:** `feature/ci-cd` → `dev` · **Plan:** `.planning/ci-cd.md` · **ADR:** `docs/adr/0004-github-actions-ci.md`
**Status:** specced (stage 2). Grilled & scoped 2026-06-25 (cc-bridge d164a75e). Supersedes ADR-0002.

## Problem

The template has a strong security foundation but no automated build/test gate: a commit that fails to compile, fails a test, or pulls in a known-vulnerable dependency can reach `dev` (and onward to `test`/`main`) unnoticed. Every later robustness tier (MFA, multi-device sessions, Redis, …) assumes its tests actually run on each change — that guarantee does not exist yet. ADR-0002 deliberately deferred CI; this PRD reverses that as the first, highest-leverage robustness slice.

## Goals

- Every PR to `dev` builds and runs the full suite (`./mvnw -B verify`, Surefire `*Test` + Failsafe `*IT`) against a **real PostgreSQL service container** in GitHub Actions.
- CI is fast enough to gate every PR (Maven dependency caching).
- Dependency updates (Maven + GitHub Actions) are surfaced automatically (Dependabot).
- Known-vulnerable dependencies fail the build (Trivy, HIGH/CRITICAL).
- The green check is **enforced** on `dev` via branch protection (required status check), not advisory.

## Non-goals (this PRD)

- Any deploy step — CI is build + test + scan only (matches the personal-dev template mission; no deploy target).
- All Tier 2 (MFA, multi-device sessions, access-token revocation) and Tier 3 (Redis, finish OAuth providers, observability) — future sprints, tracked by `robustness-roadmap-PRD.md` + issue #10.
- Any application/domain-model code change. Tier 1 touches no `CONTEXT.md` term.
- CI on `test`/`main` deploy promotions (out-of-band, Diangelo-gated as today).

## User stories & acceptance criteria

### S1 — CI core: verify on a Postgres service container
- **AC1** A workflow `.github/workflows/ci.yml` triggers on `pull_request` targeting `dev` (and `push` to `dev`).
- **AC2** It runs JDK 21 (Temurin) and a `postgres:16-alpine` **service container** seeded with DB `oauth_template_test`, user/pw `test`/`test`.
- **AC3** The Spring `test` profile reaches the service via env (`SPRING_DATASOURCE_URL`/credentials) — no change to `application-test.yaml`.
- **AC4** `./mvnw -B verify` runs and BOTH Surefire (`*Test`) and Failsafe (`*IT`) execute; the job is red if any test fails.
- **AC5** Proven green on a real PR to `dev`.

### S2 — Maven dependency caching
- **AC1** The workflow caches `~/.m2` keyed on `pom.xml` (e.g. `setup-java` `cache: maven`).
- **AC2** A warm run restores the cache (visible cache hit in logs); no behaviour change to the build result.

### S3 — Dependabot
- **AC1** `.github/dependabot.yml` enables the `maven` and `github-actions` ecosystems on a weekly schedule.
- **AC2** Config is valid (Dependabot accepts it; no schema errors in the repo's Dependabot view).

### S4 — Dependency-vulnerability scan (Trivy)
- **AC1** A Trivy step (`aquasecurity/trivy-action`) scans the project's dependencies in CI.
- **AC2** `severity: HIGH,CRITICAL`, `exit-code: 1` — a HIGH/CRITICAL finding fails the build.
- **AC3** The current dependency set is assessed; if a pre-existing HIGH/CRITICAL exists, it is documented (ignore-file with justification or an upgrade) rather than silently disabling the gate.

### S5 — Enforced gate + ADR-0004
- **AC1** Branch protection on `dev` requires the CI status check to pass before merge (set via `gh api`).
- **AC2** No required PR reviews on `dev` (only `main` requires review — standard policy).
- **AC3** `docs/adr/0004-github-actions-ci.md` is committed and ADR-0002 is annotated as superseded. *(Already landed on this branch; S5 verifies + wires enforcement.)*

## Technical decisions (locked in grill)

- **Platform:** GitHub Actions (not Azure Pipelines). Supersedes ADR-0002.
- **DB in CI:** `postgres:16-alpine` service container mirroring ADR-0003's external-Postgres contract; workflow env points the `test` profile at it. Keeps `*IT` running on real Postgres as ADR-0003 mandates.
- **Scanner:** Trivy (no NVD API key, GitHub-native, fast) over OWASP dependency-check.
- **Enforcement:** agent enables the required status check on `dev` via `gh api` (dev is non-gated; agent owns the dev cycle).
- **Issue housekeeping (stage 3):** close #1–#6 (postgres-conversion, done); keep + annotate #10 (deferred-work parking lot).

## Testing

- Tier 1 is itself the meta-test: once green it enforces every later tier's suite automatically.
- S1 is proven by an actual PR turning the check green; S2 by a cache-hit log line; S4 by a deliberately-checked vuln gate; S3 by a valid config; S5 by an attempted merge being blocked until the check passes.
- No new application tests — Tier 1 adds tooling, not behaviour.

## Rollout

Single feature epic into `dev` via per-issue PRs. S1 is the foundation (others extend the same workflow file), then S2/S3/S4 are independent, then S5 wires enforcement once S1's check name is stable. `dev → test → main` promotions remain out-of-band and Diangelo-gated.
