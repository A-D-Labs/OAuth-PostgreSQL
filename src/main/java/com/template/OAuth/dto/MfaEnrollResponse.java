package com.template.OAuth.dto;

import java.util.List;

/**
 * Returned once at MFA enrolment. The raw recovery codes are shown here and never again
 * (only their hashes are stored). The client renders {@code otpauthUri} as a QR code.
 */
public record MfaEnrollResponse(String secret, String otpauthUri, List<String> recoveryCodes) {
}
