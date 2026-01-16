package com.template.OAuth.service;

import com.template.OAuth.entities.AuditEvent;
import com.template.OAuth.enums.AuditEventType;
import com.template.OAuth.repositories.AuditEventRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

@Service
public class AuditService {

    private static final Logger logger = LoggerFactory.getLogger(AuditService.class);

    @Autowired
    private AuditEventRepository auditEventRepository;

    /**
     * Log an audit event asynchronously to prevent impacting application
     * performance
     */
    @Async
    public void logEvent(AuditEvent auditEvent) {
        try {
            // Capture current request details if available
            Optional<HttpServletRequest> requestOpt = getCurrentRequest();
            if (requestOpt.isPresent()) {
                HttpServletRequest request = requestOpt.get();
                auditEvent.setIpAddress(getClientIp(request));
                auditEvent.setUserAgent(request.getHeader("User-Agent"));
                auditEvent.setSource(request.getRequestURI());
            }

            auditEventRepository.save(Objects.requireNonNull(auditEvent));
            logger.debug("Audit event logged: {}", auditEvent);
        } catch (Exception e) {
            // Even if audit logging fails, don't disrupt the application flow
            logger.error("Failed to log audit event: {}", e.getMessage(), e);
        }
    }

    /**
     * Convenience method to create and log an audit event
     */
    @Async
    public void logEvent(AuditEventType type, String description) {
        AuditEvent auditEvent = new AuditEvent(type, getCurrentUserPrincipal(), description);
        logEvent(auditEvent);
    }

    /**
     * Convenience method with details
     */
    @Async
    public void logEvent(AuditEventType type, String description, String details) {
        AuditEvent auditEvent = new AuditEvent(type, getCurrentUserPrincipal(), description);
        auditEvent.setDetails(details);
        logEvent(auditEvent);
    }

    /**
     * Convenience method with outcome
     */
    @Async
    public void logEvent(AuditEventType type, String description, String details, String outcome) {
        AuditEvent auditEvent = new AuditEvent(type, getCurrentUserPrincipal(), description);
        auditEvent.setDetails(details);
        auditEvent.setOutcome(outcome);
        logEvent(auditEvent);
    }

    /**
     * Get the current authenticated user principal or "anonymous" if not
     * authenticated
     */
    private String getCurrentUserPrincipal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() ||
                "anonymousUser".equals(authentication.getPrincipal())) {
            return "anonymous";
        }
        return authentication.getName();
    }

    /**
     * Get the current HttpServletRequest if available
     */
    private Optional<HttpServletRequest> getCurrentRequest() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();

        if (attributes != null) {
            return Optional.of(attributes.getRequest());
        }
        return Optional.empty();
    }

    /**
     * Extract client IP address from request, handling proxies
     */
    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("HTTP_CLIENT_IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("HTTP_X_FORWARDED_FOR");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        return ip;
    }

    // Methods for retrieving audit events

    public Page<AuditEvent> findAllEvents(Pageable pageable) {
        return auditEventRepository.findAll(Objects.requireNonNull(pageable));
    }

    public Page<AuditEvent> findEventsByPrincipal(String principal, Pageable pageable) {
        return auditEventRepository.findByPrincipalOrderByTimestampDesc(principal, Objects.requireNonNull(pageable));
    }

    public Page<AuditEvent> findEventsByType(AuditEventType type, Pageable pageable) {
        return auditEventRepository.findByTypeOrderByTimestampDesc(type, Objects.requireNonNull(pageable));
    }

    public Page<AuditEvent> findEventsByTimeRange(Instant start, Instant end, Pageable pageable) {
        return auditEventRepository.findByTimestampBetweenOrderByTimestampDesc(start, end,
                Objects.requireNonNull(pageable));
    }

    public Page<AuditEvent> searchEvents(String searchTerm, Pageable pageable) {
        return auditEventRepository.searchAuditEvents(searchTerm, Objects.requireNonNull(pageable));
    }
}