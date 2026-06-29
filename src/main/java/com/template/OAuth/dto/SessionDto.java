package com.template.OAuth.dto;

import java.time.Instant;

/**
 * One active session/device for the authenticated user (multi-device, ADR-0007). Exposes the
 * device metadata captured at login plus a {@code current} flag marking the caller's own session.
 * The opaque refresh token is never exposed — only the stable {@code sessionId}.
 */
public record SessionDto(
        String sessionId,
        String userAgent,
        String ipAddress,
        Instant createdAt,
        Instant lastUsedAt,
        boolean current) {
}
