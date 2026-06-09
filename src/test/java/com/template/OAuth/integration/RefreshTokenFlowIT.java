package com.template.OAuth.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.template.OAuth.OAuthApplication;
import com.template.OAuth.dto.EmailRegistrationRequest;
import com.template.OAuth.repositories.UserRepository;
import com.template.OAuth.service.EmailService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.template.OAuth.BaseIntegrationTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(classes = OAuthApplication.class)
@AutoConfigureMockMvc
@Transactional
@ActiveProfiles("test")
class RefreshTokenFlowIT extends BaseIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenFlowIT.class);

    private static final String ACCESS_COOKIE = "jwt";
    private static final String REFRESH_COOKIE = "refresh_token";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private UserRepository userRepository;

    @MockitoBean
    private EmailService emailService;

    private String email;
    private final String password = "OrigPassw0rd!";
    private final String name = "Refresh IT";

    @BeforeEach
    void setUp() {
        // prevent real SMTP
        doNothing().when(emailService).sendVerificationEmail(any(), any(), any());
        doNothing().when(emailService).sendWelcomeEmail(any(), any());
        email = "refresh_flow_it+" + UUID.randomUUID() + "@example.com";
        log.info("Using test email: {}", email);
    }

    @Test
    void initial_login_returns_tokens() throws Exception {
        LoginResponse login = registerVerifyAndLogin();

        assertThat(login.accessToken).isNotBlank();
        assertThat(login.refreshCookie).isNotNull();

        // refresh works using the cookie we got at login
        MvcResult refreshRes = mockMvc.perform(
                post("/refresh-token")
                        .cookie(login.refreshCookie)
                        .contentType(MediaType.APPLICATION_JSON))
                .andReturn();

        assertThat(refreshRes.getResponse().getStatus()).isEqualTo(200);
        JsonNode refreshed = parseJson(refreshRes);
        assertThat(refreshed.path("accessToken").asText(null)).isNotBlank();

        Cookie rotated = getCookieByName(refreshRes, REFRESH_COOKIE);
        assertThat(rotated).isNotNull();
    }

    @Test
    void refresh_rejects_invalid_cookie() throws Exception {
        Cookie bogus = new Cookie(REFRESH_COOKIE, "definitely-invalid");
        bogus.setPath("/");

        MvcResult res = mockMvc.perform(
                post("/refresh-token")
                        .cookie(bogus)
                        .contentType(MediaType.APPLICATION_JSON))
                .andReturn();

        assertThat(res.getResponse().getStatus()).isBetween(400, 499);
    }

    @Test
    void second_refresh_after_rotation_still_works() throws Exception {
        LoginResponse login = registerVerifyAndLogin();

        MvcResult first = mockMvc.perform(
                post("/refresh-token")
                        .cookie(login.refreshCookie)
                        .contentType(MediaType.APPLICATION_JSON))
                .andReturn();

        assertThat(first.getResponse().getStatus()).isEqualTo(200);
        Cookie rotated = getCookieByName(first, REFRESH_COOKIE);
        assertThat(rotated).isNotNull();

        MvcResult second = mockMvc.perform(
                post("/refresh-token")
                        .cookie(rotated)
                        .contentType(MediaType.APPLICATION_JSON))
                .andReturn();

        assertThat(second.getResponse().getStatus()).isEqualTo(200);
        JsonNode payload = parseJson(second);
        assertThat(payload.path("accessToken").asText(null)).isNotBlank();
    }

    // ---------- helpers ----------

    /**
     * Registers, verifies (302 redirect), then logs in.
     * Access token is taken from the 'jwt' cookie (or JSON if present).
     * Refresh token is taken from the 'refresh_token' cookie.
     */
    private LoginResponse registerVerifyAndLogin() throws Exception {
        // 1) register
        EmailRegistrationRequest req = new EmailRegistrationRequest();
        req.setName(name);
        req.setEmail(email);
        req.setPassword(password);

        MvcResult registerRes = mockMvc.perform(
                post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andReturn();

        assertThat(registerRes.getResponse().getStatus()).isEqualTo(200);
        log.debug("Register response: {}", registerRes.getResponse().getContentAsString(StandardCharsets.UTF_8));

        // 2) verify email (controller redirects to FE; expect 302). Tokens are stored hashed,
        // so capture the RAW token from the verification email the user would receive.
        ArgumentCaptor<String> tokenCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendVerificationEmail(eq(email), anyString(), tokenCaptor.capture());
        String verificationToken = tokenCaptor.getValue();

        MvcResult verifyRes = mockMvc.perform(get("/auth/verify-email").param("token", verificationToken))
                .andReturn();
        assertThat(verifyRes.getResponse().getStatus()).isBetween(300, 399);

        // 3) login (may be 200 JSON or 3xx redirect; cookies carry tokens)
        String loginJson = """
                {"email":"%s","password":"%s"}
                """.formatted(email, password);

        MvcResult loginRes = mockMvc.perform(
                post("/auth/email-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson))
                .andReturn();

        int status = loginRes.getResponse().getStatus();
        assertThat(status).isBetween(200, 399);

        // access token primarily comes via cookie 'jwt'
        String accessToken = null;
        Cookie accessCookie = getCookieByName(loginRes, ACCESS_COOKIE);
        if (accessCookie != null && accessCookie.getValue() != null && !accessCookie.getValue().isBlank()) {
            accessToken = accessCookie.getValue();
        } else if (status == 200) {
            // optional: some implementations might also return it in JSON
            JsonNode body = parseJson(loginRes);
            String bodyToken = body.path("accessToken").asText(null);
            if (bodyToken != null && !bodyToken.isBlank()) {
                accessToken = bodyToken;
            }
        }

        Cookie refreshCookie = getCookieByName(loginRes, REFRESH_COOKIE);
        assertThat(refreshCookie)
                .withFailMessage("Expected refresh cookie '%s' on login response", REFRESH_COOKIE)
                .isNotNull();

        assertThat(accessToken)
                .withFailMessage("Expected access token in '%s' cookie or JSON body", ACCESS_COOKIE)
                .isNotBlank();

        return new LoginResponse(accessToken, refreshCookie);
    }

    private JsonNode parseJson(MvcResult res) throws Exception {
        String body = res.getResponse().getContentAsString(StandardCharsets.UTF_8);
        return (body == null || body.isBlank())
                ? objectMapper.createObjectNode()
                : objectMapper.readTree(body);
    }

    private Cookie getCookieByName(MvcResult res, String name) {
        Cookie[] cookies = res.getResponse().getCookies();
        if (cookies == null)
            return null;
        for (Cookie c : cookies) {
            if (name.equals(c.getName()))
                return c;
        }
        return null;
    }

    private record LoginResponse(String accessToken, Cookie refreshCookie) {
    }
}
