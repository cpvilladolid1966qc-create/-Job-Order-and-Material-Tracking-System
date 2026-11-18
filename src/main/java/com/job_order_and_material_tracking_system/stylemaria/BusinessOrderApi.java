package com.job_order_and_material_tracking_system.stylemaria;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import jakarta.servlet.http.HttpSession;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
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
@RequestMapping("/api/v1/orders")
class BusinessOrderController {

    private static final BigDecimal MAX_ORDER_AMOUNT = new BigDecimal("9999999999.99");
    private static final String ORDER_SELECT = """
            SELECT order_id, customer_name, contact, address, item_description,
                   amount, paid_amount, status, payment_status, payment_method,
                   created_at, updated_at, completed_at
            FROM pos_orders
            """;

    private final JdbcTemplate jdbcTemplate;

    BusinessOrderController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping
    public List<OrderView> listOrders(@RequestParam(required = false) String status) {
        if (status == null || status.isBlank()) {
            return jdbcTemplate.query(ORDER_SELECT + " ORDER BY created_at DESC, id DESC",
                    (result, row) -> toOrderView(result));
        }
        String normalized = normalizeStatus(status);
        return jdbcTemplate.query(ORDER_SELECT + " WHERE status = ? ORDER BY created_at DESC, id DESC",
                (result, row) -> toOrderView(result), normalized);
    }

    @PostMapping
    @Transactional
    public ResponseEntity<?> createOrder(@RequestBody OrderCreateRequest input, HttpSession session) {
        AuthenticatedActor actor = AuthenticatedActor.from(session);
        String validationError = validateOrder(input);
        if (validationError != null) {
            throw badRequest(validationError);
        }
        BigDecimal amount = orderAmount(input).setScale(2, RoundingMode.UNNECESSARY);
        String orderId = "ORD-" + UUID.randomUUID().toString().replace("-", "")
                .substring(0, 10).toUpperCase(Locale.ROOT);
        Instant now = Instant.now();
        jdbcTemplate.update("""
                INSERT INTO pos_orders
                    (order_id, customer_name, contact, address, item_description, amount,
                     paid_amount, status, payment_status, created_at, updated_at, created_by_email)
                VALUES (?, ?, ?, ?, ?, ?, 0, 'PENDING', 'UNPAID', ?, ?, ?)
                """,
                orderId,
                input.customerName().trim(),
                normalizeOptional(input.contact()),
                normalizeOptional(input.address()),
                input.item().trim(),
                amount,
                Timestamp.from(now),
                Timestamp.from(now),
                actor.email());
        AuditEvents.append(jdbcTemplate, actor, "BUSINESS_ORDER_CREATED", "POS_ORDER",
                orderId, Map.of(
                        "customerName", input.customerName().trim(),
                        "item", input.item().trim(),
                        "amount", amount,
                        "status", "PENDING"));
        return ResponseEntity.status(HttpStatus.CREATED).body(order(orderId));
    }

    @PatchMapping("/{orderId}/status")
    @Transactional
    public OrderView updateStatus(
            @PathVariable String orderId,
            @RequestBody OrderStatusRequest input,
            HttpSession session) {
        AuthenticatedActor actor = AuthenticatedActor.from(session);
        if (input == null || input.status() == null) {
            throw badRequest("An order status is required.");
        }
        String nextStatus = normalizeStatus(input.status());
        if (!List.of("PENDING", "FOR_CONFIRMATION", "IN_PRODUCTION", "COMPLETED")
                .contains(nextStatus)) {
            throw badRequest("Choose a supported operational order status.");
        }
        if (input.notes() != null && input.notes().trim().length() > 1000) {
            throw badRequest("Order notes must be no more than 1,000 characters.");
        }
        OrderRecord current = orderForUpdate(orderId);
        if (current.status().equals(nextStatus)) {
            throw new ApiException(HttpStatus.CONFLICT, "invalid_transition",
                    "The order already has that status.");
        }
        if (!validStatusTransition(current.status(), nextStatus, current.paymentStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, "invalid_transition",
                    "The requested order status transition is not allowed.");
        }
        Instant now = Instant.now();
        jdbcTemplate.update("""
                UPDATE pos_orders SET status = ?, updated_at = ?
                WHERE order_id = ?
                """, nextStatus, Timestamp.from(now), orderId);
        AuditEvents.append(jdbcTemplate, actor, "BUSINESS_ORDER_STATUS_CHANGED", "POS_ORDER",
                orderId, Map.of(
                        "before", current.status(),
                        "after", nextStatus,
                        "notes", normalizeOptional(input.notes()) == null ? "" : input.notes().trim()));
        return order(orderId);
    }

