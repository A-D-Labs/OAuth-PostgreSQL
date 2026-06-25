package com.template.OAuth.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.template.OAuth.BaseIntegrationTest;
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
import org.springframework.mock.web.MockCookie;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
class RefreshAbsoluteLifetimeIT extends BaseIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private RefreshTokenRepository refreshTokenRepository;

    @MockitoBean private EmailService emailService;

    private String email;
    private final String password = "AbsoluteP0rd!";

    @BeforeEach
    void setUp() {
        email = "refresh_abs+" + UUID.randomUUID() + "@example.com";
    }

    private MockCookie registerVerifyLogin() throws Exception {
        var reg = new EmailRegistrationRequest();
        reg.setName("Abs Life");
        reg.setEmail(email);
        reg.setPassword(password);
        mockMvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(reg))).andExpect(status().isOk());

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendVerificationEmail(eq(email), anyString(), captor.capture());
        mockMvc.perform(get("/auth/verify-email").param("token", captor.getValue()))
                .andExpect(status().is3xxRedirection());

        var login = new EmailLoginRequest();
        login.setEmail(email);
        login.setPassword(password);
        MvcResult result = mockMvc.perform(post("/auth/email-login").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(login))).andExpect(status().isOk()).andReturn();
        return (MockCookie) result.getResponse().getCookie("refresh_token");
    }

    @Test
    void refreshWithinAbsoluteCapSucceeds() throws Exception {
        MockCookie refresh = registerVerifyLogin();
        mockMvc.perform(post("/refresh-token").cookie(refresh).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    void refreshPastAbsoluteCapIsRejectedEvenWithValidRotation() throws Exception {
        MockCookie refresh = registerVerifyLogin();

        // Age the family past the 30-day absolute cap (the token itself is otherwise valid/unexpired).
        User user = userRepository.findByEmail(email).orElseThrow();
        RefreshToken token = refreshTokenRepository.findByUser(user).get(0);
        token.setCreatedAt(Instant.now().minus(31, ChronoUnit.DAYS));
        refreshTokenRepository.saveAndFlush(token);

        MvcResult result = mockMvc.perform(post("/refresh-token")
                        .cookie((Cookie) refresh).contentType(MediaType.APPLICATION_JSON))
                .andReturn();
        assertThat(result.getResponse().getStatus()).isBetween(400, 499);
    }
}
