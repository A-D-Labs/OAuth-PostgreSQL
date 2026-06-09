package com.template.OAuth.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.template.OAuth.dto.EmailLoginRequest;
import com.template.OAuth.dto.EmailRegistrationRequest;
import com.template.OAuth.entities.RefreshToken;
import com.template.OAuth.entities.User;
import com.template.OAuth.repositories.RefreshTokenRepository;
import com.template.OAuth.repositories.UserRepository;
import com.template.OAuth.service.EmailService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * E2E happy path:
 *   Register -> Verify (redirect) -> Login (cookies) -> Refresh (rotation + cookies)
 *
 * Notes:
 *  - App returns auth via HttpOnly cookies ("jwt", "refresh_token"), not JSON "token".
 *  - Email sending is mocked to avoid SMTP attempts during tests.
 *  - Uses H2 via application-test.yaml.
 */
import com.template.OAuth.BaseIntegrationTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@ActiveProfiles("test")
class AuthFlowIT extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    // Mock async emails (verification/welcome) to avoid SMTP in tests
    @MockitoBean
    private EmailService emailService;

    private String email;
    private String password;
    private String name;

    @BeforeEach
    void cleanAndArrange() {
        email = "flow_it@example.com";
        password = "Password123!";
        name = "Flow IT";
    }

    @Test
    void register_verify_login_refresh_success() throws Exception {
        // === 1) REGISTER ===
        EmailRegistrationRequest reg = new EmailRegistrationRequest();
        reg.setEmail(email);
        reg.setName(name);
        reg.setPassword(password);

        mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(reg)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.email").value(email));

        // DB state after registration
        User stored = userRepository.findByEmail(email).orElseThrow();
        assertThat(stored.isEnabled()).isFalse();
        assertThat(stored.getVerificationToken()).isNotBlank();
        assertThat(stored.getVerificationTokenExpiry()).isAfter(Instant.now());

        // === 2) VERIFY (expects redirect to frontend) ===
        // DB stores only the token hash; capture the RAW token from the verification email.
        ArgumentCaptor<String> tokenCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendVerificationEmail(eq(email), anyString(), tokenCaptor.capture());
        String token = tokenCaptor.getValue();
        mockMvc.perform(get("/auth/verify-email").param("token", token))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", containsString("/login?verified=1")));

        // After verify: enabled, token cleared (or otherwise unusable)
        User verified = userRepository.findByEmail(email).orElseThrow();
        assertThat(verified.isEnabled()).isTrue();
        assertThat(verified.getVerificationToken()).isNull();

        // === 3) LOGIN (email/password) -> cookies issued ===
        EmailLoginRequest login = new EmailLoginRequest();
        login.setEmail(email);
        login.setPassword(password);

        MvcResult loginResult = mockMvc.perform(post("/auth/email-login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(login)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Login successful"))
                .andExpect(cookie().exists("jwt"))
                .andExpect(cookie().exists("refresh_token"))
                .andReturn();

        Cookie jwtCookie = extractCookie(loginResult, "jwt");
        assertThat(jwtCookie).isNotNull();
        assertThat(jwtCookie.isHttpOnly()).isTrue();
        assertThat(jwtCookie.getPath()).isEqualTo("/"); // adjust if your config differs

        Cookie refreshCookie = extractCookie(loginResult, "refresh_token");
        assertThat(refreshCookie).isNotNull();
        assertThat(refreshCookie.isHttpOnly()).isTrue();
        assertThat(refreshCookie.getPath()).isEqualTo("/refresh-token");

        // DB has a refresh token for the user
        Optional<User> afterLoginUser = userRepository.findByEmail(email);
        assertThat(afterLoginUser).isPresent();
        Optional<RefreshToken> storedRT = refreshTokenRepository.findByUser(afterLoginUser.get());
        assertThat(storedRT).isPresent();
        String originalRefreshValue = storedRT.get().getToken();

        // === 4) REFRESH -> rotation + new cookies ===
        MvcResult refreshResult = mockMvc.perform(post("/refresh-token")
                .cookie(new Cookie("refresh_token", refreshCookie.getValue())))
                .andExpect(status().isOk())
                .andExpect(cookie().exists("jwt"))
                .andExpect(cookie().exists("refresh_token"))
                .andReturn();

        Cookie rotatedRefreshCookie = extractCookie(refreshResult, "refresh_token");
        assertThat(rotatedRefreshCookie).isNotNull();
        assertThat(rotatedRefreshCookie.getValue()).isNotBlank();
        assertThat(rotatedRefreshCookie.isHttpOnly()).isTrue();
        assertThat(rotatedRefreshCookie.getPath()).isEqualTo("/refresh-token");

        Cookie newJwtCookie = extractCookie(refreshResult, "jwt");
        assertThat(newJwtCookie).isNotNull();
        assertThat(newJwtCookie.getValue()).isNotBlank();
        assertThat(newJwtCookie.isHttpOnly()).isTrue();
        assertThat(newJwtCookie.getPath()).isEqualTo("/");

        // DB shows rotation (stored refresh token changed + expiry in the future)
        RefreshToken afterRefresh = refreshTokenRepository.findByUser(afterLoginUser.get()).orElseThrow();
        assertThat(afterRefresh.getToken()).isNotEqualTo(originalRefreshValue);
        assertThat(afterRefresh.getExpiryDate()).isAfter(Instant.now());
    }

    // Helper: find a cookie by name in MockMvc response
    private static Cookie extractCookie(MvcResult result, String name) {
        for (Cookie c : result.getResponse().getCookies()) {
            if (name.equals(c.getName()))
                return c;
        }
        return null;
    }
}
