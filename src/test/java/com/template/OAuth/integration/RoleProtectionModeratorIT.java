package com.template.OAuth.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.template.OAuth.entities.User;
import com.template.OAuth.enums.Role;
import com.template.OAuth.repositories.UserRepository;
import com.template.OAuth.service.EmailService;
import com.template.OAuth.service.UserService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.template.OAuth.BaseIntegrationTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@ActiveProfiles("test")
class RoleProtectionModeratorIT extends BaseIntegrationTest {

    private static final String PASSWORD = "OrigPassw0rd!";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private UserService userService;

    @MockitoBean
    private EmailService emailService;

    @Test
    void moderator_endpoint_requires_moderator_or_admin() throws Exception {
        String email = uniqueEmail("moderator");

        registerAndVerify(email, "Moderator Check");
        Cookie userJwt = loginAndGetJwt(email, PASSWORD);

        mockMvc.perform(get("/api/moderator/users").cookie(userJwt))
                .andExpect(status().isForbidden());

        userService.assignRole(email, Role.MODERATOR);

        Cookie modJwt = loginAndGetJwt(email, PASSWORD);

        mockMvc.perform(get("/api/moderator/users").cookie(modJwt))
                .andExpect(status().isOk());
    }

    // helpers
    private String uniqueEmail(String prefix) {
        return prefix + "+" + UUID.randomUUID() + "@example.com";
    }

    private void registerAndVerify(String email, String name) throws Exception {
        mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("name", name, "email", email, "password", PASSWORD))))
                .andExpect(status().isOk());

        assertThat(userRepository.findByEmail(email)).isPresent();
        // DB stores only the token hash; capture the RAW token from the verification email.
        ArgumentCaptor<String> tokenCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendVerificationEmail(eq(email), anyString(), tokenCaptor.capture());
        String token = tokenCaptor.getValue();
        assertThat(token).isNotBlank();

        mockMvc.perform(get("/auth/verify-email").param("token", token))
                .andExpect(status().is3xxRedirection());
    }

    private Cookie loginAndGetJwt(String email, String password) throws Exception {
        MvcResult res = mockMvc.perform(post("/auth/email-login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("email", email, "password", password))))
                .andExpect(status().isOk())
                .andReturn();
        Cookie jwt = res.getResponse().getCookie("jwt");
        assertThat(jwt).isNotNull();
        assertThat(jwt.getValue()).isNotBlank();
        return jwt;
    }

    private String json(Object o) throws Exception {
        return objectMapper.writeValueAsString(o);
    }
}
