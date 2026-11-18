package com.job_order_and_material_tracking_system.stylemaria;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import jakarta.servlet.http.HttpSession;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/inventory")
class InventoryController {

    private static final String MATERIAL_SELECT = """
            SELECT m.id, m.name, m.sku, m.category_id, c.name AS category_name,
                   m.unit_code, u.display_name AS unit_name, m.quantity_on_hand,
                   m.low_stock_threshold, m.active, m.created_at, m.updated_at
            FROM inventory_materials m
            JOIN inventory_categories c ON c.id = m.category_id
            JOIN inventory_units u ON u.code = m.unit_code
            """;

    private final JdbcTemplate jdbcTemplate;

    InventoryController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/materials")
    public List<MaterialView> listMaterials(
            @RequestParam(required = false) Long categoryId,
            @RequestParam(defaultValue = "false") boolean includeInactive) {
        StringBuilder sql = new StringBuilder(MATERIAL_SELECT);
        List<Object> parameters = new ArrayList<>();
        List<String> conditions = new ArrayList<>();
        if (categoryId != null) {
            conditions.add("m.category_id = ?");
            parameters.add(categoryId);
        }
        if (!includeInactive) {
            conditions.add("m.active = TRUE");
        }
        if (!conditions.isEmpty()) {
            sql.append(" WHERE ").append(String.join(" AND ", conditions));
        }
        sql.append(" ORDER BY c.name, m.name, m.id");
        return jdbcTemplate.query(sql.toString(), (result, row) -> toMaterialView(
                result.getLong("id"),
                result.getString("name"),
                result.getString("sku"),
                result.getLong("category_id"),
                result.getString("category_name"),
                result.getString("unit_code"),
                result.getString("unit_name"),
                result.getBigDecimal("quantity_on_hand"),
                result.getBigDecimal("low_stock_threshold"),
                result.getBoolean("active"),
                result.getTimestamp("created_at"),
                result.getTimestamp("updated_at")), parameters.toArray());
    }

