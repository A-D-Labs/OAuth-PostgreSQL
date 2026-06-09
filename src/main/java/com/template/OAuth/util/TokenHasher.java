package com.template.OAuth.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Hashes opaque security tokens (refresh, email-verification, password-reset) before they
 * are persisted. Tokens are looked up by their hash, so a read-only database leak (backup,
 * snapshot, SQLi elsewhere) does not hand an attacker usable tokens. SHA-256 is appropriate
 * here because the inputs are already high-entropy random values, not low-entropy passwords.
 */
public final class TokenHasher {

    private TokenHasher() {
    }

    public static String sha256Hex(String rawToken) {
        if (rawToken == null) {
            throw new IllegalArgumentException("rawToken must not be null");
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is mandated by the JLS to be present on every JVM.
            throw new IllegalStateException("SHA-256 algorithm unavailable", e);
        }
    }
}
