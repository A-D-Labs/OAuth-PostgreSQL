package com.template.OAuth.integration;

import com.template.OAuth.BaseIntegrationTest;
import com.template.OAuth.config.OAuth2SuccessHandler;
import com.template.OAuth.entities.User;
import com.template.OAuth.enums.AuthProvider;
import com.template.OAuth.repositories.UserRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.transaction.annotation.Transactional;

import java.net.URL;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Simulates the back half of a real Microsoft OAuth login — the provider has authenticated the
 * user and Spring Security hands an {@link OidcUser} to {@link OAuth2SuccessHandler}. This drives
 * that handler directly (the one thing a headless build CAN prove without a live browser) and
 * asserts the full callback chain: User persisted with a {@code microsoft_id}, mapped to
 * {@link AuthProvider#MICROSOFT}, and a {@code jwt} session cookie issued. The only step this
 * cannot cover is the interactive consent screen itself (real password + MFA), which is verified
 * manually via docs/dev-oauth-test-harness.md.
 */
@Transactional
class OAuth2SuccessHandlerCallbackIT extends BaseIntegrationTest {

    @Autowired
    private OAuth2SuccessHandler successHandler;

    @Autowired
    private UserRepository userRepository;

    @Test
    void microsoftCallbackPersistsUserAndIssuesJwtCookie() throws Exception {
        String email = "ms-callback-user@example.com";
        String subject = "ms-subject-1234";
        String issuer = "https://login.microsoftonline.com/common/v2.0";

        OidcUser principal = mock(OidcUser.class);
        when(principal.getEmail()).thenReturn(email);
        when(principal.getFullName()).thenReturn("MS Callback User");
        when(principal.getPicture()).thenReturn("https://pic/ms");
        when(principal.getSubject()).thenReturn(subject);
        when(principal.getIssuer()).thenReturn(new URL(issuer));

        Authentication authentication = mock(Authentication.class);
        when(authentication.getPrincipal()).thenReturn(principal);

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        successHandler.onAuthenticationSuccess(request, response, authentication);

        // User round-tripped through the rationalized schema as a Microsoft identity.
        User saved = userRepository.findByMicrosoftId(subject).orElseThrow();
        assertThat(saved.getEmail()).isEqualTo(email);
        assertThat(saved.getPrimaryProvider()).isEqualTo(AuthProvider.MICROSOFT);
        assertThat(saved.isEnabled()).isTrue();

        // A real session was issued (SESSION_ISSUED outcome), not an error redirect.
        Cookie jwt = response.getCookie("jwt");
        assertThat(jwt).as("jwt session cookie issued by the success handler").isNotNull();
        assertThat(jwt.getValue()).isNotBlank();
        assertThat(response.getStatus()).isEqualTo(302);
    }
}
