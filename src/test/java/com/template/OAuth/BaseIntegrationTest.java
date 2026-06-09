package com.template.OAuth;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Base for integration tests. Points at an externally-managed PostgreSQL instance
 * (see src/test/resources/application-test.yaml) rather than Testcontainers, which
 * cannot start on the headless build box (docker-java discovery fails against the
 * Docker Desktop proxy socket). Provide the DB via:
 *   docker run -d --name oauth-pg-test -e POSTGRES_DB=oauth_template_test \
 *     -e POSTGRES_USER=test -e POSTGRES_PASSWORD=test -p 5433:5432 postgres:16-alpine
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public abstract class BaseIntegrationTest {
}
