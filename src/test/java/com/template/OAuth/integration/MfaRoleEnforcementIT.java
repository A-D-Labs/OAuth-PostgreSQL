package com.template.OAuth.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.template.OAuth.BaseIntegrationTest;
import com.template.OAuth.dto.EmailLoginRequest;
import com.template.OAuth.dto.EmailRegistrationRequest;
import com.template.OAuth.enums.Role;
import com.template.OAuth.repositories.UserRepository;
import com.template.OAuth.service.EmailService;
import com.template.OAuth.service.UserService;
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
import org.springframework.test.context.TestPropertySource;
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
@TestPropertySource(properties = "app.security.mfa.required-roles=ADMIN")
class MfaRoleEnforcementIT extends BaseIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private UserService userService;

    @MockitoBean private EmailService emailService;

    private String email;
    private final String password = "AdminMfaPassw0rd!";

    @BeforeEach
    void setUp() {
        email = "mfa_role+" + UUID.randomUUID() + "@example.com";
    }

    private void registerVerifyAndPromoteToAdmin() throws Exception {
        var reg = new EmailRegistrationRequest();
        reg.setName("Admin MFA");
        reg.setEmail(email);
        reg.setPassword(password);
        mockMvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(reg))).andExpect(status().isOk());

        ArgumentCaptor<String> tokenCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendVerificationEmail(eq(email), anyString(), tokenCaptor.capture());
        mockMvc.perform(get("/auth/verify-email").param("token", tokenCaptor.getValue()))
                .andExpect(status().is3xxRedirection());

        userService.assignRole(email, Role.ADMIN);
    }

    private MvcResult login() throws Exception {
        var login = new EmailLoginRequest();
        login.setEmail(email);
        login.setPassword(password);
        return mockMvc.perform(post("/auth/email-login").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(login))).andExpect(status().isOk()).andReturn();
    }

    private String validCodeFor(String secret) throws Exception {
        long window = Math.floorDiv(System.currentTimeMillis() / 1000L, 30L);
        return new DefaultCodeGenerator().generate(secret, window);
    }

    @Test
    void adminWithoutMfaIsForcedToEnrolThenChallengedOnNextLogin() throws Exception {
        registerVerifyAndPromoteToAdmin();

        // Login: role-mandatory MFA but not enrolled -> enrol-only token, no full session.
        MvcResult enrolLogin = login();
        assertThat(enrolLogin.getResponse().getContentAsString()).contains("MFA enrolment required");
        MockCookie enrolToken = (MockCookie) enrolLogin.getResponse().getCookie("jwt");
        assertThat(enrolToken).isNotNull();
        assertThat(enrolLogin.getResponse().getCookie("mfa_challenge")).isNull();

        // The enrol-only token cannot reach protected endpoints.
        mockMvc.perform(get("/api/admin/users").cookie(enrolToken)).andExpect(status().isUnauthorized());

        // But it CAN reach enrol/activate.
        MvcResult enroll = mockMvc.perform(post("/auth/mfa/enroll").cookie(enrolToken))
                .andExpect(status().isOk()).andReturn();
        JsonNode body = objectMapper.readTree(enroll.getResponse().getContentAsString());
        String secret = body.get("secret").asText();
        mockMvc.perform(post("/auth/mfa/activate").cookie(enrolToken).contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"" + validCodeFor(secret) + "\"}")).andExpect(status().isOk());

        assertThat(userRepository.findByEmail(email).orElseThrow().isMfaEnabled()).isTrue();

        // Next login now goes through the normal MFA challenge flow.
        MvcResult second = login();
        assertThat(second.getResponse().getContentAsString()).contains("MFA verification required");
        assertThat(second.getResponse().getCookie("mfa_challenge")).isNotNull();
    }
}
