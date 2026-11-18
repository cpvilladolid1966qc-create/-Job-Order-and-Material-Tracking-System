package com.job_order_and_material_tracking_system.stylemaria;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpSession;

class WastageControllerTests {

    private JdbcTemplate jdbcTemplate;
    private WastageController controller;

    @BeforeEach
    void setUp() {
        DataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:wastage-" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1",
                "sa",
                "");
        new ResourceDatabasePopulator(new ClassPathResource("schema.sql")).execute(dataSource);
        jdbcTemplate = new JdbcTemplate(dataSource);
        controller = new WastageController(jdbcTemplate);
        jdbcTemplate.update("""
                INSERT INTO inventory_categories (name, active, created_at, updated_at)
                VALUES ('Glass', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """);
        Long categoryId = jdbcTemplate.queryForObject(
                "SELECT id FROM inventory_categories WHERE name = 'Glass'", Long.class);
        jdbcTemplate.update("""
                INSERT INTO inventory_materials
                    (name, category_id, unit_code, quantity_on_hand, low_stock_threshold,
                     active, created_at, updated_at)
                VALUES ('Mirror Glass', ?, 'EA', 5, 0, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, categoryId);
    }

    @Test
    void createsAndReadsWastageLogFromDatabase() {
        var request = new WastageController.WastageRequest(
                "ORD-1001",
                "Mirror Glass",
                "Panel chipped during cutting",
                "Machine calibration issue",
                "Scrap and re-cut",
                new BigDecimal("1"),
                "pcs",
                "Production Team",
                "Cutting",
                LocalDate.of(2026, 10, 28),
                true,
                "Open");

        MockHttpSession session = new MockHttpSession();
        session.setAttribute(DemoAuthController.SESSION_ROLE, DemoAuthController.STAFF_ROLE);
        session.setAttribute(DemoAuthController.SESSION_EMAIL, "staff@example.com");
        var response = controller.createLog(request, session);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        var saved = assertInstanceOf(WastageController.WastageLog.class, response.getBody());
        assertEquals(new BigDecimal("75.00"), saved.materialCost());
        assertEquals(new BigDecimal("120.00"), saved.totalImpact());
        assertEquals(1, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM wastage_logs", Integer.class));
        assertEquals(new BigDecimal("4.000"), jdbcTemplate.queryForObject(
                "SELECT quantity_on_hand FROM inventory_materials WHERE name = 'Mirror Glass'",
                BigDecimal.class));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM inventory_movements WHERE movement_type = 'WASTAGE' AND wastage_log_id = ?",
                Integer.class,
                saved.id()));
        assertEquals("staff@example.com", saved.loggedBy());
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_events WHERE event_type = 'WASTAGE_RECORDED'",
                Integer.class));
        var loaded = controller.getLogs().getFirst();
        assertEquals(saved.id(), loaded.id());
        assertEquals(saved.orderId(), loaded.orderId());
        assertEquals(saved.quantity(), loaded.quantity().stripTrailingZeros());
        assertEquals(saved.totalImpact(), loaded.totalImpact());
    }

    @Test
    void wastageCannotConsumeMoreThanAvailableStock() {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(DemoAuthController.SESSION_ROLE, DemoAuthController.STAFF_ROLE);
        session.setAttribute(DemoAuthController.SESSION_EMAIL, "staff@example.com");
        var request = new WastageController.WastageRequest(
                "ORD-1002",
                "Mirror Glass",
                "Large broken panel",
                "Accidental damage",
                "Discarded safely",
                new BigDecimal("6"),
                "pcs",
                "Spoofed actor",
                "Cutting",
                LocalDate.of(2026, 10, 28),
                false,
                "Open");

        var response = controller.createLog(request, session);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals(0, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM wastage_logs", Integer.class));
        assertEquals(0, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM inventory_movements", Integer.class));
        assertEquals(new BigDecimal("5.000"), jdbcTemplate.queryForObject(
                "SELECT quantity_on_hand FROM inventory_materials WHERE name = 'Mirror Glass'",
                BigDecimal.class));
    }
}
