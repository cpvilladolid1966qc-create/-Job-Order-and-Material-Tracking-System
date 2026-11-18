package com.job_order_and_material_tracking_system.stylemaria;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;
import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

class PersistenceSchemaTests {

    private DataSource dataSource;
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        dataSource = new SingleConnectionDataSource(
                "jdbc:h2:mem:persistence-schema-" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1",
                "sa",
                "",
                true);
        jdbcTemplate = new JdbcTemplate(dataSource);
    }

    @Test
    void schemaCanBeAppliedMoreThanOnceAndStartsBusinessTablesEmpty() {
        ResourceDatabasePopulator schema = new ResourceDatabasePopulator(new ClassPathResource("schema.sql"));
        schema.execute(dataSource);
        schema.execute(dataSource);

        assertEquals(6, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM inventory_units", Integer.class));
        assertEquals(0, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM inventory_categories", Integer.class));
        assertEquals(0, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM inventory_materials", Integer.class));
        assertEquals(0, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM inventory_movements", Integer.class));
        assertEquals(0, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM staff_accounts", Integer.class));
        assertEquals(0, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM staff_invites", Integer.class));
        assertEquals(0, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM audit_events", Integer.class));
        assertEquals(0, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM wastage_logs", Integer.class));
    }

    @Test
    void stockAndMovementConstraintsRejectInvalidValues() {
        new ResourceDatabasePopulator(new ClassPathResource("schema.sql")).execute(dataSource);
        jdbcTemplate.update(
                "INSERT INTO inventory_categories (name, created_at, updated_at) VALUES (?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                "Glass");
        long categoryId = jdbcTemplate.queryForObject(
                "SELECT id FROM inventory_categories WHERE name = ?",
                Long.class,
                "Glass");
        jdbcTemplate.update(
                """
                        INSERT INTO inventory_materials
                            (name, category_id, unit_code, quantity_on_hand, low_stock_threshold, created_at, updated_at)
                        VALUES (?, ?, ?, 0, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                        """,
                "Mirror glass",
                categoryId,
                "M");
        long materialId = jdbcTemplate.queryForObject(
                "SELECT id FROM inventory_materials WHERE name = ?",
                Long.class,
                "Mirror glass");

        jdbcTemplate.update(
                """
                        INSERT INTO inventory_movements
                            (material_id, movement_type, quantity_delta, balance_after, actor_email, actor_role, occurred_at, reason)
                        VALUES (?, 'RECEIPT', 1, 1, 'admin@example.com', 'ADMIN', CURRENT_TIMESTAMP, 'Opening receipt')
                        """,
                materialId);
        assertEquals(1, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM inventory_movements", Integer.class));

        assertThrows(DataAccessException.class, () -> jdbcTemplate.update(
                "UPDATE inventory_materials SET quantity_on_hand = -1 WHERE id = ?",
                materialId));
        assertThrows(DataAccessException.class, () -> jdbcTemplate.update(
                """
                        INSERT INTO inventory_movements
                            (material_id, movement_type, quantity_delta, balance_after, actor_email, actor_role, occurred_at, reason)
                        VALUES (?, 'USE', 1, 1, 'staff@example.com', 'STAFF', CURRENT_TIMESTAMP, 'Invalid positive use')
                        """,
                materialId));
        assertThrows(DataAccessException.class, () -> jdbcTemplate.update(
                """
                        INSERT INTO inventory_movements
                            (material_id, movement_type, quantity_delta, balance_after, actor_email, actor_role, occurred_at, reason)
                        VALUES (?, 'RECEIPT', 1, -1, 'admin@example.com', 'ADMIN', CURRENT_TIMESTAMP, 'Invalid balance')
                        """,
                materialId));
    }

    @Test
    void customOrderMigrationAddsMissingPaymentVerificationColumnsAndIsRepeatable() {
        new ResourceDatabasePopulator(new ClassPathResource("schema.sql")).execute(dataSource);
        jdbcTemplate.execute("ALTER TABLE custom_order_checkouts DROP COLUMN payment_verified_at");
        jdbcTemplate.execute("ALTER TABLE custom_order_checkouts DROP COLUMN payment_verified_by_email");

        CustomOrderSchemaMigration migration = new CustomOrderSchemaMigration(jdbcTemplate);
        migration.run(null);
        migration.run(null);

        assertEquals(2, jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
                        WHERE LOWER(TABLE_NAME) = 'custom_order_checkouts'
                            AND LOWER(COLUMN_NAME) IN ('payment_verified_at', 'payment_verified_by_email')
                        """,
                Integer.class));
    }
}
