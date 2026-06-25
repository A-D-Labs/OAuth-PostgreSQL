package com.template.OAuth.service;

import dev.samstevens.totp.code.CodeGenerator;
import dev.samstevens.totp.code.CodeVerifier;
import dev.samstevens.totp.code.DefaultCodeGenerator;
import dev.samstevens.totp.code.DefaultCodeVerifier;
import dev.samstevens.totp.code.HashingAlgorithm;
import dev.samstevens.totp.qr.QrData;
import dev.samstevens.totp.recovery.RecoveryCodeGenerator;
import dev.samstevens.totp.secret.DefaultSecretGenerator;
import dev.samstevens.totp.secret.SecretGenerator;
import dev.samstevens.totp.time.SystemTimeProvider;
import dev.samstevens.totp.time.TimeProvider;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

/**
 * TOTP (RFC 6238) primitives for MFA: secret generation, otpauth:// provisioning URIs,
 * code verification, and one-time recovery-code generation. Stateless; persistence and
 * hashing live in the callers.
 */
@Service
public class MfaService {

    private static final String ISSUER = "OAuthTemplate";
    private static final int RECOVERY_CODE_COUNT = 10;

    private final SecretGenerator secretGenerator = new DefaultSecretGenerator();
    private final RecoveryCodeGenerator recoveryCodeGenerator = new RecoveryCodeGenerator();
    private final CodeVerifier codeVerifier;

    public MfaService() {
        CodeGenerator codeGenerator = new DefaultCodeGenerator();
        TimeProvider timeProvider = new SystemTimeProvider();
        DefaultCodeVerifier verifier = new DefaultCodeVerifier(codeGenerator, timeProvider);
        // Allow +/- 1 time-step (30s) of clock skew between server and authenticator.
        verifier.setAllowedTimePeriodDiscrepancy(1);
        this.codeVerifier = verifier;
    }

    /** A fresh base32 TOTP secret. */
    public String generateSecret() {
        return secretGenerator.generate();
    }

    /** otpauth:// URI for the authenticator app (render as a QR on the client). */
    public String provisioningUri(String secret, String accountLabel) {
        QrData data = new QrData.Builder()
                .label(accountLabel)
                .secret(secret)
                .issuer(ISSUER)
                .algorithm(HashingAlgorithm.SHA1)
                .digits(6)
                .period(30)
                .build();
        return data.getUri();
    }

    /** True if {@code code} is a currently-valid TOTP for {@code secret}. */
    public boolean verifyCode(String secret, String code) {
        return secret != null && code != null && codeVerifier.isValidCode(secret, code);
    }

    /** A fresh set of raw recovery codes (caller hashes + persists; shown to the user once). */
    public List<String> generateRecoveryCodes() {
        return Arrays.asList(recoveryCodeGenerator.generateCodes(RECOVERY_CODE_COUNT));
    }
}
