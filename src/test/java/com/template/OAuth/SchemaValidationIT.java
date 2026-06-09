package com.template.OAuth;

import com.template.OAuth.enums.AuditEventType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the Flyway V1 schema against entity/enum drift on the real PostgreSQL.
 *
 * The application boots under {@code ddl-auto: validate}, so an out-of-sync schema
 * already fails context load. This adds an explicit guard on the one piece validation
 * cannot catch: the {@code audit_events.type} CHECK constraint must list every
 * {@link AuditEventType} value (the column is a plain VARCHAR to Hibernate, so a
 * missing enum value in the CHECK list would silently reject valid inserts at runtime).
 */
class SchemaValidationIT extends BaseIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void auditEventTypeCheckConstraintCoversEveryEnumValue() {
        String constraintDef = jdbcTemplate.queryForObject(
                "SELECT pg_get_constraintdef(c.oid) " +
                        "FROM pg_constraint c " +
                        "JOIN pg_class t ON c.conrelid = t.oid " +
                        "WHERE t.relname = 'audit_events' AND c.conname = 'ck_audit_events_type'",
                String.class);

        assertThat(constraintDef)
                .as("ck_audit_events_type CHECK constraint must exist")
                .isNotNull();

        for (AuditEventType type : AuditEventType.values()) {
            assertThat(constraintDef)
                    .as("CHECK constraint must accept audit event type %s", type.name())
                    .contains(type.name());
        }
    }
}
