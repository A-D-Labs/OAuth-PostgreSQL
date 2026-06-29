package com.template.OAuth.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.template.OAuth.BaseIntegrationTest;
import com.template.OAuth.dto.EmailLoginRequest;
import com.template.OAuth.dto.EmailRegistrationRequest;
import com.template.OAuth.service.EmailService;
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

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@ActiveProfiles("test")
class SessionEndpointsIT extends BaseIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockitoBean private EmailService emailService;

    private final String password = "SessP0rd!Multi";

    @Test
    void twoLoginsAreListedWithExactlyOneCurrent() throws Exception {
        String email = uniqueEmail();
        registerAndVerify(email);
        Session first = login(email);
        login(email); // second device

        JsonNode sessions = listSessions(first.jwt);
        assertThat(sessions.isArray()).isTrue();
        assertThat(sessions.size()).isEqualTo(2);

        long current = 0;
        for (JsonNode s : sessions) {
            assertThat(s.path("sessionId").asText()).isNotBlank();
            // device metadata is present (MockMvc supplies a default remote addr)
            assertThat(s.has("createdAt")).isTrue();
            if (s.path("current").asBoolean()) {
                current++;
            }
        }
        assertThat(current).isEqualTo(1);
    }

    @Test
    void revokingOneDeviceLeavesTheOtherFunctional() throws Exception {
        String email = uniqueEmail();
        registerAndVerify(email);
        Session keep = login(email);   // the device we keep / list from
        Session drop = login(email);   // the device we revoke

        // sanity: the dropped device's refresh token works before revocation
        mockMvc.perform(post("/refresh-token").cookie(drop.refresh))
                .andExpect(status().isOk());

        // find the non-current session id (the other device) from the list
        String otherSessionId = findNonCurrentSessionId(listSessions(keep.jwt));

        mockMvc.perform(delete("/api/user/sessions/{id}", otherSessionId).cookie(keep.jwt).with(csrf()))
                .andExpect(status().isNoContent());

        // revoked device's refresh token is now rejected...
        mockMvc.perform(post("/refresh-token").cookie(drop.refresh))
                .andExpect(status().is4xxClientError());

        // ...while the kept device keeps working, and only one session remains
        mockMvc.perform(post("/refresh-token").cookie(keep.refresh))
                .andExpect(status().isOk());
        assertThat(listSessions(keep.jwt).size()).isEqualTo(1);
    }

    @Test
    void revokingTheCurrentSessionInvalidatesItsRefreshToken() throws Exception {
        String email = uniqueEmail();
        registerAndVerify(email);
        Session session = login(email);

        String currentId = findCurrentSessionId(listSessions(session.jwt));

        mockMvc.perform(delete("/api/user/sessions/{id}", currentId).cookie(session.jwt).with(csrf()))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/refresh-token").cookie(session.refresh))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void cannotRevokeAnotherUsersSession() throws Exception {
        String owner = uniqueEmail();
        registerAndVerify(owner);
        Session ownerSession = login(owner);
        String ownerSessionId = findCurrentSessionId(listSessions(ownerSession.jwt));

        String attacker = uniqueEmail();
        registerAndVerify(attacker);
        Session attackerSession = login(attacker);

        // attacker may not revoke a session they don't own — and existence is not leaked (404)
        mockMvc.perform(delete("/api/user/sessions/{id}", ownerSessionId)
                        .cookie(attackerSession.jwt).with(csrf()))
                .andExpect(status().isNotFound());

        // owner's session is untouched
        assertThat(listSessions(ownerSession.jwt).size()).isEqualTo(1);
    }

    // ---------- helpers ----------

    private String uniqueEmail() {
        return "session_it+" + UUID.randomUUID() + "@example.com";
    }

    private void registerAndVerify(String email) throws Exception {
        var reg = new EmailRegistrationRequest();
        reg.setName("Session User");
        reg.setEmail(email);
        reg.setPassword(password);
        mockMvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(reg))).andExpect(status().isOk());

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendVerificationEmail(eq(email), anyString(), captor.capture());
        mockMvc.perform(get("/auth/verify-email").param("token", captor.getValue()))
                .andExpect(status().is3xxRedirection());
    }

    private Session login(String email) throws Exception {
        var login = new EmailLoginRequest();
        login.setEmail(email);
        login.setPassword(password);
        MvcResult res = mockMvc.perform(post("/auth/email-login").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(login))).andExpect(status().isOk()).andReturn();
        MockCookie jwt = (MockCookie) res.getResponse().getCookie("jwt");
        MockCookie refresh = (MockCookie) res.getResponse().getCookie("refresh_token");
        assertThat(jwt).isNotNull();
        assertThat(refresh).isNotNull();
        return new Session(jwt, refresh);
    }

    private JsonNode listSessions(MockCookie jwt) throws Exception {
        MvcResult res = mockMvc.perform(get("/api/user/sessions").cookie(jwt))
                .andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private String findNonCurrentSessionId(JsonNode sessions) {
        for (JsonNode s : sessions) {
            if (!s.path("current").asBoolean()) {
                return s.path("sessionId").asText();
            }
        }
        throw new AssertionError("no non-current session found");
    }

    private String findCurrentSessionId(JsonNode sessions) {
        for (JsonNode s : sessions) {
            if (s.path("current").asBoolean()) {
                return s.path("sessionId").asText();
            }
        }
        throw new AssertionError("no current session found");
    }

    private record Session(MockCookie jwt, MockCookie refresh) {}
}
