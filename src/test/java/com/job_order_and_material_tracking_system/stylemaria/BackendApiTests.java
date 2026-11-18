package com.job_order_and_material_tracking_system.stylemaria;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.UUID;
import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mock.web.MockHttpSession;

class BackendApiTests {

    private JdbcTemplate jdbcTemplate;
    private InventoryController inventory;
    private StaffInvitationService staffInvitations;
    private StaffController staff;
    private BusinessOrderController orders;
    private JavaMailSender mailSender;
    private MockHttpSession adminSession;
    private MockHttpSession staffSession;

    @BeforeEach
    void setUp() {
        DataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:backend-api-" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1",
                "sa",
                "");
        new ResourceDatabasePopulator(new ClassPathResource("schema.sql")).execute(dataSource);
        jdbcTemplate = new JdbcTemplate(dataSource);
        inventory = new InventoryController(jdbcTemplate);
        staffInvitations = new StaffInvitationService(jdbcTemplate);
        mailSender = org.mockito.Mockito.mock(JavaMailSender.class);
        staff = new StaffController(
                jdbcTemplate, staffInvitations, mailSender,
                "admin@example.com", "https://stylemaria.example", "no-reply@stylemaria.example");
        orders = new BusinessOrderController(jdbcTemplate);
        adminSession = session(DemoAuthController.ADMIN_ROLE, "admin@example.com");
        staffSession = session(DemoAuthController.STAFF_ROLE, "staff@example.com");
    }

    @Test
    void catalogStartsEmptyAndAllStockMutationsAppendSignedActorLedgerRows() {
        assertTrue(inventory.listMaterials(null, false).isEmpty());
        var category = inventory.createCategory(
                new InventoryController.CategoryMutation("Glass", true), adminSession);
        var material = inventory.createMaterial(
                new InventoryController.MaterialMutation(
                        "Mirror Glass", "MG-01", category.id(), "EA", new BigDecimal("2.000"), true),
                adminSession);
        assertEquals(new BigDecimal("0.000"), material.quantityOnHand());
        assertTrue(material.lowStock());

        var receipt = inventory.createMovement(
                new InventoryController.MovementMutation(
                        material.id(), "RECEIPT", new BigDecimal("10.000"), "Initial delivery", null, "PO-9", null),
                staffSession);
        var usage = inventory.createMovement(
                new InventoryController.MovementMutation(
                        material.id(), "USAGE", new BigDecimal("3.000"), "Cutting job", null, "ORD-1", null),
                staffSession);
        var returned = inventory.createMovement(
                new InventoryController.MovementMutation(
                        material.id(), "RETURN", new BigDecimal("1.000"), "Unused returned stock", null, "ORD-1", null),
                staffSession);

        assertEquals(new BigDecimal("10.000"), receipt.balanceAfter());
        assertEquals(new BigDecimal("-3.000"), usage.quantityDelta());
        assertEquals("USAGE", usage.type());
        assertEquals(new BigDecimal("8.000"), returned.balanceAfter());
        assertEquals("staff@example.com", usage.actorEmail());
        assertEquals("STAFF", usage.actorRole());
        assertEquals(3, inventory.listMovements(material.id(), null, null).size());
        assertEquals(3, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM inventory_movements WHERE material_id = ?", Integer.class, material.id()));
        assertEquals(5, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_events WHERE entity_type IN ('INVENTORY_CATEGORY', 'INVENTORY_MATERIAL')",
                Integer.class));
    }

    @Test
    void workshopEmailsCannotBeReusedAcrossAdminStaffAndCustomerRoles() {
        jdbcTemplate.update("""
                INSERT INTO customer_accounts
                    (full_name, email, password_salt, password_hash, created_at)
                VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP)
                """, "Customer", "customer@example.com", "salt", "hash");

        assertThrows(ApiException.class, () -> staffInvitations.issue(
                "customer@example.com", "Collision", new AuthenticatedActor(
                        DemoAuthController.ADMIN_ROLE, "admin@example.com")));
        assertThrows(ApiException.class, () -> staff.inviteStaff(
                new StaffController.StaffInviteRequest("Admin", "admin@example.com"), adminSession));
    }

    @Test
    void insufficientStockDoesNotAppendAMovement() {
        var category = inventory.createCategory(
                new InventoryController.CategoryMutation("Adhesive", true), adminSession);
        var material = inventory.createMaterial(
                new InventoryController.MaterialMutation(
                        "Glue", null, category.id(), "EA", BigDecimal.ZERO, true),
                adminSession);

        ApiException error = assertThrows(ApiException.class, () -> inventory.createMovement(
                new InventoryController.MovementMutation(
                        material.id(), "USAGE", BigDecimal.ONE, "Attempted usage", null, null, null),
                staffSession));

        assertEquals(HttpStatus.CONFLICT, error.status());
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM inventory_movements WHERE material_id = ?", Integer.class, material.id()));
    }

    @Test
    void invitationStoresOnlyHashAcceptsOnceAndAuthenticatesStaff() {
        var response = staff.inviteStaff(
                new StaffController.StaffInviteRequest("Shop Floor", "Staff@Example.com"), adminSession);
        assertEquals(HttpStatus.ACCEPTED, response.getStatusCode());
        var safeResponse = assertInstanceOf(StaffController.StaffInviteResponse.class, response.getBody());
        assertEquals("staff@example.com", safeResponse.email());

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        String mailText = captor.getValue().getText();
        String token = mailText.substring(mailText.indexOf("token=") + "token=".length()).split("\\s")[0];
        String storedHash = jdbcTemplate.queryForObject(
                "SELECT token_hash FROM staff_invites WHERE email = ?", String.class, "staff@example.com");
        assertEquals(64, storedHash.length());
        assertEquals(PasswordSecurity.hashToken(token), storedHash);
        assertTrue(!storedHash.equals(token));

        var accepted = staff.acceptInvitation(
                new StaffController.StaffInvitationAcceptance(token, "A-long-staff-passphrase"));
        assertEquals(HttpStatus.CREATED, accepted.getStatusCode());
        assertEquals("STAFF", assertInstanceOf(StaffController.AcceptedStaff.class, accepted.getBody()).role());
        String savedHash = jdbcTemplate.queryForObject(
                "SELECT password_hash FROM staff_accounts WHERE email = ?", String.class, "staff@example.com");
        assertTrue(!savedHash.contains("A-long-staff-passphrase"));
        assertEquals("STAFF_INVITATION_ACCEPTED", jdbcTemplate.queryForObject(
                "SELECT event_type FROM audit_events WHERE entity_type = 'STAFF_ACCOUNT' ORDER BY id DESC LIMIT 1",
                String.class));

        ApiException reused = assertThrows(ApiException.class, () -> staff.acceptInvitation(
                new StaffController.StaffInvitationAcceptance(token, "A-long-staff-passphrase")));
        assertEquals(HttpStatus.GONE, reused.status());

        DemoAuthController auth = new DemoAuthController(
                jdbcTemplate, mailSender, "", "", "https://stylemaria.example", "no-reply@stylemaria.example");
        org.springframework.mock.web.MockHttpServletRequest request =
                new org.springframework.mock.web.MockHttpServletRequest();
        assertEquals("redirect:/pos.html",
                auth.login("staff@example.com", "A-long-staff-passphrase", request));
    }

    @Test
    void failedInvitationDeliveryRevokesTheInviteAndWritesAudit() {
        doThrow(new MailSendException("SMTP unavailable"))
                .when(mailSender).send(any(SimpleMailMessage.class));

        var response = staff.inviteStaff(
                new StaffController.StaffInviteRequest("Shop Floor", "failure@example.com"), adminSession);

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        Timestamp revokedAt = jdbcTemplate.queryForObject(
                "SELECT revoked_at FROM staff_invites WHERE email = ?", Timestamp.class, "failure@example.com");
        assertTrue(revokedAt != null);
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_events WHERE event_type = 'STAFF_INVITATION_REVOKED'",
                Integer.class));
    }

    @Test
    void posOrderPaymentStatusAndAuditArePersistedTogetherByTheApi() {
        var createdResponse = orders.createOrder(
                new BusinessOrderController.OrderCreateRequest(
                        "Customer", "09170000000", "Main Street", "Custom mirror", 12500L, null),
                staffSession);
        assertEquals(HttpStatus.CREATED, createdResponse.getStatusCode());
        var created = assertInstanceOf(BusinessOrderController.OrderView.class, createdResponse.getBody());
        assertEquals("PENDING", created.status());
        assertEquals(12500L, created.priceCents());

        var paymentResponse = orders.submitPayment(
                created.id(),
                new BusinessOrderController.PaymentRequest("cash", new BigDecimal("130.00"), null),
                staffSession);
        assertEquals(HttpStatus.CREATED, paymentResponse.getStatusCode());
        var paid = assertInstanceOf(BusinessOrderController.OrderView.class, paymentResponse.getBody());
        assertEquals("COMPLETED", paid.status());
        assertEquals("PAID", paid.paymentStatus());
        assertEquals("CASH", paid.paymentMethod());
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM pos_order_payments WHERE order_id = ?", Integer.class, created.id()));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_events WHERE event_type = 'BUSINESS_ORDER_PAYMENT_SUBMITTED'",
                Integer.class));
        assertEquals(1, orders.listPayments(created.id()).size());

        ApiException repeated = assertThrows(ApiException.class, () -> orders.submitPayment(
                created.id(),
                new BusinessOrderController.PaymentRequest("cash", new BigDecimal("125.00"), null),
                staffSession));
        assertEquals(HttpStatus.CONFLICT, repeated.status());
    }

    private MockHttpSession session(String role, String email) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(DemoAuthController.SESSION_ROLE, role);
        session.setAttribute(DemoAuthController.SESSION_EMAIL, email);
        return session;
    }
}
