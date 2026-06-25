package com.template.OAuth.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.template.OAuth.BaseIntegrationTest;
import com.template.OAuth.dto.EmailLoginRequest;
import com.template.OAuth.dto.EmailRegistrationRequest;
import com.template.OAuth.service.EmailService;
import dev.samstevens.totp.code.DefaultCodeGenerator;
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
class MfaLoginChallengeIT extends BaseIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockitoBean private EmailService emailService;

    private String email;
    private final String password = "ChallengePassw0rd!";
    private String totpSecret;
    private JsonNode recoveryCodes;

    @BeforeEach
    void setUp() {
        email = "mfa_login+" + UUID.randomUUID() + "@example.com";
    }

    private String validCodeFor(String secret) throws Exception {
        long window = Math.floorDiv(System.currentTimeMillis() / 1000L, 30L);
        return new DefaultCodeGenerator().generate(secret, window);
    }

    /** Register, verify, login, then enrol + activate MFA. Returns a session jwt cookie. */
    private MockCookie registerAndEnableMfa() throws Exception {
        var reg = new EmailRegistrationRequest();
        reg.setName("MFA Login");
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
        MvcResult first = mockMvc.perform(post("/auth/email-login").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(login))).andExpect(status().isOk()).andReturn();
        MockCookie jwt = (MockCookie) first.getResponse().getCookie("jwt");

        MvcResult enroll = mockMvc.perform(post("/auth/mfa/enroll").cookie(jwt))
                .andExpect(status().isOk()).andReturn();
        JsonNode body = objectMapper.readTree(enroll.getResponse().getContentAsString());
        totpSecret = body.get("secret").asText();
        recoveryCodes = body.get("recoveryCodes");

        mockMvc.perform(post("/auth/mfa/activate").cookie(jwt).contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"" + validCodeFor(totpSecret) + "\"}")).andExpect(status().isOk());
        return jwt;
    }

    private MockCookie loginExpectingChallenge() throws Exception {
        var login = new EmailLoginRequest();
        login.setEmail(email);
        login.setPassword(password);
        MvcResult result = mockMvc.perform(post("/auth/email-login").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(login))).andExpect(status().isOk()).andReturn();

        // MFA on: a challenge cookie, but NO session jwt yet.
        assertThat(result.getResponse().getCookie("jwt")).isNull();
        MockCookie challenge = (MockCookie) result.getResponse().getCookie("mfa_challenge");
        assertThat(challenge).isNotNull();
        assertThat(result.getResponse().getContentAsString()).contains("MFA verification required");
        return challenge;
    }

    @Test
    void loginWithMfaRequiresTotpThenIssuesSession() throws Exception {
        registerAndEnableMfa();
        MockCookie challenge = loginExpectingChallenge();

        // Verify with a valid TOTP code -> full session issued.
        MvcResult verified = mockMvc.perform(post("/auth/mfa/verify").cookie(challenge)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + validCodeFor(totpSecret) + "\"}"))
                .andExpect(status().isOk()).andReturn();
        assertThat(verified.getResponse().getCookie("jwt")).isNotNull();
    }

    @Test
    void recoveryCodeSatisfiesTheChallenge() throws Exception {
        registerAndEnableMfa();
        MockCookie challenge = loginExpectingChallenge();

        String recoveryCode = recoveryCodes.get(0).asText();
        MvcResult verified = mockMvc.perform(post("/auth/mfa/verify").cookie(challenge)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + recoveryCode + "\"}"))
                .andExpect(status().isOk()).andReturn();
        assertThat(verified.getResponse().getCookie("jwt")).isNotNull();
    }

    @Test
    void wrongCodeDoesNotIssueSession() throws Exception {
        registerAndEnableMfa();
        MockCookie challenge = loginExpectingChallenge();

        mockMvc.perform(post("/auth/mfa/verify").cookie(challenge)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"000000\"}"))
                .andExpect(status().isUnauthorized());
    }
}
