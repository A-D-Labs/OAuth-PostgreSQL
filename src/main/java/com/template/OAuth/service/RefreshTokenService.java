package com.template.OAuth.service;

import com.template.OAuth.config.AppProperties;
import com.template.OAuth.config.JwtTokenProvider;
import com.template.OAuth.config.RefreshTokenProvider;
import com.template.OAuth.dto.RefreshTokenResponse;
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
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

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
        RefreshToken rt = refreshTokenRepository.findByUser(user).orElseGet(() -> {
            RefreshToken created = new RefreshToken();
            created.setUser(user);
            return created;
        });

        String rawToken = refreshTokenProvider.generateRefreshToken();
        rt.setToken(TokenHasher.sha256Hex(rawToken));
        // A fresh login starts a new token family — clear any rotation history.
        rt.setPreviousToken(null);
        rt.setExpiryDate(Instant.now().plusMillis(appProperties.getSecurity().getRefresh().getExpiration()));

        RefreshToken saved = refreshTokenRepository.save(rt);
        // The raw token is what the client receives; only its hash is persisted.
        saved.setRawToken(rawToken);
        return saved;
    }

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

        // Generate new JWT
        String newAccessToken = jwtTokenProvider.generateToken(refreshToken.getUser().getEmail());

        // Rotate refresh token (store the hash, hand the raw value to the client). Remember the
        // hash we just consumed so a later replay of it is detected as reuse.
        String newRefreshToken = refreshTokenProvider.generateRefreshToken();
        refreshToken.setPreviousToken(presentedHash);
        refreshToken.setToken(TokenHasher.sha256Hex(newRefreshToken));
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
            // Either is fine; keep both available in the repo
            refreshTokenRepository.deleteAllByUser(user);
        });
    }
}
