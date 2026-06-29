package com.template.OAuth.service;

import com.template.OAuth.config.AppProperties;
import com.template.OAuth.config.JwtTokenProvider;
import com.template.OAuth.entities.RefreshToken;
import com.template.OAuth.entities.User;
import com.template.OAuth.enums.Role;
import com.template.OAuth.util.CookieUtil;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Single chokepoint for turning an authenticated {@link User} into a session (or, when MFA
 * is active, an MFA challenge). Used by the password-login path, the OAuth2 success handler,
 * and the MFA verify endpoint so the three never diverge.
 */
@Service
public class SessionIssuer {

    /** How long the one-time MFA challenge cookie lives (seconds). */
    public static final long CHALLENGE_TTL_SECONDS = 300; // 5 minutes
    public static final long ENROL_TTL_SECONDS = 600;     // 10 minutes
    public static final String CHALLENGE_COOKIE = "mfa_challenge";

    /** Outcome of a first-factor success: full session, second-factor needed, or enrolment forced. */
    public enum LoginOutcome { SESSION_ISSUED, MFA_CHALLENGE, MFA_ENROLMENT_REQUIRED }

    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenService refreshTokenService;
    private final AppProperties appProperties;

    /** Roles for which MFA is mandatory (configurable; default ADMIN). */
    private final Set<Role> mfaRequiredRoles;

    public SessionIssuer(JwtTokenProvider jwtTokenProvider,
                         RefreshTokenService refreshTokenService,
                         AppProperties appProperties,
                         @Value("${app.security.mfa.required-roles:ADMIN}") String requiredRolesCsv) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.refreshTokenService = refreshTokenService;
        this.appProperties = appProperties;
        this.mfaRequiredRoles = parseRoles(requiredRolesCsv);
    }

    private static Set<Role> parseRoles(String csv) {
        if (csv == null || csv.isBlank()) {
            return EnumSet.noneOf(Role.class);
        }
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(Role::valueOf)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(Role.class)));
    }

    /** True if the user holds a role for which MFA is mandatory. */
    private boolean mfaMandatoryFor(User user) {
        return user.getRoles() != null && user.getRoles().stream().anyMatch(mfaRequiredRoles::contains);
    }

    /**
     * Issue a full session (access JWT + rotating refresh token cookies). Call this only
     * after ALL factors are satisfied.
     */
    public void issueSession(User user, HttpServletResponse response) {
        // Create the session row first so the access token can carry its sid (ADR-0007):
        // the JWT's sid lets "list my sessions" flag the caller's current device.
        RefreshToken refreshToken = refreshTokenService.generateRefreshToken(user);
        String token = jwtTokenProvider.generateToken(user.getEmail(), refreshToken.getSessionId());

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

    /** Issue the restricted enrol-only token (in the jwt cookie); gated to enrol/activate by the filter. */
    public void issueEnrolToken(User user, HttpServletResponse response) {
        String enrolToken = jwtTokenProvider.generateMfaEnrolToken(user.getEmail());
        CookieUtil.addCookie(response, "jwt", enrolToken, "/", ENROL_TTL_SECONDS, appProperties);
    }

    /**
     * Resolve the first-factor success into one of three outcomes:
     * <ul>
     *   <li>MFA active → issue a challenge (second factor required)</li>
     *   <li>MFA mandatory for the user's role but not enrolled → issue an enrol-only token</li>
     *   <li>otherwise → issue a full session</li>
     * </ul>
     */
    public LoginOutcome issueSessionOrChallenge(User user, HttpServletResponse response) {
        if (user.isMfaEnabled()) {
            issueChallenge(user, response);
            return LoginOutcome.MFA_CHALLENGE;
        }
        if (mfaMandatoryFor(user)) {
            issueEnrolToken(user, response);
            return LoginOutcome.MFA_ENROLMENT_REQUIRED;
        }
        issueSession(user, response);
        return LoginOutcome.SESSION_ISSUED;
    }
}
