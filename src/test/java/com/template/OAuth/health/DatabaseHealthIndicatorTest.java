package com.template.OAuth.health;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class DatabaseHealthIndicatorTest {

    @Autowired
    private DatabaseHealthIndicator databaseHealthIndicator;

    @Test
    void reportsUpAndIdentifiesPostgres() {
        Health health = databaseHealthIndicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails())
                .containsEntry("database", "PostgreSQL")
                .containsEntry("status", "Available");
    }
}
