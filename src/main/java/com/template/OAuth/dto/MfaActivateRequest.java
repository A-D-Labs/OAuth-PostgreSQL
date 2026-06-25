package com.template.OAuth.dto;

/** A TOTP code submitted to activate (or, later, verify) MFA. */
public record MfaActivateRequest(String code) {
}
