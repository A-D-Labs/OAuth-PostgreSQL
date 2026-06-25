package com.template.OAuth.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey; // NOTE: javax, not jakarta
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class JwtTokenProvider {

    /** Minimum key length for HS256: 256 bits / 8 = 32 bytes. */
    private static final int MIN_SECRET_BYTES = 32;

    /** The insecure built-in default that older configs shipped — must never be used. */
    private static final String INSECURE_DEFAULT_SECRET = "defaultsecretkey12345678901234567890";

    @Value("${app.security.jwt.secret}")
    private String jwtSecret;

    private final UserDetailsService userDetailsService;
    private final AppProperties appProperties;

    public JwtTokenProvider(UserDetailsService userDetailsService, AppProperties appProperties) {
        this.userDetailsService = userDetailsService;
        this.appProperties = appProperties;
    }

    /**
     * Fail fast at startup rather than silently booting with a forgeable signing key.
     * A blank, too-short, or known-default secret lets anyone mint valid (even admin)
     * tokens, which is a full authentication bypass.
     */
    @PostConstruct
    void validateSecret() {
        if (jwtSecret == null || jwtSecret.isBlank()) {
            throw new IllegalStateException(
                    "JWT secret is not configured. Set the JWT_SECRET environment variable to a strong, "
                            + "random value of at least " + MIN_SECRET_BYTES + " bytes.");
        }
        if (INSECURE_DEFAULT_SECRET.equals(jwtSecret)) {
            throw new IllegalStateException(
                    "JWT secret is set to the insecure built-in default. Set JWT_SECRET to a strong, "
                            + "random value of at least " + MIN_SECRET_BYTES + " bytes.");
        }
        int byteLength = jwtSecret.getBytes(StandardCharsets.UTF_8).length;
        if (byteLength < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "JWT secret is too short (" + byteLength + " bytes). HS256 requires at least "
                            + MIN_SECRET_BYTES + " bytes (256 bits).");
        }
    }

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
    }

    public String generateToken(String email) {
        UserDetails userDetails = userDetailsService.loadUserByUsername(email);

        List<String> roles = userDetails.getAuthorities().stream()
                .map(a -> a.getAuthority())
                .collect(Collectors.toList());

        Date now = new Date();
        Date exp = new Date(now.getTime() + appProperties.getSecurity().getJwt().getExpiration());

        return Jwts.builder()
                .subject(email)
                .id(UUID.randomUUID().toString()) // jti — enables future per-token deny-list (with Redis)
                .claim("roles", roles)
                .issuedAt(now)
                .expiration(exp)
                // 0.12.x: use SecureDigestAlgorithm from Jwts.SIG
                .signWith(getSigningKey(), Jwts.SIG.HS256)
                .compact();
    }

    public String getEmailFromToken(String token) {
        return parseClaims(token).getSubject();
    }

    /** The token's issued-at instant, or null if unreadable. Used for the per-user revocation epoch. */
    public Instant getIssuedAt(String token) {
        try {
            Date issued = parseClaims(token).getIssuedAt();
            return issued == null ? null : issued.toInstant();
        } catch (JwtException | IllegalArgumentException e) {
            return null;
        }
    }

    public boolean validateToken(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey()) // 0.12.x verifyWith(SecretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public Optional<String> getTokenFromRequest(HttpServletRequest request) {
        if (request.getCookies() != null) {
            return Arrays.stream(request.getCookies())
                    .filter(cookie -> "jwt".equals(cookie.getName()))
                    .map(Cookie::getValue)
                    .findFirst();
        }
        return Optional.empty();
    }
}
