# Decisions Log

Running log of notable decisions taken during automated dev sessions. Newest first.

## 2026-09-05 — Stack upgrade to latest stable (hl_dev_flow_v2, session 714bba31)

**Phase A baseline: GREEN.** `./mvnw verify` on dev HEAD `10e416d` (tag `v1.0.0`), JDK 21, real
PostgreSQL :5433 → 100/100 tests pass (67 Surefire + 33 Failsafe), BUILD SUCCESS ~42s. Cleared to
upgrade. See `.planning/BASELINE.md`.

**Upgrade shape (Diangelo, "do as recommended" → Option A "modern-but-guarded"):**
- **Java 21 → 25 LTS.**
- **Spring Boot stays on latest stable 3.5.x (3.5.16)** for the routine pass. **Spring Boot 4.x
  (GA, at 4.1.1) is gated** behind a mandatory breaking-change scan + explicit HITL go/no-go — NOT
  an autonomous merge.
- **Bump explicit/unmanaged pins to latest stable; re-evaluate & prune the #25 CVE overrides**
  against the post-upgrade Boot BOM (keep only still-needed ones, stay Trivy-clean).
- STABLE-ONLY (no -RC/-M/-SNAPSHOT/-alpha/-beta). One issue per major-version boundary; trivial
  patches grouped into one housekeeping issue. Every upgrade PR must pass `./mvnw verify` before
  merge to dev.

**Node.js tooling — N/A (asked & skipped).** No `package.json`, `.husky`, `.nvmrc`, or npm scripts
exist anywhere in the repo (backend-only Java/Maven). Nothing to upgrade to Node LTS. Logged per
Diangelo's explicit ask so it's on record that we checked, not overlooked.

**Frontend deps (Vite, Tailwind) — N/A (asked & skipped).** This repo is backend-only per README
(no bundled UI; it expects a separate frontend). No Vite/Tailwind/any frontend build present. Logged
per Diangelo's explicit ask.

**Maven 4 — SKIPPED (not stable).** Latest Maven 4 is `4.0.0-rc-6` (release candidate). Wrapper
targets latest stable **3.9.16** instead. Re-evaluate once Maven 4.0.0 GA ships.
