package com.template.OAuth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.template.OAuth.dto.EmailRegistrationRequest;
import com.template.OAuth.dto.PasswordResetRequest;
import com.template.OAuth.entities.User;
import com.template.OAuth.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
// import org.springframework.boot.test.mock.mockito.MockBean; // deprecated in Boot 3.4+
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean; // <-- new in 3.4+
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AuthService authService; // replaced @MockBean

    private EmailRegistrationRequest registrationRequest;
    private User testUser;

    @BeforeEach
    void setUp() {
        // Set up test user
        testUser = new User();
        testUser.setId(1L);
        testUser.setEmail("test@example.com");
        testUser.setName("Test User");

        // Set up registration request
        registrationRequest = new EmailRegistrationRequest();
        registrationRequest.setEmail("test@example.com");
        registrationRequest.setName("Test User");
        registrationRequest.setPassword("Password123!");
    }

    @Test
    void testRegisterUser_Success() throws Exception {
        // Arrange
        when(authService.registerUser(any(EmailRegistrationRequest.class))).thenReturn(testUser);

        // Act & Assert
        mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(registrationRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("test@example.com"));
    }

    @Test
    void testVerifyEmail_Success() throws Exception {
        // Arrange
        when(authService.verifyEmail(anyString())).thenReturn(true);

        // Act & Assert
        mockMvc.perform(get("/auth/verify-email")
                .param("token", "valid-token"))
                .andExpect(status().isFound())
                .andExpect(header().exists("Location"));
    }

    @Test
    void testVerifyEmail_Failure() throws Exception {
        // Arrange
        when(authService.verifyEmail(anyString())).thenReturn(false);

        // Act & Assert
        mockMvc.perform(get("/auth/verify-email")
                .param("token", "invalid-token"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void testForgotPassword() throws Exception {
        // Arrange
        PasswordResetRequest resetRequest = new PasswordResetRequest();
        resetRequest.setEmail("test@example.com");

        doNothing().when(authService).initiatePasswordReset(anyString());

        // Act & Assert
        mockMvc.perform(post("/auth/forgot-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(resetRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").exists());

        verify(authService, times(1)).initiatePasswordReset(anyString());
    }
}
