# GitHub Actions CI gate on `dev` (supersedes ADR-0002)

**Status:** accepted (2026-06-25) · **Supersedes:** ADR-0002 ("no CI/CD for now").

ADR-0002 deliberately added no CI because the template was "not deployed anywhere." The robustness roadmap reverses that: even an undeployed template benefits from a green gate, because a red commit can otherwise reach `dev`/`test`/`main` unnoticed and every later robustness tier relies on its tests actually running. This ADR adds **build+test CI** (no deploy) and makes it an **enforced** gate on `dev`.

## Decision

A GitHub Actions workflow (`.github/workflows/ci.yml`) runs on every pull request targeting `dev`:

- **JDK 21 (Temurin)** + Maven dependency caching.
- A **`postgres:16-alpine` service container** mirroring the ADR-0003 test-DB contract (`oauth_template_test`, `test`/`test`). The workflow points the Spring `test` profile at the service via env (`SPRING_DATASOURCE_*`), so `application-test.yaml` needs no change. This keeps integration tests (`*IT` via Failsafe) running against a **real PostgreSQL** in CI exactly as ADR-0003 requires — the "future CI as a service container" path that ADR-0003 anticipated.
- `./mvnw -B verify` — Surefire (`*Test`) + Failsafe (`*IT`) already split unit/integration.
- **Trivy** dependency-vulnerability scan (`aquasecurity/trivy-action`), failing the build on **HIGH/CRITICAL**. Chosen over OWASP dependency-check: no NVD API key, GitHub-native, fast enough to gate every PR.
- **Dependabot** (`.github/dependabot.yml`) for the `maven` and `github-actions` ecosystems.

The CI status check is made a **required check** on `dev` via branch protection (`gh api`), so the gate is enforced, not advisory. No required PR reviews on `dev` — only `main` requires review, matching the standard branch policy. CI is **build + test + scan only**; no deploy step (deploys remain out of scope, consistent with the personal-dev template mission).

## Consequences

- A failing build, failing test, or HIGH/CRITICAL dependency CVE blocks merge to `dev`.
- The suite still requires a reachable PostgreSQL (ADR-0003); CI now provides it as a service container instead of a local `docker run`.
- ADR-0002's "CI intentionally absent" stance no longer holds and is superseded here. The `azure/` removal recorded in ADR-0002's revision stands — this is GitHub Actions, not Azure Pipelines.
- Reversible: deleting the workflow + branch-protection rule restores the pre-CI state, but there is no reason to.
