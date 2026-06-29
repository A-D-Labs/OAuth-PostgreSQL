package com.template.OAuth.controller;

import com.template.OAuth.config.JwtTokenProvider;
import com.template.OAuth.dto.SessionDto;
import com.template.OAuth.entities.User;
import com.template.OAuth.service.RefreshTokenService;
import com.template.OAuth.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Per-device session management for the authenticated user (multi-device, ADR-0007).
 * Lists the user's active sessions and revokes a single device, leaving the others intact.
 */
@RestController
@RequestMapping("/api/user/sessions")
public class SessionController {

    private final UserService userService;
    private final RefreshTokenService refreshTokenService;
    private final JwtTokenProvider jwtTokenProvider;

    public SessionController(UserService userService,
                            RefreshTokenService refreshTokenService,
                            JwtTokenProvider jwtTokenProvider) {
        this.userService = userService;
        this.refreshTokenService = refreshTokenService;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    /** List the caller's active sessions, flagging the one this request authenticated with. */
    @GetMapping
    public List<SessionDto> listSessions() {
        User user = userService.getCurrentUser();
        return refreshTokenService.listSessions(user, currentSessionId());
    }

    /**
     * Revoke one of the caller's own sessions. Returns 204 on success; 404 if the session does not
     * exist or belongs to another user (existence is not leaked across owners).
     */
    @DeleteMapping("/{sessionId}")
    public ResponseEntity<Void> revokeSession(@PathVariable String sessionId) {
        User user = userService.getCurrentUser();
        boolean revoked = refreshTokenService.revokeSession(user, sessionId);
        return revoked ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }

    /** The sid claim on the access token that authenticated this request, or null if absent. */
    private String currentSessionId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getCredentials() instanceof String token) {
            return jwtTokenProvider.getSessionId(token);
        }
        return null;
    }
}
