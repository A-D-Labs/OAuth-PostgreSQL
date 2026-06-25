package com.template.OAuth.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.template.OAuth.BaseIntegrationTest;
import com.template.OAuth.dto.EmailLoginRequest;
import com.template.OAuth.dto.EmailRegistrationRequest;
import com.template.OAuth.entities.User;
import com.template.OAuth.repositories.MfaRecoveryCodeRepository;
import com.template.OAuth.repositories.UserRepository;
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
class MfaEnrollmentIT extends BaseIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private MfaRecoveryCodeRepository recoveryCodeRepository;

    @MockitoBean private EmailService emailService;

    private String email;
    private final String password = "EnrollPassw0rd!";

    @BeforeEach
    void setUp() {
        email = "mfa_enroll+" + UUID.randomUUID() + "@example.com";
    }

    private MockCookie loginAndGetJwt() throws Exception {
        var reg = new EmailRegistrationRequest();
        reg.setName("MFA Enroll");
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
        return (MockCookie) result.getResponse().getCookie("jwt");
    }

    private String validCodeFor(String secret) throws Exception {
        long timeWindow = Math.floorDiv(System.currentTimeMillis() / 1000L, 30L);
        return new DefaultCodeGenerator().generate(secret, timeWindow);
    }

    @Test
    void enrollThenActivateTurnsMfaOnAndIssuesRecoveryCodes() throws Exception {
        MockCookie jwt = loginAndGetJwt();

        // Enrol
        var enrollResult = mockMvc.perform(post("/auth/mfa/enroll").cookie(jwt))
                .andExpect(status().isOk()).andReturn();
        JsonNode body = objectMapper.readTree(enrollResult.getResponse().getContentAsString());
        String secret = body.get("secret").asText();
        assertThat(secret).isNotBlank();
        assertThat(body.get("otpauthUri").asText()).startsWith("otpauth://totp/");
        assertThat(body.get("recoveryCodes")).hasSize(10);

        // Not yet active until activation
        assertThat(userRepository.findByEmail(email).orElseThrow().isMfaEnabled()).isFalse();
        assertThat(recoveryCodeRepository.findByUser(userRepository.findByEmail(email).orElseThrow())).hasSize(10);

        // Activate with a valid TOTP code
        String code = validCodeFor(secret);
        mockMvc.perform(post("/auth/mfa/activate").cookie(jwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + code + "\"}"))
                .andExpect(status().isOk());

        User after = userRepository.findByEmail(email).orElseThrow();
        assertThat(after.isMfaEnabled()).isTrue();
        assertThat(after.getTotpSecret()).isEqualTo(secret);
    }

    @Test
    void activateWithWrongCodeIsRejected() throws Exception {
        MockCookie jwt = loginAndGetJwt();
        mockMvc.perform(post("/auth/mfa/enroll").cookie(jwt)).andExpect(status().isOk());

        mockMvc.perform(post("/auth/mfa/activate").cookie(jwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"000000\"}"))
                .andExpect(status().isBadRequest());

        assertThat(userRepository.findByEmail(email).orElseThrow().isMfaEnabled()).isFalse();
    }
}