    @PostMapping("/{orderId}/payments")
    @Transactional
    public ResponseEntity<?> submitPayment(
            @PathVariable String orderId,
            @RequestBody PaymentRequest input,
            HttpSession session) {
        AuthenticatedActor actor = AuthenticatedActor.from(session);
        if (input == null || input.paymentMethod() == null || input.paymentMethod().isBlank()) {
            throw badRequest("Payment method is required.");
        }
        String method = normalizePaymentMethod(input.paymentMethod());
        if (input.amountPaid() == null || input.amountPaid().scale() > 2
                || input.amountPaid().signum() <= 0
                || input.amountPaid().compareTo(MAX_ORDER_AMOUNT) > 0) {
            throw badRequest("Payment amount must be positive with no more than two decimal places.");
        }
        if (input.reference() != null && input.reference().trim().length() > 100) {
            throw badRequest("Payment reference must be no more than 100 characters.");
        }
        OrderRecord order = orderForUpdate(orderId);
        if ("PAID".equals(order.paymentStatus()) || "COMPLETED".equals(order.status())) {
            throw new ApiException(HttpStatus.CONFLICT, "payment_already_recorded",
                    "This order already has a recorded payment.");
        }
        if (input.amountPaid().compareTo(order.amount()) < 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "insufficient_payment",
                    "Payment must cover the full order amount.");
        }
        if (!"CASH".equals(method) && input.amountPaid().compareTo(order.amount()) != 0) {
            throw badRequest("Non-cash payment must match the order total exactly.");
        }
        BigDecimal changeDue = input.amountPaid().subtract(order.amount()).setScale(2, RoundingMode.UNNECESSARY);
        Instant now = Instant.now();
        jdbcTemplate.update("""
                INSERT INTO pos_order_payments
                    (order_id, amount, tendered_amount, change_due, payment_method, reference,
                     submitted_by_email, submitted_by_role, submitted_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                orderId,
                order.amount(),
                input.amountPaid(),
                changeDue,
                method,
                normalizeOptional(input.reference()),
                actor.email(),
                actor.role(),
                Timestamp.from(now));
        jdbcTemplate.update("""
                UPDATE pos_orders
                SET paid_amount = ?, status = 'COMPLETED', payment_status = 'PAID',
                    payment_method = ?, updated_at = ?, completed_at = ?
                WHERE order_id = ?
                """, order.amount(), method, Timestamp.from(now), Timestamp.from(now), orderId);
        AuditEvents.append(jdbcTemplate, actor, "BUSINESS_ORDER_PAYMENT_SUBMITTED", "POS_ORDER",
                orderId, Map.of(
                        "amount", order.amount(),
                        "tenderedAmount", input.amountPaid(),
                        "changeDue", changeDue,
                        "paymentMethod", method,
                        "paymentStatus", "PAID"));
        return ResponseEntity.status(HttpStatus.CREATED).body(order(orderId));
    }

    @GetMapping("/{orderId}/payments")
    public List<PaymentView> listPayments(@PathVariable String orderId) {
        Integer exists = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM pos_orders WHERE order_id = ?", Integer.class, orderId);
        if (exists == null || exists == 0) {
            throw new ApiException(HttpStatus.NOT_FOUND, "not_found", "Order was not found.");
        }
        return jdbcTemplate.query("""
                SELECT id, order_id, amount, tendered_amount, change_due, payment_method, reference,
                       submitted_by_email, submitted_by_role, submitted_at
                FROM pos_order_payments
                WHERE order_id = ?
                ORDER BY submitted_at DESC, id DESC
                """, (result, row) -> new PaymentView(
                result.getLong("id"),
                result.getString("order_id"),
                result.getBigDecimal("amount"),
                result.getBigDecimal("tendered_amount"),
                result.getBigDecimal("change_due"),
                result.getString("payment_method"),
                result.getString("reference"),
                result.getString("submitted_by_email"),
                result.getString("submitted_by_role"),
                result.getTimestamp("submitted_at")), orderId);
    }

    private String validateOrder(OrderCreateRequest input) {
        if (input == null || input.customerName() == null || input.customerName().isBlank()
                || input.customerName().trim().length() > 120
                || input.item() == null || input.item().isBlank() || input.item().trim().length() > 500) {
            return "Customer name and item description are required.";
        }
        if (input.contact() != null && input.contact().trim().length() > 40) {
            return "Contact must be no more than 40 characters.";
        }
        if (input.address() != null && input.address().trim().length() > 500) {
            return "Address must be no more than 500 characters.";
        }
        try {
            BigDecimal amount = orderAmount(input);
            if (amount.signum() <= 0 || amount.compareTo(MAX_ORDER_AMOUNT) > 0
                    || amount.scale() > 2) {
                return "Order amount must be positive with no more than two decimal places.";
            }
        } catch (IllegalArgumentException exception) {
            return "Supply either a positive priceCents value or a valid amount.";
        }
        return null;
    }

    private BigDecimal orderAmount(OrderCreateRequest input) {
        if (input.amount() != null) {
            return input.amount();
        }
        if (input.priceCents() == null || input.priceCents() <= 0) {
            throw new IllegalArgumentException("Invalid order amount.");
        }
        return BigDecimal.valueOf(input.priceCents(), 2);
    }

    private boolean validStatusTransition(String current, String next, String paymentStatus) {
        return switch (current) {
            case "PENDING" -> "FOR_CONFIRMATION".equals(next)
                    || "IN_PRODUCTION".equals(next)
                    || ("COMPLETED".equals(next) && "PAID".equals(paymentStatus));
            case "FOR_CONFIRMATION" -> "IN_PRODUCTION".equals(next)
                    || ("COMPLETED".equals(next) && "PAID".equals(paymentStatus));
            case "IN_PRODUCTION" -> "COMPLETED".equals(next) && "PAID".equals(paymentStatus);
            default -> false;
        };
    }

    private String normalizeStatus(String raw) {
        return raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT).replace(' ', '_');
    }

    private String normalizePaymentMethod(String raw) {
        String method = raw.trim().toUpperCase(Locale.ROOT).replace(" ", "");
        if (method.equals("CASH")) {
            return "CASH";
        }
        if (method.equals("GCASH")) {
            return "GCASH";
        }
        if (method.equals("MAYA") || method.equals("MAYAQR")) {
            return "MAYA";
        }
        throw badRequest("Payment method must be CASH, GCASH, or MAYA.");
    }

    private String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private OrderRecord orderForUpdate(String orderId) {
        try {
            return jdbcTemplate.queryForObject(ORDER_SELECT + " WHERE order_id = ? FOR UPDATE",
                    (result, row) -> new OrderRecord(
                            result.getString("order_id"),
                            result.getString("customer_name"),
                            result.getString("contact"),
                            result.getString("address"),
                            result.getString("item_description"),
                            result.getBigDecimal("amount"),
                            result.getBigDecimal("paid_amount"),
                            result.getString("status"),
                            result.getString("payment_status"),
                            result.getString("payment_method"),
                            result.getTimestamp("created_at"),
                            result.getTimestamp("updated_at"),
                            result.getTimestamp("completed_at")), orderId);
        } catch (EmptyResultDataAccessException exception) {
            throw new ApiException(HttpStatus.NOT_FOUND, "not_found", "Order was not found.");
        }
    }

    private OrderView order(String orderId) {
        try {
            return jdbcTemplate.queryForObject(ORDER_SELECT + " WHERE order_id = ?",
                    (result, row) -> toOrderView(result), orderId);
        } catch (EmptyResultDataAccessException exception) {
            throw new ApiException(HttpStatus.NOT_FOUND, "not_found", "Order was not found.");
        }
    }

    private OrderView toOrderView(java.sql.ResultSet result) throws java.sql.SQLException {
        BigDecimal amount = result.getBigDecimal("amount");
        BigDecimal paidAmount = result.getBigDecimal("paid_amount");
        Timestamp paidAt = result.getTimestamp("completed_at");
        return new OrderView(
                result.getString("order_id"),
                result.getString("customer_name"),
                result.getString("contact"),
                result.getString("address"),
                result.getString("item_description"),
                amount,
                amount.movePointRight(2).longValueExact(),
                paidAmount == null ? 0L : paidAmount.movePointRight(2).longValueExact(),
                result.getString("status"),
                result.getString("payment_status"),
                result.getString("payment_method"),
                result.getTimestamp("created_at"),
                paidAt);
    }

    private ApiException badRequest(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, "invalid_request", message);
    }

    public record OrderCreateRequest(
            String customerName,
            String contact,
            String address,
            String item,
            Long priceCents,
            BigDecimal amount) {
    }

    public record OrderStatusRequest(String status, String notes) {
    }

    public record PaymentRequest(String paymentMethod, BigDecimal amountPaid, String reference) {
    }

    public record OrderView(
            String id,
            String customerName,
            String contact,
            String address,
            String item,
            BigDecimal amount,
            long priceCents,
            long paidCents,
            String status,
            String paymentStatus,
            String paymentMethod,
            Timestamp createdAt,
            Timestamp paidAt) {
    }

    public record PaymentView(
            long id,
            String orderId,
            BigDecimal amount,
            BigDecimal tenderedAmount,
            BigDecimal changeDue,
            String paymentMethod,
            String reference,
            String submittedByEmail,
            String submittedByRole,
            Timestamp submittedAt) {
    }

    private record OrderRecord(
            String id,
            String customerName,
            String contact,
            String address,
            String item,
            BigDecimal amount,
            BigDecimal paidAmount,
            String status,
            String paymentStatus,
            String paymentMethod,
            Timestamp createdAt,
            Timestamp updatedAt,
            Timestamp completedAt) {
    }
}
