package com.template.OAuth.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.template.OAuth.BaseIntegrationTest;
import com.template.OAuth.OAuthApplication;
import com.template.OAuth.dto.EmailRegistrationRequest;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Refresh-token reuse detection: replaying a token that has already been rotated away must be
 * treated as theft — the request is rejected (401) and the whole token family is revoked, so
 * even the most recently rotated token stops working.
 */
@SpringBootTest(classes = OAuthApplication.class)
@AutoConfigureMockMvc
@Transactional
@ActiveProfiles("test")
class RefreshTokenReuseIT extends BaseIntegrationTest {

    private static final String REFRESH_COOKIE = "refresh_token";
    private static final String PASSWORD = "OrigPassw0rd!";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private EmailService emailService;

    private String email;

    @BeforeEach
    void setUp() {
        doNothing().when(emailService).sendVerificationEmail(any(), any(), any());
        doNothing().when(emailService).sendWelcomeEmail(any(), any());
        email = "reuse_it+" + UUID.randomUUID() + "@example.com";
    }

    @Test
    void replaying_a_rotated_refresh_token_revokes_the_whole_family() throws Exception {
        Cookie original = registerVerifyAndLogin();

        // First refresh rotates the token: 'original' is now consumed.
        MvcResult firstRefresh = mockMvc.perform(post("/refresh-token")
                .cookie(original).contentType(MediaType.APPLICATION_JSON)).andReturn();
        assertThat(firstRefresh.getResponse().getStatus()).isEqualTo(200);
        Cookie rotated = cookie(firstRefresh, REFRESH_COOKIE);
        assertThat(rotated).isNotNull();

        // Replaying the now-consumed original token is reuse -> 401.
        MvcResult replay = mockMvc.perform(post("/refresh-token")
                .cookie(original).contentType(MediaType.APPLICATION_JSON)).andReturn();
        assertThat(replay.getResponse().getStatus()).isEqualTo(401);

        // Family was revoked: even the legitimately-rotated token no longer works.
        MvcResult afterRevocation = mockMvc.perform(post("/refresh-token")
                .cookie(rotated).contentType(MediaType.APPLICATION_JSON)).andReturn();
        assertThat(afterRevocation.getResponse().getStatus()).isEqualTo(401);
    }

    // ---------- helpers ----------

    private Cookie registerVerifyAndLogin() throws Exception {
        EmailRegistrationRequest req = new EmailRegistrationRequest();
        req.setName("Reuse IT");
        req.setEmail(email);
        req.setPassword(PASSWORD);

        mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andReturn();

        ArgumentCaptor<String> tokenCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendVerificationEmail(eq(email), anyString(), tokenCaptor.capture());
        mockMvc.perform(get("/auth/verify-email").param("token", tokenCaptor.getValue())).andReturn();

        String loginJson = "{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD);
        MvcResult loginRes = mockMvc.perform(post("/auth/email-login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginJson)).andReturn();

        Cookie refreshCookie = cookie(loginRes, REFRESH_COOKIE);
        assertThat(refreshCookie).isNotNull();
        return refreshCookie;
    }

    private Cookie cookie(MvcResult res, String name) {
        Cookie[] cookies = res.getResponse().getCookies();
        if (cookies == null) return null;
        for (Cookie c : cookies) {
            if (name.equals(c.getName())) return c;
        }
        return null;
    }
}
