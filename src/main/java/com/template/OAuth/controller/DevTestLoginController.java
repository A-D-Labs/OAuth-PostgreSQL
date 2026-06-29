package com.template.OAuth.controller;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Dev-only manual test harness for the OAuth login flows (Google + Microsoft).
 *
 * <p>Registered ONLY under the {@code dev} profile, so it is never present in {@code test}
 * or {@code prod}. It serves a single self-contained page that initiates the provider login
 * and shows the resulting {@code User} + active sessions — a place to eyeball that a real
 * Google/Microsoft sign-in round-trips end-to-end (the one step a headless build cannot do
 * for you). See docs/dev-oauth-test-harness.md.
 */
@Controller
@Profile("dev")
public class DevTestLoginController {

    @GetMapping("/dev/test-login")
    public String testLogin() {
        return "dev-test-login";
    }
}
