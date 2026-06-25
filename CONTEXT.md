# OAuth-PostgreSQL

A reusable Spring Boot authentication/OAuth **backend template** — the foundation you clone to start a new project that needs auth. Inherited from `Nootje88/OAuth` (a MySQL-backed template) and being converted to run on **PostgreSQL**. It is deliberately generic: no app-specific tables or business logic live here.

## Mission (locked 2026-06-09)

This is **a template, not a product**. The immediate job is a faithful **MySQL → PostgreSQL conversion** of the existing template, keeping all current auth features intact. It stays in the workspace `personal-dev` category (not promoted to a Dimoit `product`). No multi-tenancy, no app-specific schema — it remains a clean foundation to build upon.

## Language

**Identity / User**:
A person who can authenticate, regardless of which provider they used. One User may carry several provider links (Google, Spotify, …) but has exactly one **Primary Provider**.
_Avoid_: account, profile (profile is a sub-view of a User, not the User itself).

**AuthProvider**:
A source of authentication: `LOCAL` (email+password) or a social provider (`GOOGLE`, `SPOTIFY`, `APPLE`, `SOUNDCLOUD`).
_Avoid_: identity provider, IdP (reserve those for a future SSO context if one ever exists).

**Primary Provider**:
The single AuthProvider recorded as a User's main login method. Distinct from the set of all linked providers.

**Refresh Token**:
A long-lived credential (7-day default) exchanged for a short-lived JWT access token (1-hour default). One active Refresh Token per User. Capped by an **Absolute Lifetime** (30-day default) beyond which rotation no longer renews it and re-auth is required.
_Note:_ "one active Refresh Token per User" holds today; Tier 2b (multi-device sessions, deferred) will revise this to per-session/family tokens.

**Role**:
An authorization grant on a User: `USER`, `ADMIN`, `MODERATOR`, `PREMIUM`. A User may hold several.

**Audit Event**:
An immutable record of a security-relevant action (login success/failure, role change, profile update, …) with principal, IP, and outcome.

**TOTP Secret**:
A per-User shared secret (RFC 6238) used to verify time-based one-time passwords from an authenticator app. Stored server-side (must be recoverable to verify codes). Present only when MFA is enrolled.

**Recovery Code**:
A one-time backup code, hashed at rest (like other secrets), that satisfies the MFA Challenge when the authenticator is unavailable. Issued as a set once, at enrolment.

**MFA Challenge**:
The short-lived intermediate state between first-factor success (password or social login) and second-factor verification. Backed by a one-time challenge token, not a full session — it grants nothing until a TOTP or Recovery Code is verified.

**Token Epoch** (`tokensInvalidBefore`):
A per-User instant before which all access tokens are considered revoked. Set to "now" on admin ban / forced-logout, giving immediate revocation of an otherwise-stateless JWT.

## Relationships

- A **User** has exactly one **Primary Provider** and zero-or-more linked **AuthProviders**
- A **User** holds one active **Refresh Token** and zero-or-more **Roles**
- An **Audit Event** references a principal (a **User**'s identifier) but is not owned by the User row

## Flagged ambiguities

- "account" appeared informally for both the **User** and the login method — resolved: the person is a **User**; the login method is an **AuthProvider**.

## Decisions locked

| # | Decision | Outcome | Date |
|---|----------|---------|------|
| Mission | What is this codebase for? | Reusable Postgres-backed auth/OAuth **template** ("just a conversion", a foundation to build on). Not a product; stays `personal-dev`. | 2026-06-09 |
| Conversion shape | Postgres-only vs DB-agnostic | **Postgres-ONLY** (A). Remove MySQL entirely; rewrite Flyway V1 as Postgres DDL; swap driver + dialect; tests run on **Testcontainers-Postgres** (not H2). | 2026-06-09 |
| Conversion depth | Which layers convert | **APP + DOCKER** (option 2). Convert app config + docker-compose to Postgres. **Azure infra is out of scope** (left unconverted). **Remove `azure-pipelines.yml`**; no CI/CD (no GitHub Actions) for now. | 2026-06-09 |
| Cleanup & branches | Opportunistic cleanup? | Remove dead deps (`mysql-connector-j`, `flyway-mysql` — mandatory; `org.json` + `android-json` — provably unused, only in pom). **Keep** stale branches `ITtests`/`Unit-testing-and-docker` (no destructive deletion). Otherwise faithful conversion, no behaviour changes. | 2026-06-09 |
| Sequencing | Order of work | **Baseline-first**: verify the current MySQL template builds and all 22 tests pass BEFORE converting; then convert; then prove all tests pass on Postgres. | 2026-06-09 |
| MFA scope | Which accounts, opt-in vs enforced | **All accounts** (LOCAL + social), **opt-in AND role-mandatory** (configurable required-roles, default `ADMIN`). TOTP only. Two-step challenge flow; recovery codes hashed. See ADR-0005. | 2026-06-25 |
| Token revocation | Stateless JWT revoke strategy | **Per-user `tokensInvalidBefore` epoch** + `jti` claim (no full deny-list yet). Admin ban sets epoch + drops refresh tokens. Refresh gains a 30-day **Absolute Lifetime** cap. See ADR-0006. | 2026-06-25 |
| Tier-2 scope | Which sub-features this iteration | **2a (MFA) + 2c (revocation + lifecycle)**; **2b (multi-device) deferred** to the Redis iteration (deepest/highest-risk, breaks the one-token-per-user invariant). | 2026-06-25 |
