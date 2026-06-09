# PRD — MySQL → PostgreSQL conversion (OAuth template)

**Source:** `.planning/postgres-conversion.md` (grill stage 1, locked 2026-06-09). Decisions: `CONTEXT.md`, `docs/adr/0001..0003`.
**Stage:** hl-dev-flow stage 2 (`/to-prd`).

## Problem Statement

The team has a feature-rich Spring Boot OAuth backend template (social + email/password auth, JWT/refresh, RBAC, audit, i18n, rate limiting, metrics) that is hard-wired to **MySQL**. The intended foundation for future projects is **PostgreSQL**, so as it stands the template can't be cloned and used without each project first ripping out MySQL — defeating the point of a reusable foundation.

## Solution

Convert the template to run on **PostgreSQL only**, faithfully — every existing auth feature and every test preserved, nothing added. After conversion a developer can clone the repo, `docker-compose up` a Postgres-backed stack, and build/test green out of the box. MySQL support is removed entirely (single-DB, not DB-agnostic).

## User Stories

1. As a developer adopting the template, I want the app to connect to PostgreSQL by default, so that I don't have to swap the datasource before starting.
2. As a developer adopting the template, I want `docker-compose up` to start a PostgreSQL container (not MySQL), so that local dev works out of the box.
3. As a developer adopting the template, I want the Flyway baseline migration to create the schema on PostgreSQL, so that the DB initializes cleanly on first boot.
4. As a developer adopting the template, I want all auth features (Google/Spotify/Apple/SoundCloud OAuth, email+password, verification, password reset) to work unchanged on Postgres, so that the conversion costs me no functionality.
5. As a developer adopting the template, I want JWT + refresh-token flows to behave identically on Postgres, so that session handling is unaffected.
6. As a developer adopting the template, I want RBAC (USER/ADMIN/MODERATOR/PREMIUM) and the element-collection role/notification tables to map correctly on Postgres, so that authorization still works.
7. As a developer adopting the template, I want audit logging to persist to Postgres, so that security events are still recorded.
8. As a maintainer, I want a verified green baseline of the current MySQL template before any conversion, so that I know the starting point works and can attribute any later failure to the conversion.
9. As a maintainer, I want the full test suite (22 classes) to run against a real PostgreSQL via Testcontainers, so that Postgres-specific issues (enums, identity, casing) are caught.
10. As a maintainer, I want `ddl-auto: validate` to pass against the rewritten migration, so that the schema and JPA entities stay in lockstep.
11. As a maintainer, I want dead dependencies (MySQL connector, flyway-mysql, unused org.json/android-json) removed, so that the template ships no cruft.
12. As a maintainer, I want the database health indicator to report "PostgreSQL", so that ops signals are accurate.
13. As a maintainer, I want the backup script to use `pg_dump`, so that backups work on the new engine.
14. As a maintainer, I want `.env.template` and the README DB sections updated to Postgres, so that setup docs are correct.
15. As a maintainer, I want `azure-pipelines.yml` removed and no GitHub Actions added, so that the template carries no broken/irrelevant CI for now.
16. As a maintainer, I want the stale `azure/` infra left untouched but clearly flagged out-of-scope, so that nobody assumes it was converted.

## Implementation Decisions

- **Single-DB, Postgres-only** (ADR 0001). Remove `mysql-connector-j` and `flyway-mysql`; add `org.postgresql:postgresql` + `flyway-database-postgresql`.
- **Datasource/dialect**: `org.postgresql.Driver` + `org.hibernate.dialect.PostgreSQLDialect` across `application.yaml` and all four profiles + test config.
- **Migration module** (`V1__init_schema.sql`): rewritten as Postgres DDL. Identity columns → `BIGINT GENERATED ALWAYS AS IDENTITY`/serial to match the entities' generation strategy; `bit(1)`→`BOOLEAN`; `datetime(6)`→`TIMESTAMP(6)`; `enum(...)`→`VARCHAR` (entities use `@Enumerated(EnumType.STRING)`); drop InnoDB/charset trailers; standard Postgres unique/index/FK syntax. This is the deepest, highest-risk module and is validated by `ddl-auto: validate` + integration tests.
- **Test harness module** (`BaseIntegrationTest`): `MySQLContainer` → `PostgreSQLContainer("postgres:16")`; H2 dropped (ADR 0003). Tests run on the real engine.
- **Health indicator**: keep portable `SELECT 1`; relabel detail to PostgreSQL.
- **Docker**: compose `mysql:8` service → `postgres:16` (`pg_isready` healthcheck, named volume); `backup.sh` `mysqldump`→`pg_dump`; `.env.template` `DB_URL` → Postgres JDBC.
- **CI/CD** (ADR 0002): delete `azure-pipelines.yml`; add no GitHub Actions. `azure/` infra left stale and out of scope.
- **No behaviour changes.** Faithful conversion only; no refactors beyond dead-dep removal.

## Testing Decisions

- A good test asserts **external behaviour** (an auth flow returns the right tokens/status; the schema persists and reads back correctly), not implementation details.
- **All 22 existing test classes** are the regression net; they must pass unchanged in intent on Postgres. The integration tests (`AuthFlowIT`, `PWResetFlowIT`, `RefreshTokenFlowIT`, `RoleProtection*IT`, `EmailVerificationFlowTest`, repository tests) exercise the converted schema via Testcontainers.
- **Prior art**: the existing `BaseIntegrationTest` + `*IT` classes already encode the Testcontainers pattern — reuse it, only swapping the container engine.
- **Gate**: green MySQL baseline first (story 8), then green Postgres run (stories 9-10) via `./mvnw clean verify`.

## Out of Scope

- Azure infra conversion (`arm-template.json`, `*.ps1`) — left stale, flagged.
- Any new CI/CD (GitHub Actions / pipeline) — deferred.
- DB-agnostic / dual-MySQL support — explicitly rejected (ADR 0001).
- New features, provider changes, refactors, or schema additions beyond the faithful port.
- Deleting the stale upstream branches `ITtests` / `Unit-testing-and-docker`.

## Further Notes

- Sequencing is a hard constraint from Diangelo: **prove the current MySQL version + all tests green before converting**, then convert, then prove green on Postgres.
- The migration↔entity validation under `ddl-auto: validate` is the principal risk; the Testcontainers-Postgres suite is the early-warning system for it.
