# Decisions Log

Running log of notable decisions taken during automated dev sessions. Newest first.

## 2026-09-05 (cont.) — Phase B outcomes (session 714bba31)

**Ladder shipped to dev, all PRs green (verify + Trivy on JDK 25):** SB 3.5.16 + CVE overrides (#70/#77),
Java 25 LTS + toolchain (#71/#78), jjwt 0.13.0 (#73/#79), housekeeping springdoc 2.9.0/bucket4j 8.10.1/
caffeine 3.2.4 (#74/#80), pruned redundant jackson override (#75/#81). #72 (postgres 42.7.13) delivered by
#70. Final validation: `./mvnw verify` 100/100 on JDK 25 + jib image builds on `temurin:25-jre`.

**CVE gate:** dev's Trivy check was pre-existingly RED on 12 CVEs (3 CRITICAL, CVE-DB drift since #25).
Approved strategy A bundled the fixes into the first PR (#70). All 12 cleared.

**tomcat correction:** Trivy's advisory listed 10.1.58 which Maven Central skipped → used published **10.1.59**.

**Override re-eval vs 3.5.16 BOM (#75):** dropped `jackson-bom` (override 2.21.4 == BOM, redundant no-op);
kept tomcat 10.1.59 / netty 4.1.136.Final / postgres 42.7.13 — the 3.5.16 BOM (10.1.55 / 4.1.135 / 42.7.11)
is below the CVE-fix floors, so they're still required to stay Trivy-clean.

**Spring Boot 4 — NO-GO this sprint, GO as a dedicated follow-up (Diangelo, "do as recommended").**
SB 4.1.1 is GA; scan (`.planning/sb4-breaking-change-scan.md`) found only ONE hard code breakage
(AntPathRequestMatcher, removed in Spring Security 7) but a broad coupled-major surface (Framework 7,
Jakarta 11, Tomcat 11, Netty 4.2, springdoc 3.x). Tracked for a future sprint by **#83**.

**Maven wrapper 3.9.9 → 3.9.16 — DEFERRED.** `.mvn/wrapper/maven-wrapper.properties` is a permission-gated
sensitive file; this headless session cannot approve the edit. Trivial to finish in an interactive session.
(Maven 4 stays skipped — RC only.)

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
