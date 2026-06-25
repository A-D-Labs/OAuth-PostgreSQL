# Plan — Tier 1: CI/CD gate (robustness roadmap)

**Sprint:** `sprint:2026-W26` · **feat slug:** `ci-cd` · **Epic:** robustness-roadmap (Tier 1 of 3)
**Source:** `robustness-roadmap-PRD.md` Tier 1 · grilled 2026-06-25 (cc-bridge session d164a75e).
**Status:** plan locked (stage 1 / grill-with-docs complete). Next: `/to-prd`.

## Scope decision (locked in grill)

**W26 = Tier 1 (CI/CD) ONLY.** Tiers 2 (security depth) and 3 (completeness/ops) are explicitly deferred to a future sprint. Rationale: the PRD sequences CI first because once green it auto-enforces every later tier's tests; all Tier-1 work is backend/config with zero domain-model impact and low mutual conflict, so it fans out cleanly to AFK agents.

## Why this is safe against the domain model

Tier 1 adds **no** application code and touches **none** of the domain language in `CONTEXT.md` (User, AuthProvider, Primary Provider, Refresh Token, Role, Audit Event). It is pure build/test/ops tooling. The only documented decision it reverses is **ADR-0002 ("no CI/CD for now")** — superseded by **ADR-0004** (added this branch). `CONTEXT.md`'s locked mission ("Postgres-backed auth template, personal-dev, not a product") is unchanged: CI is operational robustness for the template, not a product pivot.

## User stories (from PRD Tier 1)

1. Every PR to `dev` runs `./mvnw -B verify` against a PostgreSQL **service container** in GitHub Actions, so a failing build/test can never be merged.
2. The workflow caches Maven dependencies, so CI is fast enough to gate every PR.
3. Dependabot is enabled for **Maven + GitHub Actions**, so dependency updates are surfaced automatically.
4. A dependency-vulnerability scan runs in CI and fails the build on known-vulnerable libraries.
5. A branch-protection-compatible **required status check** on `dev` enforces the green gate (not advisory).

## Decisions locked in grill (2026-06-25)

| # | Decision | Outcome |
|---|----------|---------|
| Scope | Which tiers in W26 | **Tier 1 (CI/CD) only.** Tier 2/3 deferred. |
| Issue housekeeping | #1–#6 (postgres-conversion, done) | **Close all six** as completed (epic shipped ~13f98c0, waves archived). |
| Issue housekeeping | #10 (deferred-work parking lot) | **Keep open + annotate**: check off now-done items (reuse detection, coarse error handling) + CI/CD once Tier 1 merges; note remaining bullets are owned by robustness-roadmap-PRD Tier 2/3. |
| Vuln scanner | OWASP dependency-check vs Trivy | **Trivy** — GitHub-native action, no NVD API key, fast enough to gate every PR, fails on HIGH/CRITICAL. |
| Gate enforcement | Agent toggles dev branch protection? | **Yes** — agent enables a required status check on `dev` via `gh api` once the CI check is green/named. No required reviews on dev (matches standard policy: only `main` needs review). |
| ADR | ADR-0002 reversal | **Add ADR-0004** (GitHub Actions CI) superseding ADR-0002; annotate ADR-0002 as superseded. |

## Technical approach

- **Workflow:** `.github/workflows/ci.yml`, triggered on `pull_request` → `dev` (and `push` to `dev` for status visibility).
  - `services: postgres:16-alpine` mirroring the test DB contract from ADR-0003 — DB `oauth_template_test`, user/pw `test`/`test`, mapped so the app's `test` profile reaches it. (CI can map container `5432`; `application-test.yaml` defaults to `:5433` — the workflow sets `SPRING_DATASOURCE_URL`/port env to match the service, no source change.)
  - Steps: checkout → set up JDK 21 (Temurin) → Maven cache → `./mvnw -B verify` (Surefire `*Test` + Failsafe `*IT` already split) → Trivy scan.
- **Maven cache:** `actions/setup-java` built-in `cache: maven` (or `actions/cache` on `~/.m2`) keyed on `pom.xml`.
- **Dependabot:** `.github/dependabot.yml` with two ecosystems — `maven` and `github-actions`, weekly.
- **Trivy:** `aquasecurity/trivy-action` in `fs`/`repo` mode (or scan the built artifact), `severity: HIGH,CRITICAL`, `exit-code: 1`.
- **Branch protection:** after the workflow's check name is stable, `gh api` PUT on `repos/Nootje88/OAuth-PostgreSQL/branches/dev/protection` requiring the CI status check; no required reviews on dev.
- **ADR-0004** records all of the above and supersedes ADR-0002.

## Issue slicing preview (finalised in stage 3 / to-issues)

Tracer-bullet vertical slices, one `feat:ci-cd` issue each, all `sprint:2026-W26` + `assignee:agent` + `ready-for-agent`:

1. **CI core** — `ci.yml` with Postgres service container + JDK 21 + `./mvnw -B verify` green on a PR to dev. (Foundation; others build on it.)
2. **Maven cache** — add dependency caching to the workflow; assert faster warm runs.
3. **Dependabot** — `dependabot.yml` for maven + github-actions.
4. **Trivy vuln scan** — add Trivy step, fail on HIGH/CRITICAL.
5. **Branch protection + ADR-0004** — enable required status check on dev via `gh api`; commit ADR-0004 + annotate ADR-0002.

Likely waves: #1 first (sequential foundation), then #2/#3/#4 parallel (independent files), then #5 (depends on #1's stable check name).

## Out of scope (this sprint)

- All Tier 2 (MFA/TOTP, multi-device sessions, access-token revocation) and Tier 3 (Redis, finish OAuth providers, observability) — future sprints, tracked by the PRD + issue #10.
- Azure infra (already removed; ADR-0002 revision).
- `dev → test` / `test → main` deploy pipelines — CI here is build+test gate only, no deploy (matches standard dev-branch CI policy).
