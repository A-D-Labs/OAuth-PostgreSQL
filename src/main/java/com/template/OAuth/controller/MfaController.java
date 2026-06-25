package com.template.OAuth.controller;

import com.template.OAuth.config.JwtTokenProvider;
import com.template.OAuth.dto.AuthResponse;
import com.template.OAuth.dto.MfaActivateRequest;
import com.template.OAuth.dto.MfaEnrollResponse;
import com.template.OAuth.entities.MfaRecoveryCode;
import com.template.OAuth.entities.User;
import com.template.OAuth.enums.AuditEventType;
import com.template.OAuth.repositories.MfaRecoveryCodeRepository;
import com.template.OAuth.service.AuditService;
import com.template.OAuth.service.MfaService;
import com.template.OAuth.service.SessionIssuer;
import com.template.OAuth.service.UserService;
import com.template.OAuth.util.TokenHasher;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/auth/mfa")
public class MfaController {

    private final MfaService mfaService;
    private final UserService userService;
    private final MfaRecoveryCodeRepository recoveryCodeRepository;
    private final AuditService auditService;
    private final SessionIssuer sessionIssuer;
    private final JwtTokenProvider jwtTokenProvider;

    public MfaController(MfaService mfaService, UserService userService,
                         MfaRecoveryCodeRepository recoveryCodeRepository, AuditService auditService,
                         SessionIssuer sessionIssuer, JwtTokenProvider jwtTokenProvider) {
        this.mfaService = mfaService;
        this.userService = userService;
        this.recoveryCodeRepository = recoveryCodeRepository;
        this.auditService = auditService;
        this.sessionIssuer = sessionIssuer;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    /**
     * Begin MFA enrolment: mint a TOTP secret + a fresh set of recovery codes. MFA is NOT
     * active until {@link #activate} succeeds. Returns the secret, an otpauth:// URI, and the
     * raw recovery codes (shown once).
     */
    @PostMapping("/enroll")
    @Transactional
    public MfaEnrollResponse enroll() {
        User user = userService.getCurrentUser();

        String secret = mfaService.generateSecret();
        user.setTotpSecret(secret);
        user.setMfaEnabled(false);

        // Replace any prior recovery codes with a fresh set.
        recoveryCodeRepository.deleteAllByUser(user);
        List<String> rawCodes = mfaService.generateRecoveryCodes();
        for (String raw : rawCodes) {
            MfaRecoveryCode code = new MfaRecoveryCode();
            code.setUser(user);
            code.setCodeHash(TokenHasher.sha256Hex(raw));
            recoveryCodeRepository.save(code);
        }
        userService.saveUser(user);

        auditService.logEvent(AuditEventType.USER_UPDATED, "MFA enrolment started", "User: " + user.getEmail());
        return new MfaEnrollResponse(secret, mfaService.provisioningUri(secret, user.getEmail()), rawCodes);
    }

    /** Activate MFA by proving possession of the authenticator (a current TOTP code). */
    @PostMapping("/activate")
    @Transactional
    public ResponseEntity<Map<String, String>> activate(@RequestBody MfaActivateRequest request) {
        User user = userService.getCurrentUser();

        if (user.getTotpSecret() == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "MFA is not enrolled; call /auth/mfa/enroll first"));
        }
        if (!mfaService.verifyCode(user.getTotpSecret(), request.code())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Invalid MFA code"));
        }

        user.setMfaEnabled(true);
        userService.saveUser(user);
        auditService.logEvent(AuditEventType.USER_UPDATED, "MFA activated", "User: " + user.getEmail());
        return ResponseEntity.ok(Map.of("status", "MFA activated"));
    }

    /**
     * Second factor at login: present a valid TOTP code (or an unused recovery code) against the
     * MFA challenge cookie issued after the first factor. On success, a full session is issued.
     */
    @PostMapping("/verify")
    @Transactional
    public ResponseEntity<?> verify(@RequestBody MfaActivateRequest request,
                                    HttpServletRequest httpRequest, HttpServletResponse response) {
        String challenge = readCookie(httpRequest, SessionIssuer.CHALLENGE_COOKIE);
        if (challenge == null || !jwtTokenProvider.isMfaChallengeToken(challenge)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Missing or expired MFA challenge"));
        }

        String email = jwtTokenProvider.getEmailFromToken(challenge);
        User user = userService.findUserByEmail(email);

        boolean ok = mfaService.verifyCode(user.getTotpSecret(), request.code())
                || consumeRecoveryCode(user, request.code());
        if (!ok) {
            auditService.logEvent(AuditEventType.LOGIN_FAILURE, "MFA verification failed", "User: " + email);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Invalid MFA code"));
        }

        sessionIssuer.issueSession(user, response);
        sessionIssuer.clearChallenge(response);
        auditService.logEvent(AuditEventType.LOGIN_SUCCESS, "MFA verification succeeded", "User: " + email);
        return ResponseEntity.ok(new AuthResponse(null, "MFA verification succeeded"));
    }

    /** Consume a one-time recovery code (hash match, owned by user, unused). */
    private boolean consumeRecoveryCode(User user, String rawCode) {
        if (rawCode == null) {
            return false;
        }
        Optional<MfaRecoveryCode> match = recoveryCodeRepository.findByCodeHash(TokenHasher.sha256Hex(rawCode));
        if (match.isEmpty()) {
            return false;
        }
        MfaRecoveryCode code = match.get();
        if (code.getUsedAt() != null || !code.getUser().getId().equals(user.getId())) {
            return false;
        }
        code.setUsedAt(Instant.now());
        recoveryCodeRepository.save(code);
        return true;
    }

    private String readCookie(HttpServletRequest request, String name) {
        if (request.getCookies() == null) {
            return null;
        }
        return Arrays.stream(request.getCookies())
                .filter(c -> name.equals(c.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .orElse(null);
    }
}
