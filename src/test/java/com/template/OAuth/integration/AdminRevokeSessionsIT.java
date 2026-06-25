package com.template.OAuth.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.template.OAuth.BaseIntegrationTest;
import com.template.OAuth.dto.EmailLoginRequest;
import com.template.OAuth.dto.EmailRegistrationRequest;
import com.template.OAuth.enums.Role;
import com.template.OAuth.service.EmailService;
import com.template.OAuth.service.UserService;
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
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@ActiveProfiles("test")
class AdminRevokeSessionsIT extends BaseIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserService userService;

    @MockitoBean private EmailService emailService;

    private final String password = "RevokeSessP0rd!";

    @BeforeEach
    void setUp() {
        // unique emails generated per call
    }

    /** Register + verify + login a user; returns the session jwt cookie. */
    private MockCookie registerVerifyLogin(String email) throws Exception {
        var reg = new EmailRegistrationRequest();
        reg.setName("User");
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
        return (MockCookie) result.getResponse().getCookie("jwt");
    }

    @Test
    void adminRevokeImmediatelyRejectsTargetAccessToken() throws Exception {
        String targetEmail = "revoke_target+" + UUID.randomUUID() + "@example.com";
        String adminEmail = "revoke_admin+" + UUID.randomUUID() + "@example.com";

        MockCookie targetJwt = registerVerifyLogin(targetEmail);
        MockCookie adminJwt = registerVerifyLogin(adminEmail);
        userService.assignRole(adminEmail, Role.ADMIN);
        // Re-login admin so the token carries the ADMIN role.
        var adminLogin = new EmailLoginRequest();
        adminLogin.setEmail(adminEmail);
        adminLogin.setPassword(password);
        MvcResult adminRes = mockMvc.perform(post("/auth/email-login").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(adminLogin))).andExpect(status().isOk()).andReturn();
        adminJwt = (MockCookie) adminRes.getResponse().getCookie("jwt");

        // Target can access before revocation.
        mockMvc.perform(get("/api/user/profile").cookie(targetJwt)).andExpect(status().isOk());

        // Admin revokes the target's access.
        mockMvc.perform(post("/api/admin/revoke-sessions").param("email", targetEmail).cookie(adminJwt).with(csrf()))
                .andExpect(status().isOk());

        // Target's existing access token is now rejected.
        mockMvc.perform(get("/api/user/profile").cookie(targetJwt)).andExpect(status().is4xxClientError());
    }

    @Test
    void nonAdminCannotRevoke() throws Exception {
        String a = "revoke_nonadmin_a+" + UUID.randomUUID() + "@example.com";
        String b = "revoke_nonadmin_b+" + UUID.randomUUID() + "@example.com";
        MockCookie userJwt = registerVerifyLogin(a);
        registerVerifyLogin(b);

        mockMvc.perform(post("/api/admin/revoke-sessions").param("email", b).cookie(userJwt).with(csrf()))
                .andExpect(status().isForbidden());
    }
}
