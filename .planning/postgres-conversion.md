# Plan — MySQL → PostgreSQL conversion

**Stage:** hl-dev-flow stage 1 (`/grill-with-docs`) output.
**Status:** design locked 2026-06-09. Ready for `/to-prd`.
**Branch:** authored on `feature/grill-bootstrap` off `dev`.

## Mission recap

This is a **reusable Postgres-backed auth/OAuth template** — a foundation to build on, not a product. The job is a **faithful MySQL → PostgreSQL conversion** of the inherited `Nootje88/OAuth` template, keeping every existing auth feature intact and all tests green. Stays `personal-dev`.

## Locked decisions (see CONTEXT.md "Decisions locked")

1. **Mission** — reusable Postgres-backed auth/OAuth template; personal-dev; no multi-tenancy.
2. **Shape** — PostgreSQL-ONLY (remove MySQL entirely). Tests run on **Testcontainers-Postgres**, not H2.
3. **Depth** — convert **APP + DOCKER** layers. Azure infra out of scope. Delete `azure-pipelines.yml`; no CI/CD for now.
4. **Cleanup** — remove dead deps (`mysql-connector-j`, `flyway-mysql`; plus `org.json` + `android-json`, both provably unused). Keep stale branches. No other refactors.
5. **Sequencing** — **baseline-first**: prove the current MySQL build + all 22 tests pass, *then* convert, *then* prove all tests pass on Postgres.

## Hard constraint from Diangelo

> "Make sure first the current version is working, and then convert. Make sure all tests are also good."

