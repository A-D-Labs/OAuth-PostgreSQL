# Spring Boot 4 — Breaking-Change Scan + Go/No-Go (#76)

**Scan date:** 2026-09-05 · **Base:** dev @f2ad2b3 (Spring Boot **3.5.16**, Java 25, all CVEs clear,
100/100 green) · **Target if adopted:** Spring Boot **4.1.1** (latest 4.x stable; 4.2.0-M1 is a milestone → excluded).

> **This is analysis only. No pom was changed. Adoption requires explicit Diangelo approval.**

## TL;DR recommendation: **GO — but as its own dedicated follow-up sprint, NOT bolted onto W36.**

The codebase is unusually SB4-ready (only ONE hard code breakage), but SB4 is a *major* framework jump
(Spring Framework 7, Jakarta EE 11, Tomcat 11, Netty 4.2, springdoc 3.x) across auth-critical code. The
current stack is already fully latest-stable-3.x, CVE-clean, and green — there is **no urgent driver**.
Safest path: adopt SB4 in a scoped sprint with the 100-test suite as the safety net, not as a tail-end
task of this upgrade pass.

## What actually breaks in THIS repo

| Area | Finding | Severity |
|---|---|---|
| **Spring Security 7** | `ActuatorSecurityConfig.java:23` — `new AntPathRequestMatcher("/management/**")` is **removed** in SS7 (JDK-25 compiler already flags it deprecated/for-removal). Replace with the String overload `.securityMatcher("/management/**")` or `PathPatternRequestMatcher`. | **The one real code change.** Small. |
| Security DSL | `SecurityConfig.java` uses the modern lambda DSL (`.requestMatchers()`, `.cors()`, `.csrf()`, `.sessionManagement()`). SS7-compatible. No `authorizeRequests`/`antMatchers`/`.and()`/`WebSecurityConfigurerAdapter`/`@EnableGlobalMethodSecurity`. | ✅ clean |
| `ActuatorSecurityConfig.java:41` | `.cors(cors -> cors.configure(http))` — verify the CORS configurer signature under SS7. | low |
| Jakarta EE 11 | **No `javax.*` imports**; 68 `jakarta.*` usages already. Jakarta EE 10→11 API bumps are largely source-compatible for persistence/servlet/validation. | low |
| Auto-config | No `spring.factories` / `AutoConfiguration.imports` in the repo — nothing to migrate. | ✅ clean |

## Coupled major bumps SB4 forces (the real weight)

| Dep | 3.5.x now | SB4 line | Notes |
|---|---|---|---|
| Spring Framework | 6.2.19 | **7.0.x** | JSpecify nullness, removed deprecations, RestClient emphasis. |
| Jakarta EE | 10 | **11** | jakarta.* minor API bumps. |
| Tomcat | 10.1.59 (override) | **11.0.x** | Our `tomcat` override moves to 11.x; the 10.1.59 pin becomes moot. |
| Netty | 4.1.136 (override) | **4.2.x** | Our `netty` override moves to 4.2.x. |
| springdoc-openapi | 2.9.0 | **3.x** | **Required** — 2.x is SB3-only. Used broadly (OpenApiConfig, @Operation across controllers). Validate 3.x API. |
| Java baseline | 25 | 17+ (we're on 25 ✅) | No action. |
| jjwt / flyway / bucket4j / caffeine | current | confirm SB4 compat | Expected fine; verify at migration. |

## Likely upside of adopting SB4

- **Fewer CVE overrides:** SB4's BOM ships Tomcat 11 / Netty 4.2, which are current — likely lets us **drop the tomcat + netty overrides** entirely (they exist today only because the 3.5.16 BOM lags the CVE floors). Net reduction in hand-maintained pins.
- Future-proofing; longer support horizon than 3.5.x.

## Risk areas to test hardest (auth-critical)

1. `SecurityConfig` / `ActuatorSecurityConfig` (the matcher swap + CORS/CSRF under SS7).
2. OAuth2 client login flow (Google + Microsoft) under SS7's OAuth2 changes — `OAuth2SuccessHandler`, `OidcUser`.
3. `JwtAuthenticationFilter` + jjwt under SF7.
4. springdoc 3.x — Swagger UI + `/v3/api-docs` still serve.
5. Actuator endpoint/property renames (run `spring-boot-properties-migrator` during migration).

## Effort estimate

**Moderate**, ~1 focused sprint / 1 ADVANCED-tier issue: the code delta is small (one matcher line + springdoc
3.x validation + override-series bumps to 11.x/4.2), but the coupled major surface demands the full suite +
manual OAuth smoke on both providers. The strong existing 100-test suite (incl. OAuth2, refresh reuse, MFA)
substantially de-risks it.

## Go/No-Go options for Diangelo

- **A. NO-GO this sprint, GO as a dedicated follow-up sprint (RECOMMENDED).** Ship W36 as-is (latest stable
  3.5.x, CVE-clean, green); open a scoped SB4 issue for a future sprint.
- **B. GO now** — proceed to SB4 4.1.1 in this session as a new ADVANCED issue (AntPathRequestMatcher swap +
  springdoc 3.x + tomcat 11 / netty 4.2, drop stale overrides, full verify).
- **C. NO-GO, park indefinitely** — stay on 3.5.x; revisit only when a concrete driver appears.
