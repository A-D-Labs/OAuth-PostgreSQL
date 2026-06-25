package com.template.OAuth.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.template.OAuth.BaseIntegrationTest;
import com.template.OAuth.dto.EmailLoginRequest;
import com.template.OAuth.dto.EmailRegistrationRequest;
import com.template.OAuth.entities.User;
import com.template.OAuth.repositories.UserRepository;
import com.template.OAuth.service.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockCookie;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@ActiveProfiles("test")
class TokenRevocationIT extends BaseIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;

    @MockitoBean private EmailService emailService;

    private String email;
    private final String password = "Revoke0Passw0rd!";

    @BeforeEach
    void setUp() {
        email = "revoke_it+" + UUID.randomUUID() + "@example.com";
    }

    private MockCookie loginAndGetJwt() throws Exception {
        var reg = new EmailRegistrationRequest();
        reg.setName("Revoke IT");
        reg.setEmail(email);
        reg.setPassword(password);
        mockMvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(reg))).andExpect(status().isOk());

        ArgumentCaptor<String> tokenCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendVerificationEmail(eq(email), anyString(), tokenCaptor.capture());
        mockMvc.perform(get("/auth/verify-email").param("token", tokenCaptor.getValue()))
                .andExpect(status().is3xxRedirection());

        var login = new EmailLoginRequest();
        login.setEmail(email);
        login.setPassword(password);
        var result = mockMvc.perform(post("/auth/email-login").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(login))).andExpect(status().isOk()).andReturn();
        MockCookie jwt = (MockCookie) result.getResponse().getCookie("jwt");
        assertThat(jwt).isNotNull();
        return jwt;
    }

    @Test
    void accessTokenRejectedAfterUserEpochAdvanced() throws Exception {
        MockCookie jwt = loginAndGetJwt();

        // Before revocation: the token grants access.
        mockMvc.perform(get("/api/user/profile").cookie(jwt)).andExpect(status().isOk());

        // Admin ban / forced-logout: advance the user's revocation epoch past the token's iat.
        User user = userRepository.findByEmail(email).orElseThrow();
        user.setTokensInvalidBefore(Instant.now().plusSeconds(60));
        userRepository.saveAndFlush(user);

        // After revocation: the same token is no longer accepted.
        mockMvc.perform(get("/api/user/profile").cookie(jwt)).andExpect(status().is4xxClientError());
    }

    @Test
    void tokenIssuedAfterEpochIsAccepted() throws Exception {
        // A pre-existing epoch in the past must not block freshly-minted tokens.
        MockCookie jwt = loginAndGetJwt();
        User user = userRepository.findByEmail(email).orElseThrow();
        user.setTokensInvalidBefore(Instant.now().minusSeconds(3600));
        userRepository.saveAndFlush(user);

        mockMvc.perform(get("/api/user/profile").cookie(jwt)).andExpect(status().isOk());
    }
}
