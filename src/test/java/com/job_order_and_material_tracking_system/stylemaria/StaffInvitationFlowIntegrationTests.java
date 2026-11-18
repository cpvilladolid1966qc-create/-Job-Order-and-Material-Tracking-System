package com.job_order_and_material_tracking_system.stylemaria;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.Mockito.verify;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;

@SpringBootTest(properties = {
        "app.admin.email=staff-flow-admin@example.com",
        "app.admin.password=Staff-flow-test-only-passphrase",
        "app.public-url=https://example.test"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class StaffInvitationFlowIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private JavaMailSender mailSender;

    @BeforeEach
    void clearStaffFixture() {
        cleanStaffFixture();
    }

    @AfterEach
    void removeStaffFixture() {
        cleanStaffFixture();
    }

    private void cleanStaffFixture() {
        jdbcTemplate.update("DELETE FROM audit_events");
        jdbcTemplate.update("DELETE FROM staff_invites");
        jdbcTemplate.update("DELETE FROM staff_accounts");
    }

    @Test
    void adminInviteEmailAcceptanceAndStaffSessionAreDurableAndRoleBound() throws Exception {
        mockMvc.perform(get("/api/v1/admin/staff"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("unauthenticated"));

        MockHttpSession adminSession = login(
                "staff-flow-admin@example.com", "Staff-flow-test-only-passphrase");
        MvcResult inviteResponse = mockMvc.perform(post("/api/v1/admin/staff/invitations")
                        .session(adminSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Invited Teammate\",\"email\":\"New.Staff@example.com\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.email").value("new.staff@example.com"))
                .andReturn();
        assertFalse(inviteResponse.getResponse().getContentAsString().contains("token"));

        ArgumentCaptor<SimpleMailMessage> mailCaptor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(mailCaptor.capture());
        Matcher tokenMatcher = Pattern.compile("token=([A-Za-z0-9_-]{20,100})")
                .matcher(mailCaptor.getValue().getText());
        org.junit.jupiter.api.Assertions.assertTrue(tokenMatcher.find(), "invite mail must carry acceptance token");
        String token = tokenMatcher.group(1);
        String storedHash = jdbcTemplate.queryForObject(
                "SELECT token_hash FROM staff_invites WHERE email = ?",
                String.class, "new.staff@example.com");
        assertEquals(PasswordSecurity.hashToken(token), storedHash);
        assertFalse(storedHash.equals(token));

        mockMvc.perform(post("/api/v1/staff/invitations/accept")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + token
                                + "\",\"newPassword\":\"Staff-flow-new-password\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("new.staff@example.com"))
                .andExpect(jsonPath("$.role").value("STAFF"));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM staff_accounts WHERE email = 'new.staff@example.com' AND active = TRUE",
                Integer.class));

        MockHttpSession staffSession = login("new.staff@example.com", "Staff-flow-new-password");
        mockMvc.perform(get("/api/v1/inventory/materials").session(staffSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
        mockMvc.perform(get("/api/v1/admin/staff").session(staffSession))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("forbidden"));
        mockMvc.perform(get("/api/v1/admin/staff").session(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].email").value("new.staff@example.com"));
        assertNotNull(jdbcTemplate.queryForObject(
                "SELECT accepted_at FROM staff_invites WHERE email = ?",
                java.sql.Timestamp.class, "new.staff@example.com"));
    }

    private MockHttpSession login(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/login")
                        .param("username", email)
                        .param("password", password))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
        assertNotNull(session);
        return session;
    }
}
