package com.template.OAuth.service;

import com.template.OAuth.config.AppProperties;
import com.template.OAuth.config.JwtTokenProvider;
import com.template.OAuth.config.RefreshTokenProvider;
import com.template.OAuth.dto.RefreshTokenResponse;
import com.template.OAuth.dto.SessionDto;
import com.template.OAuth.entities.RefreshToken;
import com.template.OAuth.entities.User;
import com.template.OAuth.enums.AuditEventType;
import com.template.OAuth.exception.InvalidRefreshTokenException;
import com.template.OAuth.exception.RefreshTokenExpiredException;
import com.template.OAuth.exception.TokenReuseException;
import com.template.OAuth.repositories.RefreshTokenRepository;
import com.template.OAuth.repositories.UserRepository;
import com.template.OAuth.util.CookieUtil;
import com.template.OAuth.util.TokenHasher;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class RefreshTokenService {

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private RefreshTokenProvider refreshTokenProvider;

    @Autowired
    private AppProperties appProperties;

    @Autowired
    private AuditService auditService;

    @Transactional
    public RefreshToken generateRefreshToken(User user) {
        // Multi-device (ADR-0007): each login creates its OWN session row, so devices coexist
        // instead of evicting each other. Capture device metadata from the current web request.
        RefreshToken rt = new RefreshToken();
        rt.setUser(user);
        rt.setSessionId(UUID.randomUUID().toString());

        String rawToken = refreshTokenProvider.generateRefreshToken();
        rt.setToken(TokenHasher.sha256Hex(rawToken));
        rt.setPreviousToken(null);

        Instant now = Instant.now();
        rt.setCreatedAt(now);      // family origin — absolute-lifetime cap anchors here
        rt.setLastUsedAt(now);
        DeviceInfo device = currentDevice();
        rt.setUserAgent(device.userAgent());
        rt.setIpAddress(device.ipAddress());
        rt.setExpiryDate(now.plusMillis(appProperties.getSecurity().getRefresh().getExpiration()));

        RefreshToken saved = refreshTokenRepository.save(rt);
        // The raw token is what the client receives; only its hash is persisted.
        saved.setRawToken(rawToken);
        return saved;
    }

    /** Device metadata (user-agent, client IP) read from the current web request, if any. */
    private DeviceInfo currentDevice() {
        try {
            ServletRequestAttributes attrs =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                HttpServletRequest req = attrs.getRequest();
                String ua = req.getHeader("User-Agent");
                if (ua != null && ua.length() > 512) {
                    ua = ua.substring(0, 512);
                }
                String forwarded = req.getHeader("X-Forwarded-For");
                String ip = (forwarded != null && !forwarded.isBlank())
                        ? forwarded.split(",")[0].trim()
                        : req.getRemoteAddr();
                return new DeviceInfo(ua, ip);
            }
        } catch (IllegalStateException ignored) {
            // Not in a request context (e.g. unit tests calling the service directly).
        }
        return new DeviceInfo(null, null);
    }

    private record DeviceInfo(String userAgent, String ipAddress) {}

    @Transactional
    public RefreshTokenResponse refreshToken(String oldRefreshToken, HttpServletResponse response) {
        // Tokens are stored hashed; look up by the hash of the presented raw token.
        String presentedHash = TokenHasher.sha256Hex(oldRefreshToken);
        Optional<RefreshToken> refreshTokenOpt = refreshTokenRepository.findByToken(presentedHash);

        if (refreshTokenOpt.isEmpty()) {
            // Not the current token. If it matches the token we just rotated away, it is a
            // replay of a consumed token — the hallmark of a stolen refresh token. Revoke the
            // entire family so neither the attacker's rotated token nor this one survives.
            Optional<RefreshToken> reusedOpt = refreshTokenRepository.findByPreviousToken(presentedHash);
            if (reusedOpt.isPresent()) {
                RefreshToken compromised = reusedOpt.get();
                String email = compromised.getUser().getEmail();
                refreshTokenRepository.delete(compromised);
                auditService.logEvent(
                        AuditEventType.ACCESS_DENIED,
                        "Refresh token reuse detected; all sessions for the user were revoked",
                        "User: " + email);
                throw new TokenReuseException("Refresh token reuse detected for user " + email);
            }
            throw new InvalidRefreshTokenException("Refresh token not recognized");
        }

        RefreshToken refreshToken = refreshTokenOpt.get();

        if (refreshToken.getExpiryDate().isBefore(Instant.now())) {
            refreshTokenRepository.delete(refreshToken);
            throw new RefreshTokenExpiredException("Refresh token has expired");
        }

        // Absolute-lifetime cap: a family cannot be renewed forever. Past the cap, force re-auth.
        long absoluteMs = appProperties.getSecurity().getRefresh().getAbsoluteExpiration();
        if (refreshToken.getCreatedAt() != null
                && refreshToken.getCreatedAt().plusMillis(absoluteMs).isBefore(Instant.now())) {
            refreshTokenRepository.delete(refreshToken);
            throw new RefreshTokenExpiredException(
                    "Refresh token exceeded its absolute lifetime; please log in again");
        }

        // Generate new JWT, re-binding it to the same session (ADR-0007) so the sid survives rotation.
        String newAccessToken = jwtTokenProvider.generateToken(
                refreshToken.getUser().getEmail(), refreshToken.getSessionId());

        // Rotate refresh token (store the hash, hand the raw value to the client). Remember the
        // hash we just consumed so a later replay of it is detected as reuse.
        String newRefreshToken = refreshTokenProvider.generateRefreshToken();
        refreshToken.setPreviousToken(presentedHash);
        refreshToken.setToken(TokenHasher.sha256Hex(newRefreshToken));
        refreshToken.setLastUsedAt(Instant.now());
        refreshToken.setExpiryDate(Instant.now().plusMillis(appProperties.getSecurity().getRefresh().getExpiration()));
        refreshTokenRepository.save(refreshToken);

        // Set cookies (HttpOnly/SameSite/Secure handled centrally)
        long accessTtlSeconds  = appProperties.getSecurity().getJwt().getExpiration() / 1000;
        long refreshTtlSeconds = appProperties.getSecurity().getRefresh().getExpiration() / 1000;

        CookieUtil.addCookie(response, "jwt", newAccessToken, "/", accessTtlSeconds, appProperties);
        CookieUtil.addCookie(response, "refresh_token", newRefreshToken, "/refresh-token", refreshTtlSeconds, appProperties);

        return new RefreshTokenResponse(newAccessToken, newRefreshToken, "Token refreshed successfully");
    }

    @Transactional
    public void revokeAllForUser(String email) {
        userRepository.findByEmail(email).ifPresent(user -> {
            // Admin forced-logout / ban: drop ALL of the user's sessions.
            refreshTokenRepository.deleteAllByUser(user);
        });
    }

    /**
     * The user's active sessions/devices (multi-device, ADR-0007), newest-first, with the caller's
     * current session flagged via {@code currentSessionId} (the sid claim on the access token).
     */
    @Transactional(readOnly = true)
    public List<SessionDto> listSessions(User user, String currentSessionId) {
        return refreshTokenRepository.findByUser(user).stream()
                .sorted(Comparator.comparing(RefreshToken::getCreatedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .map(rt -> new SessionDto(
                        rt.getSessionId(),
                        rt.getUserAgent(),
                        rt.getIpAddress(),
                        rt.getCreatedAt(),
                        rt.getLastUsedAt(),
                        rt.getSessionId().equals(currentSessionId)))
                .toList();
    }

    /** Revoke a single session/device owned by the user (multi-device, ADR-0007). */
    @Transactional
    public boolean revokeSession(User user, String sessionId) {
        return refreshTokenRepository.findBySessionId(sessionId)
                .filter(rt -> rt.getUser().getId().equals(user.getId()))
                .map(rt -> {
                    refreshTokenRepository.delete(rt);
                    return true;
                })
                .orElse(false);
    }
}
