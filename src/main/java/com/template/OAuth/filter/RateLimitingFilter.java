package com.template.OAuth.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.template.OAuth.enums.AuditEventType;
import com.template.OAuth.service.AuditService;
import com.template.OAuth.service.MetricsService;
import com.template.OAuth.service.RateLimitService;
import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull; // <-- added
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.Objects;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RateLimitingFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(RateLimitingFilter.class);

    private final RateLimitService rateLimitService;
    private final AuditService auditService;
    private final MetricsService metricsService;
    private final ObjectMapper objectMapper;
    private final Environment environment;

    // Pattern for authentication endpoints
    private static final Pattern AUTH_ENDPOINTS = Pattern.compile("^/(auth|oauth2|login|refresh-token)/.*$");

    // Pattern for sensitive operations (admin, password changes, etc.)
    private static final Pattern SENSITIVE_ENDPOINTS = Pattern
            .compile("^/(api/admin|api/moderator|api/user/password)/.*$");

    // Pattern for static resources to skip rate limiting
    private static final Pattern STATIC_RESOURCES = Pattern
            .compile("^/(swagger-ui|v3/api-docs|favicon.ico|error|css|js|images)/.*$");

    public RateLimitingFilter(
            RateLimitService rateLimitService,
            AuditService auditService,
            MetricsService metricsService,
            ObjectMapper objectMapper,
            Environment environment) {
        this.rateLimitService = rateLimitService;
        this.auditService = auditService;
        this.metricsService = metricsService;
        this.objectMapper = objectMapper;
        this.environment = environment;
    }

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        String path = request.getRequestURI();

        // Skip rate limiting for static resources and main page
        if (STATIC_RESOURCES.matcher(path).matches() || path.equals("/")) {
            return true;
        }

        // Skip rate limiting for test environment
        if (isTestEnvironment()) {
            return true;
        }

        return false;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        // If we're in test environment, skip rate limiting
        if (isTestEnvironment()) {
            filterChain.doFilter(request, response);
            return;
        }

        String uri = request.getRequestURI();
        String path = uri != null ? uri : "";
        String method = request.getMethod();
        String clientIp = Objects.requireNonNull(getClientIP(request));
        String requestId = generateRequestId();

        // Add request ID to response headers for tracking
        response.setHeader("X-Request-ID", requestId);

        // Log request information with low verbosity for normal requests
        if (logger.isDebugEnabled()) {
            logger.debug("Request received - ID: {}, Method: {}, Path: {}, IP: {}",
                    requestId, method, path, clientIp);
        }

        Bucket bucket;
        long remainingTokens;
        String bucketType = "standard";

        // Apply different rate limits based on endpoint type
        if (AUTH_ENDPOINTS.matcher(path).matches()) {
            bucket = rateLimitService.resolveBucketForAuthRequest(Objects.requireNonNull(clientIp));
            remainingTokens = rateLimitService.checkRateLimit(bucket, 1);
            bucketType = "auth";

            if (remainingTokens < 0) {
                handleRateLimitExceeded(response, requestId, clientIp, path, bucketType);
                return;
            }
        } else if (SENSITIVE_ENDPOINTS.matcher(path).matches()) {
            bucket = rateLimitService.resolveBucketForSensitiveOperation(Objects.requireNonNull(clientIp));
            remainingTokens = rateLimitService.checkRateLimit(bucket, 1);
            bucketType = "sensitive";

            if (remainingTokens < 0) {
                handleRateLimitExceeded(response, requestId, clientIp, path, bucketType);
                return;
            }
        } else {
            // Default API rate limiting
            bucket = rateLimitService.resolveBucketForEndpoint(Objects.requireNonNull(path));
            remainingTokens = rateLimitService.checkRateLimit(bucket, 1);
            bucketType = "standard";

            if (remainingTokens < 0) {
                handleRateLimitExceeded(response, requestId, clientIp, path, bucketType);
                return;
            }
        }

        // Add rate limit headers
        response.addHeader("X-Rate-Limit-Remaining", String.valueOf(remainingTokens));
        response.addHeader("X-Rate-Limit-Type", bucketType);

        // Log remaining tokens for debugging
        if (logger.isDebugEnabled() && remainingTokens < 10) {
            logger.debug("Rate limit nearly reached - ID: {}, IP: {}, Path: {}, Bucket: {}, Remaining: {}",
                    requestId, clientIp, path, bucketType, remainingTokens);
        }

        // Update metrics
        metricsService.incrementCustomCounter("rate.limit.request",
                "bucket", bucketType,
                "path", path,
                "method", method);

        // Continue with the request
        filterChain.doFilter(request, response);
    }

    private void handleRateLimitExceeded(
            HttpServletResponse response,
            String requestId,
            String clientIp,
            String path,
            String bucketType) throws IOException {

        // Log rate limit exceeded with high severity
        logger.warn("Rate limit exceeded - ID: {}, IP: {}, Path: {}, Bucket: {}",
                requestId, clientIp, path, bucketType);

        // Record audit event
        auditService.logEvent(AuditEventType.ACCESS_DENIED,
                "Rate limit exceeded",
                String.format("Request ID: %s, IP: %s, Path: %s, Type: %s", requestId, clientIp, path, bucketType),
                "RATE_LIMITED");

        // Update metrics
        metricsService.incrementCustomCounter("rate.limit.exceeded",
                "bucket", bucketType,
                "path", path);
        metricsService.incrementError("rate_limit");

        // Determine appropriate error message
        String message, details;

        switch (bucketType) {
            case "auth":
                message = "Too many authentication attempts";
                details = "Please try again later. Authentication endpoints have stricter rate limits.";
                break;
            case "sensitive":
                message = "Rate limit exceeded for sensitive operations";
                details = "Please try again later. Administrative operations have stricter rate limits.";
                break;
            default:
                message = "API rate limit exceeded";
                details = "Please try again later. Consider reducing your request frequency.";
                break;
        }

        // Send rate limit response
        rejectRequest(response, message, details, HttpStatus.TOO_MANY_REQUESTS);
    }

    private void rejectRequest(HttpServletResponse response, String message, String details, HttpStatus status)
            throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        Map<String, String> errorResponse = new HashMap<>();
        errorResponse.put("message", message);
        errorResponse.put("details", details);
        errorResponse.put("status", String.valueOf(status.value()));
        errorResponse.put("error", "TOO_MANY_REQUESTS");

        response.getWriter().write(objectMapper.writeValueAsString(errorResponse));
    }

    @NonNull
    private String getClientIP(HttpServletRequest request) {
        String xfHeader = request.getHeader("X-Forwarded-For");
        if (xfHeader != null) {
            return xfHeader.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null) {
            return realIp;
        }
        String remoteAddr = request.getRemoteAddr();
        return remoteAddr != null ? remoteAddr : "unknown";
    }

    private String generateRequestId() {
        return java.util.UUID.randomUUID().toString().substring(0, 8);
    }

    private boolean isTestEnvironment() {
        return environment != null &&
                Arrays.asList(environment.getActiveProfiles()).contains("test");
    }
}
