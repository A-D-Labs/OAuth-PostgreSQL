package com.template.OAuth.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.template.OAuth.dto.EmailLoginRequest;
import com.template.OAuth.dto.EmailRegistrationRequest;
import com.template.OAuth.entities.User;
import com.template.OAuth.enums.Role;
import com.template.OAuth.repositories.UserRepository;
import com.template.OAuth.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockCookie;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.template.OAuth.BaseIntegrationTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@ActiveProfiles("test")
class RoleProtectionAdminIT extends BaseIntegrationTest {

        private static final Logger log = LoggerFactory.getLogger(RoleProtectionAdminIT.class);

        @Autowired
        private MockMvc mockMvc;
        @Autowired
        private ObjectMapper objectMapper;
        @Autowired
        private UserRepository userRepository;
        @Autowired
        private UserService userService;

        private String email;
        private final String password = "OrigPassw0rd!";

        @BeforeEach
        void setUp() {
                email = "role_it+" + UUID.randomUUID() + "@example.com";
                log.info("Using test email: {}", email);
        }

        @Test
        void user_gets_403_on_admin_then_200_after_promotion() throws Exception {
                // 1) Register
                var regReq = new EmailRegistrationRequest();
                regReq.setName("Role IT");
                regReq.setEmail(email);
                regReq.setPassword(password);

                mockMvc.perform(
                                post("/auth/register")
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content(objectMapper.writeValueAsString(regReq)))
                                .andExpect(status().isOk());

                // 2) Verify email using token from DB
                User created = userRepository.findByEmail(email).orElseThrow();
                String token = created.getVerificationToken();

                mockMvc.perform(get("/auth/verify-email").param("token", token))
                                .andExpect(status().is3xxRedirection());

                // 3) Login to get USER JWT
                var loginReq = new EmailLoginRequest();
                loginReq.setEmail(email);
                loginReq.setPassword(password);

                var loginMvcResult = mockMvc.perform(
                                post("/auth/email-login")
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content(objectMapper.writeValueAsString(loginReq)))
                                .andExpect(status().isOk()).andReturn();

                MockCookie userJwt = (MockCookie) loginMvcResult.getResponse().getCookie("jwt");
                assertThat(userJwt).isNotNull();

                // 4) Hit an admin-protected endpoint => expect 403 (authenticated but not
                // authorized)
                mockMvc.perform(
                                get("/api/admin/users")
                                                .cookie(userJwt))
                                .andExpect(status().isForbidden());

                // 5) Promote to ADMIN via service and re-login to mint a token with the new
                // role
                userService.assignRole(email, Role.ADMIN);

                var loginAgain = mockMvc.perform(
                                post("/auth/email-login")
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content(objectMapper.writeValueAsString(loginReq)))
                                .andExpect(status().isOk()).andReturn();

                MockCookie adminJwt = (MockCookie) loginAgain.getResponse().getCookie("jwt");
                assertThat(adminJwt).isNotNull();

                // 6) Now the same call should be allowed
                mockMvc.perform(
                                get("/api/admin/users")
                                                .cookie(adminJwt))
                                .andExpect(status().isOk());
        }
}
