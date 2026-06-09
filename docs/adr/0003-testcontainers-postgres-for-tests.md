# Integration tests run on a real PostgreSQL (externally provided), not H2 or Testcontainers

The template's tests previously leaned on H2 (test-scoped) and a MySQL Testcontainer. Post-conversion, integration tests run against a **real PostgreSQL** so that `ddl-auto: validate` against the rewritten `V1` migration is actually exercised — H2 silently diverges on enums, identity generation, casing, and type coercion, giving false confidence.

## Revision: external Postgres, not Testcontainers

The original plan was `PostgreSQLContainer` via Testcontainers. That is **not viable on the headless build box**: docker-java's environment discovery hits Docker Desktop's proxy socket and gets an empty HTTP 400 `/info` on every strategy, so no Testcontainers context can start (proven, not assumed). A plain `docker run postgres:16-alpine` works, however.

So integration tests now point at an **externally-managed PostgreSQL** selected by the `test` Spring profile (`src/test/resources/application-test.yaml`, default `jdbc:postgresql://localhost:5433/oauth_template_test`), and the Testcontainers dependencies are removed from `pom.xml`. `BaseIntegrationTest` is a plain `@SpringBootTest` + `@ActiveProfiles("test")` with no container lifecycle. Provide the DB with:

```
docker run -d --name oauth-pg-test -e POSTGRES_DB=oauth_template_test \
  -e POSTGRES_USER=test -e POSTGRES_PASSWORD=test -p 5433:5432 postgres:16-alpine
```

Trade-off: the suite no longer self-provisions its DB — a Postgres must be running and reachable (locally or in any future CI as a service container). In exchange it runs everywhere a daemon-bound Testcontainers setup cannot, and still catches Postgres-specific schema/validation failures at test time. The H2 dependency remains dropped.
