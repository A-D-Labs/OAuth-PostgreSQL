package com.template.OAuth.integration;

import com.template.OAuth.entities.User;
import com.template.OAuth.repositories.UserRepository;
import com.template.OAuth.service.EmailService;
import jakarta.servlet.http.Cookie;
import org.hamcrest.Matchers;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.template.OAuth.BaseIntegrationTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@ActiveProfiles("test")
class PWResetFlowIT extends BaseIntegrationTest {

        @Autowired
        private MockMvc mockMvc;
        @Autowired
        private UserRepository userRepository;

        // Boot 3.4+: prefer MockitoBean over deprecated @MockitoBean
        @MockitoBean
        private EmailService emailService;

        @Test
        void password_reset_flow_success() throws Exception {
                final String email = "reset_flow_it@example.com";
                final String originalPassword = "OrigPassw0rd!";
                final String newPassword = "N3wStrongPass!";

                // 0) Register user (starts disabled & has verification token)
                mockMvc.perform(post("/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                                {"name":"Reset Flow IT","email":"%s","password":"%s"}
                                                """.formatted(email, originalPassword)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.message").value("User created successfully"))
                                .andExpect(jsonPath("$.email").value(email));

                User justRegistered = userRepository.findByEmail(email).orElseThrow();
                assertThat(justRegistered.isEnabled()).isFalse();

                // Tokens are stored hashed; capture the RAW token from the verification email.
                ArgumentCaptor<String> verifyTokenCaptor = ArgumentCaptor.forClass(String.class);
                verify(emailService).sendVerificationEmail(eq(email), anyString(), verifyTokenCaptor.capture());
                String verificationToken = verifyTokenCaptor.getValue();
                assertThat(verificationToken).isNotBlank();

                // -- Verify via endpoint -> expect 302 redirect to FE login
                mockMvc.perform(get("/auth/verify-email").param("token", verificationToken))
                                .andExpect(status().is3xxRedirection())
                                .andExpect(header().string("Location",
                                                Matchers.startsWith("http://localhost:3000/login")));

                // Ensure user is now enabled
                User verified = userRepository.findByEmail(email).orElseThrow();
                assertThat(verified.isEnabled()).isTrue();
                String originalHash = verified.getPassword();

                // 1) Forgot password (generates reset token)
                mockMvc.perform(post("/auth/forgot-password")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                                {"email":"%s"}
                                                """.formatted(email)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.message")
                                                .value("Password reset instructions have been sent to your email"));

                User afterForgot = userRepository.findByEmail(email).orElseThrow();
                assertThat(afterForgot.getPasswordResetToken()).isNotBlank();
                assertThat(afterForgot.getPasswordResetTokenExpiry()).isNotNull();

                // Capture the RAW reset token from the reset email (DB stores only its hash).
                ArgumentCaptor<String> resetTokenCaptor = ArgumentCaptor.forClass(String.class);
                verify(emailService).sendPasswordResetEmail(eq(email), anyString(), resetTokenCaptor.capture());
                String resetToken = resetTokenCaptor.getValue();
                assertThat(resetToken).isNotBlank();

                // 2) Reset with token
                mockMvc.perform(post("/auth/reset-password")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                                {"password":"%s","confirmPassword":"%s","token":"%s"}
                                                """.formatted(newPassword, newPassword, resetToken)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.message").value("Password has been reset successfully"));

                User afterReset = userRepository.findByEmail(email).orElseThrow();
                assertThat(afterReset.getPasswordResetToken()).isNull();
                assertThat(afterReset.getPasswordResetTokenExpiry()).isNull();
                assertThat(afterReset.getPassword()).isNotEqualTo(originalHash);

                // 3) Login with new password -> expect 200 and secure cookies
                MvcResult loginRes = mockMvc.perform(post("/auth/email-login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                                {"email":"%s","password":"%s"}
                                                """.formatted(email, newPassword)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.message").value("Login successful"))
                                // JWT is HttpOnly cookie; body token can be null by design.
                                .andReturn();

                Cookie jwtCookie = getCookie(loginRes, "jwt");
                Cookie refreshCookie = getCookie(loginRes, "refresh_token");

                assertThat(jwtCookie).isNotNull();
                assertThat(jwtCookie.isHttpOnly()).isTrue();
                assertThat(jwtCookie.getPath()).isEqualTo("/");

                assertThat(refreshCookie).isNotNull();
                assertThat(refreshCookie.isHttpOnly()).isTrue();
                assertThat(refreshCookie.getPath()).isEqualTo("/refresh-token");
        }

        private Cookie getCookie(MvcResult result, String name) {
                for (Cookie c : result.getResponse().getCookies()) {
                        if (name.equals(c.getName()))
                                return c;
                }
                return null;
        }
}
