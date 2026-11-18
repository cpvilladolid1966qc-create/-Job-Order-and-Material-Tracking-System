package com.job_order_and_material_tracking_system.stylemaria;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
        "app.admin.email=inventory-flow-admin@example.com",
        "app.admin.password=Inventory-flow-test-only-passphrase"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InventoryFlowIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clearInventoryFixture() {
        cleanInventoryFixture();
    }

    @AfterEach
    void removeInventoryFixture() {
        cleanInventoryFixture();
    }

    private void cleanInventoryFixture() {
        jdbcTemplate.update("DELETE FROM inventory_movements");
        jdbcTemplate.update("DELETE FROM audit_events");
        jdbcTemplate.update("DELETE FROM inventory_materials");
        jdbcTemplate.update("DELETE FROM inventory_categories");
    }

    @Test
    void authenticatedCatalogAndMovementsPersistAcrossReadsWhileAnonymousAccessIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/inventory/materials"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("unauthenticated"));

        MockHttpSession adminSession = login("inventory-flow-admin@example.com",
                "Inventory-flow-test-only-passphrase");
        MvcResult categoryResponse = mockMvc.perform(post("/api/v1/inventory/categories")
                        .session(adminSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Integration Stone\"}"))
                .andExpect(status().isOk())
                .andReturn();
        long categoryId = readNumericField(categoryResponse, "id");

        MvcResult materialResponse = mockMvc.perform(post("/api/v1/inventory/materials")
                        .session(adminSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Integration Tile\",\"categoryId\":"
                                + categoryId + ",\"unitCode\":\"EA\",\"lowStockThreshold\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantityOnHand").value(0))
                .andExpect(jsonPath("$.lowStock").value(true))
                .andReturn();
        long materialId = readNumericField(materialResponse, "id");

        mockMvc.perform(post("/api/v1/inventory/movements")
                        .session(adminSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"materialId\":" + materialId
                                + ",\"type\":\"RECEIPT\",\"quantity\":8,\"reason\":\"Delivery\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balanceAfter").value(8))
                .andExpect(jsonPath("$.actorEmail").value("inventory-flow-admin@example.com"));
        mockMvc.perform(post("/api/v1/inventory/movements")
                        .session(adminSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"materialId\":" + materialId
                                + ",\"type\":\"USAGE\",\"quantity\":3,\"reason\":\"Job consumption\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantityDelta").value(-3))
                .andExpect(jsonPath("$.balanceAfter").value(5));

        mockMvc.perform(get("/api/v1/inventory/materials?includeInactive=true").session(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(materialId))
                .andExpect(jsonPath("$[0].quantityOnHand").value(5));
        mockMvc.perform(get("/api/v1/inventory/movements?materialId=" + materialId).session(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].actorRole").value("ADMIN"));

        mockMvc.perform(patch("/api/v1/inventory/materials/" + materialId)
                        .session(adminSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
        mockMvc.perform(get("/api/v1/inventory/materials").session(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        org.junit.jupiter.api.Assertions.assertEquals(2, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM inventory_movements WHERE material_id = ?",
                Integer.class, materialId));
    }

    private MockHttpSession login(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/login")
                        .param("username", email)
                        .param("password", password))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
        org.junit.jupiter.api.Assertions.assertNotNull(session);
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
}
