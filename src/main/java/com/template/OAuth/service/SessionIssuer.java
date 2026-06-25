package com.template.OAuth.service;

import com.template.OAuth.config.AppProperties;
import com.template.OAuth.config.JwtTokenProvider;
import com.template.OAuth.entities.RefreshToken;
import com.template.OAuth.entities.User;
import com.template.OAuth.util.CookieUtil;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Service;

/**
 * Single chokepoint for turning an authenticated {@link User} into a session (or, when MFA
 * is active, an MFA challenge). Used by the password-login path, the OAuth2 success handler,
 * and the MFA verify endpoint so the three never diverge.
 */
@Service
public class SessionIssuer {

    /** How long the one-time MFA challenge cookie lives (seconds). */
    public static final long CHALLENGE_TTL_SECONDS = 300; // 5 minutes
    public static final String CHALLENGE_COOKIE = "mfa_challenge";

    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenService refreshTokenService;
    private final AppProperties appProperties;

    public SessionIssuer(JwtTokenProvider jwtTokenProvider,
                         RefreshTokenService refreshTokenService,
                         AppProperties appProperties) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.refreshTokenService = refreshTokenService;
        this.appProperties = appProperties;
    }

    /**
     * Issue a full session (access JWT + rotating refresh token cookies). Call this only
     * after ALL factors are satisfied.
     */
    public void issueSession(User user, HttpServletResponse response) {
        String token = jwtTokenProvider.generateToken(user.getEmail());
        RefreshToken refreshToken = refreshTokenService.generateRefreshToken(user);

        long accessTtlSeconds = appProperties.getSecurity().getJwt().getExpiration() / 1000;
        long refreshTtlSeconds = appProperties.getSecurity().getRefresh().getExpiration() / 1000;

        CookieUtil.addCookie(response, "jwt", token, "/", accessTtlSeconds, appProperties);
        CookieUtil.addCookie(response, "refresh_token", refreshToken.getRawToken(),
                "/refresh-token", refreshTtlSeconds, appProperties);
    }

    /** Issue the short-lived one-time MFA challenge cookie (no session yet). */
    public void issueChallenge(User user, HttpServletResponse response) {
        String challenge = jwtTokenProvider.generateMfaChallengeToken(user.getEmail());
        CookieUtil.addCookie(response, CHALLENGE_COOKIE, challenge, "/auth/mfa",
                CHALLENGE_TTL_SECONDS, appProperties);
    }

    /** Expire the MFA challenge cookie once the second factor is satisfied (or on failure). */
    public void clearChallenge(HttpServletResponse response) {
        CookieUtil.addCookie(response, CHALLENGE_COOKIE, "", "/auth/mfa", 0, appProperties);
    }

    /**
     * Issue a session, or an MFA challenge if the user has MFA active.
     * @return true if an MFA challenge was issued (second factor still required).
     */
    public boolean issueSessionOrChallenge(User user, HttpServletResponse response) {
        if (user.isMfaEnabled()) {
            issueChallenge(user, response);
            return true;
        }
        issueSession(user, response);
        return false;
    }
}