Translation into the work order: a **green MySQL baseline is the first deliverable** (issue #1). No conversion commit lands until the baseline is reproducibly green. The conversion is "done" only when the same 22 test classes pass on Postgres.

## Conversion surface (21 files, 3 layers)

### APP layer (mandatory)

| File | Change |
|---|---|
| `pom.xml` | Remove `com.mysql:mysql-connector-j`, `org.flywaydb:flyway-mysql`. Add `org.postgresql:postgresql` (runtime) + `org.flywaydb:flyway-database-postgresql`. Swap Testcontainers `mysql` artifact → `postgresql`. Remove unused `org.json` + `com.vaadin.external.google:android-json`. Drop `h2` test dep (replaced by Testcontainers-Postgres) — confirm no remaining H2 usage first. |
| `src/main/resources/application.yaml` | `datasource.url` → `jdbc:postgresql://localhost:5432/oauth_template`; `driver-class-name` → `org.postgresql.Driver`; `hibernate.dialect` → `org.hibernate.dialect.PostgreSQLDialect`. Keep `flyway.baseline-on-migrate`. |
| `application-dev.yaml` / `-test.yaml` / `-pat.yaml` / `-prod.yaml` | Swap each MySQL URL/driver/dialect to Postgres equivalents. |
| `src/main/resources/db/migration/V1__init_schema.sql` | Rewrite MySQL DDL → Postgres DDL (details below). **Highest-risk file.** |
| `src/test/resources/application-test.yaml` | Postgres driver/dialect. |
| `src/test/java/.../BaseIntegrationTest.java` | `MySQLContainer("mysql:8.0")` → `PostgreSQLContainer("postgres:16")`; driver → `org.postgresql.Driver`; dialect → `PostgreSQLDialect`. |
| `src/main/java/.../health/DatabaseHealthIndicator.java` | `SELECT 1` is portable; change the `"MySQL"` detail label to `"PostgreSQL"`. |

#### V1__init_schema.sql rewrite rules

All entity enums use `@Enumerated(EnumType.STRING)`, so enum columns become **VARCHAR** (optionally CHECK-constrained), **not** native Postgres enum types. `ddl-auto: validate` means the DDL must satisfy Hibernate validation against the entities exactly — this is where tests will catch mismatches.

- `` `identifier` `` (backticks) → bare or double-quoted identifiers.
- `bigint NOT NULL AUTO_INCREMENT` PK → `BIGINT GENERATED ALWAYS AS IDENTITY` (or `BIGSERIAL`); pick whichever Hibernate's `IDENTITY`/`SEQUENCE` strategy on the entities validates against.
- `bit(1)` → `BOOLEAN`.
- `datetime(6)` → `TIMESTAMP(6)` (without time zone, to stay faithful to current behaviour).
- `enum('A','B',...)` columns (incl. `user_roles.roles`, `user_notification_preferences.enabled_notifications` element collections) → `VARCHAR` (+ optional `CHECK` constraint).
- `varchar(n)` → `VARCHAR(n)` (unchanged).
- Drop `ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=...` trailers (Postgres default UTF-8).
- Named `UNIQUE KEY` / `KEY` / `CONSTRAINT ... FOREIGN KEY` → standard Postgres `UNIQUE` / `CREATE INDEX` / `FOREIGN KEY`.

### DOCKER layer (in scope)

| File | Change |
|---|---|
| `docker/compose/docker-compose.yml` + `.dev.yml` / `.test.yml` / `.pat.yml` / `-prod-yml` | Replace the `mysql:8` service with `postgres:16` (env `POSTGRES_USER/PASSWORD/DB`, port `5432`, `pg_isready` healthcheck, named volume). Update the app service's `DB_URL`/driver env. |
| `docker/scripts/backup.sh` | `mysqldump` → `pg_dump`. |
| `docker/**/Dockerfile*` | Audit for MySQL client refs; likely no change. |
| `.env.template` | `DB_URL` → `jdbc:postgresql://localhost:5432/oauth_template?...`. |

### Remove (CI/CD)

- **Delete `azure-pipelines.yml`.**
- No `.github/workflows` exists yet — nothing to delete there; none added.

### Out of scope (left as-is, flagged stale)

- `azure/` infra (`arm-template.json`, `*.ps1`) — still MySQL; **not converted** per decision 3. Noted as stale in the template; a future adopter wiring Azure deploy must convert it then.
- `README.md` — update the DB sections (MySQL → PostgreSQL, prereqs) as doc hygiene; low risk, fold into the conversion.

## Work order (feeds /to-issues)

1. **Establish green MySQL baseline** — build + run all 22 test classes on the *current* template; record the green result. (Gate: no conversion starts until this passes.)
2. **Build/deps swap** — pom.xml driver/flyway/testcontainers + dead-dep removal.
3. **App config swap** — all `application*.yaml` (main + test) URLs/drivers/dialects.
4. **Migration rewrite** — `V1__init_schema.sql` to Postgres DDL; reconcile against entities under `ddl-auto: validate`.
5. **Test harness** — `BaseIntegrationTest` → PostgreSQLContainer; green all integration tests.
6. **Health indicator label** — MySQL → PostgreSQL.
7. **Docker** — compose services + `backup.sh` + `.env.template`.
8. **Remove `azure-pipelines.yml`.**
9. **Docs** — README DB sections.
10. **Final gate** — all 22 test classes green on Postgres; `./mvnw clean verify` passes.

## Architecture pass (stage 4)

**Outcome: no refactor precedes the build.** The conversion is a value-swap on a conventional layered Spring Boot app, not a structural change; refactoring now would contradict the faithful-conversion mission (ADR 0001) and add risk to a change whose whole value is fidelity. Assessed the three touched module-areas:

- **Migration/schema** (`V1__init_schema.sql`) — single raw-SQL baseline; appropriate for a template. No change.
- **Test harness** (`BaseIntegrationTest`) — clean single seam; swap one adapter (MySQL→Postgres container). No change.
- **Datasource config** — URL/driver/dialect duplicated across `application.yaml` + 4 profiles (mild shallow seam). _Deferred deepening:_ centralize datasource defaults in the base YAML so profiles override only deltas. Not done now — restructuring config mid-conversion risks behaviour drift and muddies the baseline→Postgres diff.
- **`DatabaseHealthIndicator`** — hardcodes the DB-product string in 3 spots. _Deferred deepening:_ derive the product name from `DatabaseMetaData`. Not done now — a behaviour change beyond a faithful port.

The two deferred items are optional post-conversion follow-ups; intentionally **not** filed as issues to keep the conversion scope clean.

## Risks

- **Migration ↔ entity validation** (step 4) is the main risk: `ddl-auto: validate` will fail the boot if any column type/name/nullability diverges from the JPA mappings. Mitigated by Testcontainers-Postgres integration tests catching it immediately.
- **Identity strategy**: if entities use `GenerationType.IDENTITY`, the DDL must use `GENERATED ... AS IDENTITY` / serial so Hibernate validates; verify per entity during step 4.
- **Element-collection enum tables**: `user_roles`, `user_notification_preferences` need their join-table DDL to match Hibernate's expectations on Postgres.
