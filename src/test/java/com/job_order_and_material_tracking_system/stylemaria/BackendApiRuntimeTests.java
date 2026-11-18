package com.job_order_and_material_tracking_system.stylemaria;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.mockito.ArgumentCaptor;
import static org.mockito.Mockito.verify;

@SpringBootTest(properties = {
        "app.admin.email=runtime-admin@example.com",
        "app.admin.password=A-runtime-admin-password"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BackendApiRuntimeTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private InventoryController inventoryController;

    @MockitoBean
    private JavaMailSender mailSender;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.update("DELETE FROM pos_order_payments");
        jdbcTemplate.update("DELETE FROM pos_orders");
        jdbcTemplate.update("DELETE FROM inventory_movements");
        jdbcTemplate.update("DELETE FROM audit_events");
        jdbcTemplate.update("DELETE FROM wastage_logs");
        jdbcTemplate.update("DELETE FROM custom_order_checkouts");
        jdbcTemplate.update("DELETE FROM custom_orders");
        jdbcTemplate.update("DELETE FROM staff_invites");
        jdbcTemplate.update("DELETE FROM staff_password_reset_tokens");
        jdbcTemplate.update("DELETE FROM staff_accounts");
        jdbcTemplate.update("DELETE FROM inventory_materials");
        jdbcTemplate.update("DELETE FROM inventory_categories");
        jdbcTemplate.update("DELETE FROM password_reset_tokens");
        jdbcTemplate.update("DELETE FROM customer_email_verification_tokens");
        jdbcTemplate.update("DELETE FROM customer_accounts");
    }

    @Test
    void protectedApisReturnExplicitJson401AndStaffCannotUseAdminRoutes() throws Exception {
        mockMvc.perform(get("/api/v1/inventory/materials"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error").value("unauthenticated"));
        mockMvc.perform(get("/api/v1/admin/staff"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("unauthenticated"));

        byte[] salt = PasswordSecurity.newSalt();
        jdbcTemplate.update("""
                INSERT INTO staff_accounts
                    (email, display_name, password_salt, password_hash, active, created_at, updated_at, created_by_email)
                VALUES (?, ?, ?, ?, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, ?)
                """,
                "runtime-staff@example.com",
                "Runtime Staff",
                PasswordSecurity.encodeSalt(salt),
                PasswordSecurity.hashPassword("A-runtime-staff-password", salt),
                "runtime-admin@example.com");
        MockHttpSession staffSession = login("runtime-staff@example.com", "A-runtime-staff-password");

        mockMvc.perform(get("/api/v1/inventory/materials").session(staffSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
        mockMvc.perform(get("/api/v1/admin/staff").session(staffSession))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error").value("forbidden"));
        mockMvc.perform(post("/api/v1/inventory/categories")
                        .session(staffSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Unauthorized category\"}"))
                .andExpect(status().isForbidden());

        Long staffId = jdbcTemplate.queryForObject(
                "SELECT id FROM staff_accounts WHERE email = 'runtime-staff@example.com'", Long.class);
        MockHttpSession adminSession = login("runtime-admin@example.com", "A-runtime-admin-password");
        mockMvc.perform(patch("/api/v1/admin/staff/" + staffId)
                        .session(adminSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
        mockMvc.perform(get("/api/v1/inventory/materials").session(staffSession))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("unauthenticated"));
    }

    @Test
    void workshopProfileIsAvailableAndStaffCanUpdateTheirDisplayName() throws Exception {
        mockMvc.perform(get("/api/v1/session"))
                .andExpect(status().isUnauthorized());

        MockHttpSession adminSession = login("runtime-admin@example.com", "A-runtime-admin-password");
        mockMvc.perform(get("/api/v1/session").session(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andExpect(jsonPath("$.displayName").value("Admin"))
                .andExpect(jsonPath("$.email").value("runtime-admin@example.com"));
        mockMvc.perform(patch("/api/v1/session/profile")
                        .session(adminSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"Changed Admin\"}"))
                .andExpect(status().isForbidden());

        byte[] salt = PasswordSecurity.newSalt();
        jdbcTemplate.update("""
                INSERT INTO staff_accounts
                    (email, display_name, password_salt, password_hash, active, created_at, updated_at, created_by_email)
                VALUES (?, ?, ?, ?, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, ?)
                """,
                "profile-staff@example.com",
                "Workshop Staff",
                PasswordSecurity.encodeSalt(salt),
                PasswordSecurity.hashPassword("A-profile-test-password", salt),
                "runtime-admin@example.com");
        MockHttpSession staffSession = login("profile-staff@example.com", "A-profile-test-password");
        mockMvc.perform(get("/api/v1/session").session(staffSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("STAFF"))
                .andExpect(jsonPath("$.displayName").value("Workshop Staff"));
        mockMvc.perform(patch("/api/v1/session/profile")
                        .session(staffSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"Updated Workshop Staff\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Updated Workshop Staff"));
        mockMvc.perform(get("/api/v1/session").session(staffSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Updated Workshop Staff"));
        assertEquals(1, jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM audit_events
                WHERE event_type = 'STAFF_PROFILE_UPDATED'
                  AND actor_email = 'profile-staff@example.com'
                """, Integer.class));
    }

    @Test
    void inventoryWastageAndOrderApisRespondWithPersistedActorAndLedgerData() throws Exception {
        MockHttpSession adminSession = login("runtime-admin@example.com", "A-runtime-admin-password");
        MvcResult categoryResult = mockMvc.perform(post("/api/v1/inventory/categories")
                        .session(adminSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Runtime Glass\"}"))
                .andExpect(status().isOk())
                .andReturn();
        long categoryId = readNumericField(categoryResult, "id");
        MvcResult materialResult = mockMvc.perform(post("/api/v1/inventory/materials")
                        .session(adminSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Runtime Mirror\",\"categoryId\":"
                                + categoryId + ",\"unitCode\":\"EA\",\"lowStockThreshold\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantityOnHand").value(0))
                .andReturn();
        long materialId = readNumericField(materialResult, "id");

        mockMvc.perform(post("/api/v1/inventory/movements")
                        .session(adminSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"materialId\":" + materialId
                                + ",\"type\":\"RECEIPT\",\"quantity\":5,\"reason\":\"Restock\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.actorEmail").value("runtime-admin@example.com"))
                .andExpect(jsonPath("$.balanceAfter").value(5));

        mockMvc.perform(post("/api/v1/wastage")
                        .session(adminSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":\"ORD-RUNTIME\",\"material\":\"Runtime Mirror\","
                                + "\"materialId\":" + materialId
                                + ",\"description\":\"damaged panel\",\"reason\":\"breakage\","
                                + "\"actionTaken\":\"scrapped\",\"quantity\":2,\"unit\":\"EA\","
                                + "\"loggedBy\":\"attacker@example.com\",\"orderStage\":\"Cutting\","
                                + "\"deliveryDate\":\"2026-11-04\",\"recoverable\":false,\"status\":\"Open\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.loggedBy").value("runtime-admin@example.com"));
        assertEquals(new BigDecimal("3.000"), jdbcTemplate.queryForObject(
                "SELECT quantity_on_hand FROM inventory_materials WHERE id = ?", BigDecimal.class, materialId));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM inventory_movements WHERE movement_type = 'WASTAGE'",
                Integer.class));

        MvcResult orderResult = mockMvc.perform(post("/api/v1/orders")
                        .session(adminSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customerName\":\"Runtime Customer\",\"contact\":\"09170000000\","
                                + "\"address\":\"Main Street\",\"item\":\"Mirror\",\"priceCents\":12500}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn();
        String orderId = readStringField(orderResult, "id");
        mockMvc.perform(patch("/api/v1/orders/" + orderId + "/status")
                        .session(adminSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"IN_PRODUCTION\",\"notes\":\"Started\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PRODUCTION"));
        mockMvc.perform(post("/api/v1/orders/" + orderId + "/payments")
                        .session(adminSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentMethod\":\"cash\",\"amountPaid\":130}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.paymentStatus").value("PAID"))
                .andExpect(jsonPath("$.status").value("COMPLETED"));
        assertTrue(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_events WHERE actor_email = 'runtime-admin@example.com'",
                Integer.class) >= 5);
    }

    @Test
    void quotePaymentSubmissionAndAdminVerificationRemainASeparateAuditedFlow() throws Exception {
        mockMvc.perform(post("/signup")
                        .param("fullName", "Payment Customer")
                        .param("email", "payment-customer@example.com")
                        .param("password", "A-runtime-customer-password")
                        .param("confirmPassword", "A-runtime-customer-password"))
                .andExpect(status().is3xxRedirection())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl(
                        "/?verificationSent"));
        ArgumentCaptor<SimpleMailMessage> verificationMail =
                ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(verificationMail.capture());
        java.util.regex.Matcher verificationToken = java.util.regex.Pattern
                .compile("verify-email\\.html\\?token=([A-Za-z0-9_-]{20,100})")
                .matcher(verificationMail.getValue().getText());
        assertTrue(verificationToken.find());
        mockMvc.perform(post("/verify-email").param("token", verificationToken.group(1)))
                .andExpect(status().is3xxRedirection())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl(
                        "/?verified"));
        MockHttpSession customerSession = login(
                "payment-customer@example.com", "A-runtime-customer-password");

        MvcResult requestResult = mockMvc.perform(multipart("/api/v1/custom-orders")
                        .session(customerSession)
                        .param("length", "12")
                        .param("width", "12")
                        .param("unit", "in")
                        .param("frame", "White"))
                .andExpect(status().isCreated())
                .andReturn();
        String orderId = readStringField(requestResult, "orderId");
        MockHttpSession adminSession = login(
                "runtime-admin@example.com", "A-runtime-admin-password");
        mockMvc.perform(post("/api/v1/admin/custom-orders/" + orderId + "/publish-quote")
                        .session(adminSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"baseMirrorAmount\":100,\"frameAmount\":0,"
                                + "\"laborAmount\":0,\"deliveryAmount\":0,\"notes\":\"Official quote\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FOR_QUOTATION"));

        MockMultipartFile receipt = new MockMultipartFile(
                "receipt", "transfer.png", "image/png", new byte[] {1, 2, 3});
        mockMvc.perform(multipart("/api/v1/custom-orders/" + orderId + "/checkout")
                        .file(receipt)
                        .session(customerSession)
                        .param("paymentMethod", "GCash")
                        .param("paymentReference", "RUNTIME-REFERENCE")
                        .param("termsConfirmed", "true"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PAYMENT_SUBMITTED"));
        assertEquals("QUOTE_BASED", jdbcTemplate.queryForObject(
                "SELECT submission_type FROM custom_order_checkouts WHERE order_id = ?",
                String.class,
                orderId));

        mockMvc.perform(post("/api/v1/admin/custom-orders/" + orderId + "/payment-verification")
                        .session(adminSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"VERIFIED\",\"notes\":\"Manually checked\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentStatus").value("VERIFIED"));
        assertEquals("VERIFIED", jdbcTemplate.queryForObject(
                "SELECT payment_status FROM custom_order_checkouts WHERE order_id = ?",
                String.class,
                orderId));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_events WHERE event_type = 'CUSTOM_ORDER_PAYMENT_VERIFIED'",
                Integer.class));
    }

    @Test
    void auditFailureRollsBackStockBalanceAndMovementInsert() throws Exception {
        MockHttpSession adminSession = login("runtime-admin@example.com", "A-runtime-admin-password");
        jdbcTemplate.update("""
                INSERT INTO inventory_categories (name, active, created_at, updated_at)
                VALUES ('Rollback Category', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """);
        Long categoryId = jdbcTemplate.queryForObject(
                "SELECT id FROM inventory_categories WHERE name = 'Rollback Category'", Long.class);
        jdbcTemplate.update("""
                INSERT INTO inventory_materials
                    (name, category_id, unit_code, quantity_on_hand, low_stock_threshold,
                     active, created_at, updated_at)
                VALUES ('Rollback Material', ?, 'EA', 5, 0, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, categoryId);
        Long materialId = jdbcTemplate.queryForObject(
                "SELECT id FROM inventory_materials WHERE name = 'Rollback Material'", Long.class);
        jdbcTemplate.execute("""
                ALTER TABLE audit_events
                ADD CONSTRAINT chk_test_reject_movement_audit
                CHECK (event_type <> 'INVENTORY_MOVEMENT_CREATED')
                """);
        try {
            assertThrows(RuntimeException.class, () -> inventoryController.createMovement(
                    new InventoryController.MovementMutation(
                            materialId, "USAGE", BigDecimal.ONE, "rollback probe", null, null, null),
                    adminSession));
        } finally {
            jdbcTemplate.execute("ALTER TABLE audit_events DROP CONSTRAINT chk_test_reject_movement_audit");
        }
        assertEquals(new BigDecimal("5.000"), jdbcTemplate.queryForObject(
                "SELECT quantity_on_hand FROM inventory_materials WHERE id = ?", BigDecimal.class, materialId));
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM inventory_movements WHERE material_id = ?", Integer.class, materialId));
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

    private long readNumericField(MvcResult result, String field) throws Exception {
        String body = result.getResponse().getContentAsString();
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("\"" + field + "\":(\\d+)")
                .matcher(body);
        if (!matcher.find()) {
            throw new AssertionError("Missing numeric field " + field + " in " + body);
        }
        return Long.parseLong(matcher.group(1));
    }

    private String readStringField(MvcResult result, String field) throws Exception {
        String body = result.getResponse().getContentAsString();
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("\"" + field + "\":\"([^\"]+)\"")
                .matcher(body);
        if (!matcher.find()) {
            throw new AssertionError("Missing string field " + field + " in " + body);
        }
        return matcher.group(1);
    }
}