    @PostMapping("/materials")
    @Transactional
    public MaterialView createMaterial(@RequestBody MaterialMutation input, HttpSession session) {
        AuthenticatedActor actor = AuthenticatedActor.from(session);
        validateMaterial(input, true);
        ensureActiveCategory(input.categoryId());
        ensureActiveUnit(input.unitCode());
        Instant now = Instant.now();
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO inventory_materials
                        (name, sku, category_id, unit_code, quantity_on_hand, low_stock_threshold,
                         active, created_at, updated_at)
                    VALUES (?, ?, ?, ?, 0, ?, ?, ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, input.name().trim());
            statement.setString(2, normalizeOptional(input.sku()));
            statement.setLong(3, input.categoryId());
            statement.setString(4, input.unitCode().trim().toUpperCase(Locale.ROOT));
            statement.setBigDecimal(5, input.lowStockThreshold() == null
                    ? java.math.BigDecimal.ZERO : input.lowStockThreshold());
            statement.setBoolean(6, input.active() == null || input.active());
            statement.setTimestamp(7, Timestamp.from(now));
            statement.setTimestamp(8, Timestamp.from(now));
            return statement;
        }, keyHolder);
        long id = generatedId(keyHolder);
        AuditEvents.append(jdbcTemplate, actor, "INVENTORY_MATERIAL_CREATED", "INVENTORY_MATERIAL",
                Long.toString(id), Map.of("name", input.name().trim(), "categoryId", input.categoryId()));
        return material(id);
    }

    @PatchMapping("/materials/{materialId}")
    @Transactional
    public MaterialView updateMaterial(
            @PathVariable long materialId,
            @RequestBody MaterialMutation input,
            HttpSession session) {
        AuthenticatedActor actor = AuthenticatedActor.from(session);
        if (input == null || (input.name() == null && input.sku() == null
                && input.categoryId() == null && input.unitCode() == null
                && input.lowStockThreshold() == null && input.active() == null)) {
            throw badRequest("Supply at least one catalog field to update.");
        }
        ensureMaterialExists(materialId);
        List<String> assignments = new ArrayList<>();
        List<Object> parameters = new ArrayList<>();
        if (input.name() != null) {
            if (input.name().isBlank() || input.name().trim().length() > 120) {
                throw badRequest("Material name must contain 1 to 120 characters.");
            }
            assignments.add("name = ?");
            parameters.add(input.name().trim());
        }
        if (input.sku() != null) {
            if (input.sku().trim().length() > 40) {
                throw badRequest("SKU must be no more than 40 characters.");
            }
            assignments.add("sku = ?");
            parameters.add(normalizeOptional(input.sku()));
        }
        if (input.categoryId() != null) {
            ensureActiveCategory(input.categoryId());
            assignments.add("category_id = ?");
            parameters.add(input.categoryId());
        }
        if (input.unitCode() != null) {
            ensureActiveUnit(input.unitCode());
            assignments.add("unit_code = ?");
            parameters.add(input.unitCode().trim().toUpperCase(Locale.ROOT));
        }
        if (input.lowStockThreshold() != null) {
            validateQuantity(input.lowStockThreshold(), "Low-stock threshold", true);
            assignments.add("low_stock_threshold = ?");
            parameters.add(input.lowStockThreshold());
        }
        if (input.active() != null) {
            assignments.add("active = ?");
            parameters.add(input.active());
        }
        assignments.add("updated_at = ?");
        parameters.add(Timestamp.from(Instant.now()));
        parameters.add(materialId);
        jdbcTemplate.update("UPDATE inventory_materials SET "
                + String.join(", ", assignments) + " WHERE id = ?", parameters.toArray());
        AuditEvents.append(jdbcTemplate, actor, "INVENTORY_MATERIAL_UPDATED", "INVENTORY_MATERIAL",
                Long.toString(materialId), Map.of("updatedFields", assignments));
        return material(materialId);
    }

    @GetMapping("/categories")
    public List<CategoryView> listCategories() {
        return jdbcTemplate.query("""
                SELECT id, name, active, created_at, updated_at
                FROM inventory_categories
                ORDER BY name, id
                """, (result, row) -> new CategoryView(
                result.getLong("id"),
                result.getString("name"),
                result.getBoolean("active"),
                result.getTimestamp("created_at"),
                result.getTimestamp("updated_at")));
    }

    @PostMapping("/categories")
    @Transactional
    public CategoryView createCategory(@RequestBody CategoryMutation input, HttpSession session) {
        AuthenticatedActor actor = AuthenticatedActor.from(session);
        String name = validateCategoryName(input == null ? null : input.name());
        Instant now = Instant.now();
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO inventory_categories (name, active, created_at, updated_at)
                    VALUES (?, TRUE, ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, name);
            statement.setTimestamp(2, Timestamp.from(now));
            statement.setTimestamp(3, Timestamp.from(now));
            return statement;
        }, keyHolder);
        long id = generatedId(keyHolder);
        AuditEvents.append(jdbcTemplate, actor, "INVENTORY_CATEGORY_CREATED", "INVENTORY_CATEGORY",
                Long.toString(id), Map.of("name", name));
        return category(id);
    }

    @PatchMapping("/categories/{categoryId}")
    @Transactional
    public CategoryView updateCategory(
            @PathVariable long categoryId,
            @RequestBody CategoryMutation input,
            HttpSession session) {
        AuthenticatedActor actor = AuthenticatedActor.from(session);
        if (input == null || (input.name() == null && input.active() == null)) {
            throw badRequest("Supply a category name or active status.");
        }
        category(categoryId);
        List<String> assignments = new ArrayList<>();
        List<Object> parameters = new ArrayList<>();
        if (input.name() != null) {
            assignments.add("name = ?");
            parameters.add(validateCategoryName(input.name()));
        }
        if (input.active() != null) {
            assignments.add("active = ?");
            parameters.add(input.active());
        }
        assignments.add("updated_at = ?");
        parameters.add(Timestamp.from(Instant.now()));
        parameters.add(categoryId);
        jdbcTemplate.update("UPDATE inventory_categories SET "
                + String.join(", ", assignments) + " WHERE id = ?", parameters.toArray());
        AuditEvents.append(jdbcTemplate, actor, "INVENTORY_CATEGORY_UPDATED", "INVENTORY_CATEGORY",
                Long.toString(categoryId), Map.of("updatedFields", assignments));
        return category(categoryId);
    }

    @GetMapping("/units")
    public List<UnitView> listUnits() {
        return jdbcTemplate.query("""
                SELECT code, display_name, active
                FROM inventory_units
                WHERE active = TRUE
                ORDER BY display_name, code
                """, (result, row) -> new UnitView(
                result.getString("code"), result.getString("display_name"), result.getBoolean("active")));
    }

    @GetMapping("/movements")
    public List<MovementView> listMovements(
            @RequestParam(required = false) Long materialId,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String orderId) {
        List<String> conditions = new ArrayList<>();
        List<Object> parameters = new ArrayList<>();
        if (materialId != null) {
            conditions.add("m.material_id = ?");
            parameters.add(materialId);
        }
        if (type != null && !type.isBlank()) {
            String dbType = normalizeMovementType(type);
            conditions.add("m.movement_type = ?");
            parameters.add(dbType);
        }
        if (orderId != null && !orderId.isBlank()) {
            conditions.add("m.reference_id = ?");
            parameters.add(orderId.trim());
        }
        String sql = """
                SELECT m.id, m.material_id, i.name AS material_name, m.movement_type,
                       m.quantity_delta, m.balance_after, m.actor_email, m.actor_role,
                       m.occurred_at, m.reference_id, m.reason
                FROM inventory_movements m
                JOIN inventory_materials i ON i.id = m.material_id
                """ + (conditions.isEmpty() ? "" : " WHERE " + String.join(" AND ", conditions))
                + " ORDER BY m.occurred_at DESC, m.id DESC";
        return jdbcTemplate.query(sql, (result, row) -> new MovementView(
                result.getLong("id"),
                result.getLong("material_id"),
                result.getString("material_name"),
                apiMovementType(result.getString("movement_type")),
                result.getBigDecimal("quantity_delta"),
                result.getBigDecimal("balance_after"),
                result.getString("actor_email"),
                result.getString("actor_role"),
                result.getTimestamp("occurred_at"),
                result.getString("reference_id"),
                result.getString("reason")), parameters.toArray());
    }

    @PostMapping("/movements")
    @Transactional
    public MovementView createMovement(@RequestBody MovementMutation input, HttpSession session) {
        AuthenticatedActor actor = AuthenticatedActor.from(session);
        if (input == null || input.materialId() == null) {
            throw badRequest("Select a material.");
        }
        String apiType = input.type() == null ? "" : input.type().trim().toUpperCase(Locale.ROOT);
        if (!List.of("RECEIPT", "USAGE", "RETURN", "ADJUSTMENT").contains(apiType)) {
            throw badRequest("Movement type must be RECEIPT, USAGE, RETURN, or ADJUSTMENT.");
        }
        validateQuantity(input.quantity(), "Movement quantity", false);
        if (input.reason() == null || input.reason().isBlank() || input.reason().trim().length() > 500) {
            throw badRequest("Movement reason must contain 1 to 500 characters.");
        }
        if (input.orderId() != null && input.orderId().trim().length() > 100) {
            throw badRequest("Order reference must be no more than 100 characters.");
        }
        if (apiType.equals("ADJUSTMENT")
                && !List.of("INCREASE", "DECREASE").contains(
                        input.adjustmentDirection() == null ? "" : input.adjustmentDirection().trim().toUpperCase(Locale.ROOT))) {
            throw badRequest("Adjustment direction must be INCREASE or DECREASE.");
        }

        InventoryBalance material;
        try {
            material = jdbcTemplate.queryForObject("""
                    SELECT id, name, quantity_on_hand, active
                    FROM inventory_materials
                    WHERE id = ?
                    FOR UPDATE
                    """, (result, row) -> new InventoryBalance(
                    result.getLong("id"),
                    result.getString("name"),
                    result.getBigDecimal("quantity_on_hand"),
                    result.getBoolean("active")), input.materialId());
        } catch (EmptyResultDataAccessException exception) {
            throw new ApiException(HttpStatus.NOT_FOUND, "not_found", "Material was not found.");
        }
        if (!material.active() && !apiType.equals("RETURN")) {
            throw new ApiException(HttpStatus.CONFLICT, "inactive_material",
                    "Inactive materials can only receive returned stock.");
        }

        java.math.BigDecimal delta = switch (apiType) {
            case "RECEIPT", "RETURN" -> input.quantity();
            case "USAGE" -> input.quantity().negate();
            case "ADJUSTMENT" -> "INCREASE".equalsIgnoreCase(input.adjustmentDirection().trim())
                    ? input.quantity() : input.quantity().negate();
            default -> throw badRequest("Unsupported movement type.");
        };
        java.math.BigDecimal balanceAfter = material.quantityOnHand().add(delta);
        if (balanceAfter.signum() < 0) {
            throw new ApiException(HttpStatus.CONFLICT, "insufficient_stock",
                    "The movement would reduce available stock below zero.");
        }
        Instant now = Instant.now();
        jdbcTemplate.update("""
                UPDATE inventory_materials
                SET quantity_on_hand = ?, updated_at = ?
                WHERE id = ?
                """, balanceAfter, Timestamp.from(now), material.id());
        String dbType = apiType.equals("USAGE") ? "USE" : apiType;
        String referenceId = normalizeOptional(input.orderId());
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO inventory_movements
                        (material_id, movement_type, quantity_delta, balance_after, actor_email,
                         actor_role, occurred_at, reference_id, reason)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, material.id());
            statement.setString(2, dbType);
            statement.setBigDecimal(3, delta);
            statement.setBigDecimal(4, balanceAfter);
            statement.setString(5, actor.email());
            statement.setString(6, actor.role());
            statement.setTimestamp(7, Timestamp.from(now));
            statement.setString(8, referenceId);
            statement.setString(9, input.reason().trim());
            return statement;
        }, keyHolder);
        long id = generatedId(keyHolder);
        AuditEvents.append(jdbcTemplate, actor, "INVENTORY_MOVEMENT_CREATED", "INVENTORY_MATERIAL",
                Long.toString(material.id()), Map.of(
                        "movementId", id,
                        "type", apiType,
                        "quantityDelta", delta,
                        "balanceAfter", balanceAfter,
                        "referenceId", referenceId == null ? "" : referenceId));
        return new MovementView(
                id,
                material.id(),
                material.name(),
                apiType,
                delta,
                balanceAfter,
                actor.email(),
                actor.role(),
                Timestamp.from(now),
                referenceId,
                input.reason().trim());
    }

    private MaterialView material(long id) {
        try {
            return jdbcTemplate.queryForObject(MATERIAL_SELECT + " WHERE m.id = ?",
                    (result, row) -> toMaterialView(
                            result.getLong("id"),
                            result.getString("name"),
                            result.getString("sku"),
                            result.getLong("category_id"),
                            result.getString("category_name"),
                            result.getString("unit_code"),
                            result.getString("unit_name"),
                            result.getBigDecimal("quantity_on_hand"),
                            result.getBigDecimal("low_stock_threshold"),
                            result.getBoolean("active"),
                            result.getTimestamp("created_at"),
                            result.getTimestamp("updated_at")), id);
        } catch (EmptyResultDataAccessException exception) {
            throw new ApiException(HttpStatus.NOT_FOUND, "not_found", "Material was not found.");
        }
    }

    private MaterialView toMaterialView(
            long id,
            String name,
            String sku,
            long categoryId,
            String categoryName,
            String unitCode,
            String unitName,
            java.math.BigDecimal quantity,
            java.math.BigDecimal threshold,
            boolean active,
            Timestamp createdAt,
            Timestamp updatedAt) {
        return new MaterialView(id, name, sku, categoryId, categoryName, unitCode, unitName,
                quantity, threshold, active, quantity.compareTo(threshold) <= 0, createdAt, updatedAt);
    }

    private CategoryView category(long id) {
        try {
            return jdbcTemplate.queryForObject("""
                    SELECT id, name, active, created_at, updated_at
                    FROM inventory_categories WHERE id = ?
                    """, (result, row) -> new CategoryView(
                    result.getLong("id"),
                    result.getString("name"),
                    result.getBoolean("active"),
                    result.getTimestamp("created_at"),
                    result.getTimestamp("updated_at")), id);
        } catch (EmptyResultDataAccessException exception) {
            throw new ApiException(HttpStatus.NOT_FOUND, "not_found", "Category was not found.");
        }
    }

    private void ensureMaterialExists(long id) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM inventory_materials WHERE id = ?", Integer.class, id);
        if (count == null || count == 0) {
            throw new ApiException(HttpStatus.NOT_FOUND, "not_found", "Material was not found.");
        }
    }

    private void ensureActiveCategory(long categoryId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM inventory_categories WHERE id = ? AND active = TRUE",
                Integer.class, categoryId);
        if (count == null || count == 0) {
            throw badRequest("Select an active inventory category.");
        }
    }

    private void ensureActiveUnit(String unitCode) {
        if (unitCode == null || unitCode.isBlank()) {
            throw badRequest("Select an inventory unit.");
        }
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM inventory_units WHERE code = ? AND active = TRUE",
                Integer.class, unitCode.trim().toUpperCase(Locale.ROOT));
        if (count == null || count == 0) {
            throw badRequest("Select a supported active inventory unit.");
        }
    }

    private void validateMaterial(MaterialMutation input, boolean creating) {
        if (input == null
                || input.name() == null || input.name().isBlank() || input.name().trim().length() > 120
                || input.categoryId() == null
                || input.unitCode() == null || input.unitCode().isBlank()) {
            throw badRequest("Material name, category, and unit are required.");
        }
        if (input.sku() != null && input.sku().trim().length() > 40) {
            throw badRequest("SKU must be no more than 40 characters.");
        }
        if (input.lowStockThreshold() != null) {
            validateQuantity(input.lowStockThreshold(), "Low-stock threshold", true);
        } else if (creating) {
            // Catalog rows always begin with a zero threshold unless the caller supplies one.
        }
    }

    private String validateCategoryName(String rawName) {
        if (rawName == null || rawName.isBlank() || rawName.trim().length() > 80) {
            throw badRequest("Category name must contain 1 to 80 characters.");
        }
        return rawName.trim();
    }

    private void validateQuantity(java.math.BigDecimal quantity, String label, boolean allowZero) {
        if (quantity == null
                || (allowZero ? quantity.signum() < 0 : quantity.signum() <= 0)
                || quantity.scale() > 3
                || quantity.precision() - quantity.scale() > 11) {
            throw badRequest(label + (allowZero
                    ? " must be non-negative with at most three decimal places."
                    : " must be positive with at most three decimal places."));
        }
    }

    private String normalizeMovementType(String rawType) {
        String type = rawType.trim().toUpperCase(Locale.ROOT);
        return switch (type) {
            case "USAGE" -> "USE";
            case "RECEIPT", "USE", "RETURN", "ADJUSTMENT", "WASTAGE" -> type;
            default -> throw badRequest("Unknown movement type.");
        };
    }

    private String apiMovementType(String storedType) {
        return "USE".equals(storedType) ? "USAGE" : storedType;
    }

    private String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private long generatedId(KeyHolder keyHolder) {
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("The database did not return an ID for the new record.");
        }
        return key.longValue();
    }

    private ApiException badRequest(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, "invalid_request", message);
    }

    public record MaterialMutation(
            String name,
            String sku,
            Long categoryId,
            String unitCode,
            java.math.BigDecimal lowStockThreshold,
            Boolean active) {
    }

    public record MaterialView(
            long id,
            String name,
            String sku,
            long categoryId,
            String categoryName,
            String unitCode,
            String unitName,
            java.math.BigDecimal quantityOnHand,
            java.math.BigDecimal lowStockThreshold,
            boolean active,
            boolean lowStock,
            Timestamp createdAt,
            Timestamp updatedAt) {
    }

    public record CategoryMutation(String name, Boolean active) {
    }

    public record CategoryView(long id, String name, boolean active, Timestamp createdAt, Timestamp updatedAt) {
    }

    public record UnitView(String code, String displayName, boolean active) {
    }

    public record MovementMutation(
            Long materialId,
            String type,
            java.math.BigDecimal quantity,
            String reason,
            String adjustmentDirection,
            String orderId,
            String description) {
    }

    public record MovementView(
            long id,
            long materialId,
            String materialName,
            String type,
            java.math.BigDecimal quantityDelta,
            java.math.BigDecimal balanceAfter,
            String actorEmail,
            String actorRole,
            Timestamp occurredAt,
            String orderId,
            String reason) {
    }

    private record InventoryBalance(long id, String name, java.math.BigDecimal quantityOnHand, boolean active) {
    }
}
