package com.template.OAuth.audit;

import com.template.OAuth.entities.AuditEvent;
import com.template.OAuth.enums.AuditEventType;
import com.template.OAuth.repositories.AuditEventRepository;
import com.template.OAuth.service.AuditService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
public class AuditLoggingTests {

    @Autowired
    private AuditService auditService;

    @Autowired
    private AuditEventRepository auditEventRepository;

    @AfterEach
    void cleanUp() {
        auditEventRepository.deleteAll();
    }

    @Test
    public void testAuditLogging() {
        String testPrincipal = "test@example.com";
        String testDescription = "Test audit event";

        // logEvent is @Async and commits in its own transaction, so this test
        // must not be @Transactional (the rollback would hide the committed row).
        auditService.logEvent(new AuditEvent(AuditEventType.SYSTEM_EVENT, testPrincipal, testDescription));

        List<AuditEvent> events = awaitEvents(testPrincipal);

        assertFalse(events.isEmpty(), "Audit events list should not be empty");
        AuditEvent event = events.get(0);
        assertEquals(AuditEventType.SYSTEM_EVENT, event.getType(), "Event type should match");
        assertEquals(testPrincipal, event.getPrincipal(), "Principal should match");
        assertEquals(testDescription, event.getDescription(), "Description should match");
    }

    private List<AuditEvent> awaitEvents(String principal) {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(5));
        List<AuditEvent> events = List.of();
        while (Instant.now().isBefore(deadline)) {
            events = auditEventRepository
                    .findByPrincipalOrderByTimestampDesc(principal, PageRequest.of(0, 10))
                    .getContent();
            if (!events.isEmpty()) {
                return events;
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        return events;
    }
}
