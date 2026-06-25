package com.template.OAuth.controller;

import com.template.OAuth.dto.MfaActivateRequest;
import com.template.OAuth.dto.MfaEnrollResponse;
import com.template.OAuth.entities.MfaRecoveryCode;
import com.template.OAuth.entities.User;
import com.template.OAuth.enums.AuditEventType;
import com.template.OAuth.repositories.MfaRecoveryCodeRepository;
import com.template.OAuth.service.AuditService;
import com.template.OAuth.service.MfaService;
import com.template.OAuth.service.UserService;
import com.template.OAuth.util.TokenHasher;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/auth/mfa")
public class MfaController {

    private final MfaService mfaService;
    private final UserService userService;
    private final MfaRecoveryCodeRepository recoveryCodeRepository;
    private final AuditService auditService;

    public MfaController(MfaService mfaService, UserService userService,
                         MfaRecoveryCodeRepository recoveryCodeRepository, AuditService auditService) {
        this.mfaService = mfaService;
        this.userService = userService;
        this.recoveryCodeRepository = recoveryCodeRepository;
        this.auditService = auditService;
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
}
