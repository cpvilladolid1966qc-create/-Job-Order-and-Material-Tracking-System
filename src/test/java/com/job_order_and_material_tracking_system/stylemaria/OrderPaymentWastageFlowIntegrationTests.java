package com.job_order_and_material_tracking_system.stylemaria;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;

@SpringBootTest(properties = {
        "app.admin.email=operations-flow-admin@example.com",
        "app.admin.password=Operations-flow-test-only-passphrase"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OrderPaymentWastageFlowIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clearWorkflowFixture() {
        cleanWorkflowFixture();
    }

    @AfterEach
    void removeWorkflowFixture() {
        cleanWorkflowFixture();
    }

    private void cleanWorkflowFixture() {
        jdbcTemplate.update("DELETE FROM pos_order_payments");
        jdbcTemplate.update("DELETE FROM pos_orders");
        jdbcTemplate.update("DELETE FROM inventory_movements");
        jdbcTemplate.update("DELETE FROM audit_events");
        jdbcTemplate.update("DELETE FROM wastage_logs");
        jdbcTemplate.update("DELETE FROM inventory_materials");
        jdbcTemplate.update("DELETE FROM inventory_categories");
    }

    @Test
    void staffWorkflowPersistsStockWastageOrderPaymentAndSameActorAudit() throws Exception {
        mockMvc.perform(get("/api/v1/orders"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("unauthenticated"));
        mockMvc.perform(get("/api/v1/inventory/materials"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("unauthenticated"));

        MockHttpSession adminSession = login(
                "operations-flow-admin@example.com", "Operations-flow-test-only-passphrase");
        MvcResult categoryResponse = mockMvc.perform(post("/api/v1/inventory/categories")
                        .session(adminSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Operations Fabric\"}"))
                .andExpect(status().isOk())
                .andReturn();
        long categoryId = readNumericField(categoryResponse, "id");
        MvcResult materialResponse = mockMvc.perform(post("/api/v1/inventory/materials")
                        .session(adminSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Operations Cloth\",\"categoryId\":"
                                + categoryId + ",\"unitCode\":\"M\",\"lowStockThreshold\":0}"))
                .andExpect(status().isOk())
                .andReturn();
        long materialId = readNumericField(materialResponse, "id");
        mockMvc.perform(post("/api/v1/inventory/materials")
                        .session(adminSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Inactive Cloth\",\"categoryId\":"
                                + categoryId + ",\"unitCode\":\"M\",\"active\":false}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/inventory/materials").session(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value((int) materialId))
                .andExpect(jsonPath("$[0].name").value("Operations Cloth"))
                .andExpect(jsonPath("$[0].unitCode").value("M"))
                .andExpect(jsonPath("$[1]").doesNotExist());

        mockMvc.perform(post("/api/v1/inventory/movements")
                        .session(adminSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"materialId\":" + materialId
                                + ",\"type\":\"RECEIPT\",\"quantity\":10,\"reason\":\"Stock received\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/wastage")
                        .session(adminSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":\"FLOW-ORDER\",\"material\":\"Operations Cloth\","
                                + "\"materialId\":" + materialId
                                + ",\"description\":\"damaged cutting piece\",\"reason\":\"tear\","
                                + "\"actionTaken\":\"scrapped\",\"quantity\":1.125,\"unit\":\"M\","
                                + "\"loggedBy\":\"untrusted-client\",\"orderStage\":\"Cutting\","
                                + "\"deliveryDate\":\"2026-11-04\",\"recoverable\":false,\"status\":\"Open\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.material").value("Operations Cloth"))
                .andExpect(jsonPath("$.unit").value("M"))
                .andExpect(jsonPath("$.loggedBy").value("operations-flow-admin@example.com"));
        assertEquals(new BigDecimal("8.875"), jdbcTemplate.queryForObject(
                "SELECT quantity_on_hand FROM inventory_materials WHERE id = ?",
                BigDecimal.class, materialId));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM inventory_movements WHERE movement_type = 'WASTAGE'",
                Integer.class));

        MvcResult orderResponse = mockMvc.perform(post("/api/v1/orders")
                        .session(adminSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customerName\":\"Flow Customer\",\"contact\":\"09170000000\","
                                + "\"address\":\"Main Street\",\"item\":\"Custom mirror\","
                                + "\"priceCents\":15000}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn();
        String orderId = readStringField(orderResponse, "id");
        mockMvc.perform(patch("/api/v1/orders/" + orderId + "/status")
                        .session(adminSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"IN_PRODUCTION\",\"notes\":\"Started\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PRODUCTION"));
        mockMvc.perform(post("/api/v1/orders/" + orderId + "/payments")
                        .session(adminSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentMethod\":\"cash\",\"amountPaid\":160}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.paymentStatus").value("PAID"))
                .andExpect(jsonPath("$.status").value("COMPLETED"));
        mockMvc.perform(get("/api/v1/orders").session(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(orderId))
                .andExpect(jsonPath("$[0].paymentStatus").value("PAID"));

        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM pos_order_payments WHERE order_id = ?",
                Integer.class, orderId));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_events WHERE event_type = 'BUSINESS_ORDER_PAYMENT_SUBMITTED' "
                        + "AND actor_email = 'operations-flow-admin@example.com'",
                Integer.class));
        assertNotNull(jdbcTemplate.queryForObject(
                "SELECT occurred_at FROM audit_events WHERE event_type = 'WASTAGE_RECORDED'",
                java.sql.Timestamp.class));
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
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("\"" + field + "\":(\\d+)")
                .matcher(result.getResponse().getContentAsString());
        if (!matcher.find()) {
            throw new AssertionError("Missing numeric field " + field);
        }
        return Long.parseLong(matcher.group(1));
    }

    private String readStringField(MvcResult result, String field) throws Exception {
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("\"" + field + "\":\"([^\"]+)\"")
                .matcher(result.getResponse().getContentAsString());
        if (!matcher.find()) {
            throw new AssertionError("Missing string field " + field);
        }
        return matcher.group(1);
    }
}
