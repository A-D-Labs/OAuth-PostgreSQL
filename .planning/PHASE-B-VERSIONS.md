# Phase B — Version Intelligence (authoritative)

Source: `maven-metadata.xml` from `repo1.maven.org` (authoritative repo, not the lagging
search index) + Adoptium/Apache release pages. Captured **2026-09-05**. STABLE-ONLY:
`-M*`, `-RC*`, `-rc*`, `-SNAPSHOT`, `alpha`, `beta`, `CR*`, milestone, `-ea`, `-pre` excluded.

Workers MUST re-confirm the exact latest patch at PR time (this file can drift); the *decisions*
(which line to target, what to prune, what to skip) are the durable part.

## Runtime & build tooling

| Item | Current | Target (stable) | Notes / coupling |
|---|---|---|---|
| Java | 21 (`maven.compiler.release=21`) | **25 LTS** | Java 25 is GA LTS, already installed. Requires Lombok 1.18.48 + a JDK25-capable Mockito + Spring Boot 3.5.16 to build/run green. |
| Maven (wrapper distro) | 3.9.9 | **3.9.16** | Maven 4.0.0 is still **RC only** (`4.0.0-rc-6`) → SKIP (stable-only). 3.10.0 also RC only. |
| maven-compiler-plugin | 3.13.0 | **3.16.0** | 4.0.0-beta-5 exists → SKIP. |
| jib-maven-plugin | 3.4.4 | **3.5.2** | Also bump `from.image` `eclipse-temurin:21-jre` → `25-jre` — coupled to the Java issue. |
| Lombok | 1.18.36 | **1.18.48** | **Required** for JDK 25 annotation processing — couple into the Java issue, not housekeeping. |
| Mockito | BOM-managed (no pin) | via SB 3.5.16 BOM | Confirm the resolved version supports JDK 25; add an override only if the BOM lags. |

## Spring Boot

| | Current | Latest 3.5.x stable | Latest 4.x stable |
|---|---|---|---|
| spring-boot-starter-parent | 3.5.14 | **3.5.16** | **4.1.1** (4.0.0–4.0.8, 4.1.0, 4.1.1 all GA; 4.2.0-M1 = milestone SKIP) |

- **Routine target = 3.5.16** (locked shape Option A).
- **Spring Boot 4.x is GA and mature (at 4.1.1)** → the gated SB4 issue is live: breaking-change
  scan first, then explicit HITL go/no-go. NOT an autonomous merge. Target if approved = latest 4.x
  stable at scan time (4.1.1 today). Major boundary: Spring Framework 7, Jakarta EE 11, removed
  deprecations, JSpecify nullness, Tomcat 11, Netty 4.2, springdoc 3.x.

## Explicitly-pinned deps

| Dep | Current | Target (on SB 3.5.16 base) | Issue |
|---|---|---|---|
| PostgreSQL JDBC (`postgresql.version`) | 42.7.11 | **42.7.13** | own issue (also collapses the #25 override) |
| jjwt (api/impl/jackson) | 0.12.6 | **0.13.0** | own issue — **minor** bump on auth-critical lib, careful verify |
| springdoc-openapi-starter-webmvc-ui | 2.8.5 | **2.9.0** | housekeeping — **2.x is the last SB3-compatible line**; 3.x is SB4-only (moves with the SB4 issue) |
| bucket4j-core / bucket4j-redis | 8.0.1 | **8.10.1** | housekeeping |
| caffeine | 3.2.0 | **3.2.4** | housekeeping |
| dev.samstevens.totp | 1.7.1 | 1.7.1 (already latest) | no-op |

## #25 security-override properties — re-evaluate vs the SB 3.5.16 BOM

These sit ABOVE the Boot BOM to clear Trivy HIGH/CRITICAL. After the SB bump, prune the ones the
new BOM already meets/exceeds; keep only still-needed overrides **within the SB-3.5-compatible
series** (do NOT chase the 4.x-series numbers below — they belong to SB4/Jakarta 11).

| Property | Current override | Latest overall | SB-3.5-compatible series | Action |
|---|---|---|---|---|
| `postgresql.version` | 42.7.11 | 42.7.13 | 42.7.x | Becomes the Postgres upgrade issue (→42.7.13); drop as a separate override if BOM catches up. |
| `jackson-bom.version` | 2.21.4 | 2.22.2 | 2.x | Check SB 3.5.16 BOM's Jackson; drop override if BOM ≥ CVE-clean, else bump within 2.x. |
| `netty.version` | 4.1.135.Final | 4.2.17.Final | **4.1.x** (4.2 is SB4 territory) | Stay on latest **4.1.x**; do NOT jump to 4.2 on a SB3.5 base. |
| `tomcat.version` | 10.1.55 | 11.0.25 | **10.1.x** (11.x is SB4 territory) | Stay on latest **10.1.x**; 11.x only under SB4. |

Verify: after re-eval, Trivy (or `mvn dependency:tree` + CVE check) must stay HIGH/CRITICAL-clean.

## N/A (logged in DECISIONS-LOG.md)

- **Node.js**: no `package.json`/`.husky`/`.nvmrc`/npm scripts anywhere in the repo → N/A.
- **Frontend (Vite, Tailwind)**: backend-only repo (README) → N/A.
