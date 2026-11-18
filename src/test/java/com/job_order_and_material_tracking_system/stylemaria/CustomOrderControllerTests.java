package com.job_order_and_material_tracking_system.stylemaria;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.mock;

import java.math.BigDecimal;
import java.util.UUID;
import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.web.multipart.MultipartFile;

class CustomOrderControllerTests {

    private JdbcTemplate jdbcTemplate;
    private CustomOrderController controller;
    private AdminCustomOrderController adminController;
    private CustomerCustomOrderController customerController;
    private JavaMailSender mailSender;

    @BeforeEach
    void setUp() {
        DataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:custom-orders-" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1",
                "sa",
                "");
        new ResourceDatabasePopulator(new ClassPathResource("schema.sql")).execute(dataSource);
        jdbcTemplate = new JdbcTemplate(dataSource);
        controller = new CustomOrderController(jdbcTemplate);
        mailSender = mock(JavaMailSender.class);
        adminController = new AdminCustomOrderController(jdbcTemplate);
        customerController = new CustomerCustomOrderController(jdbcTemplate);
    }

    @Test
    void acceptsCustomFrameRequestWithMessageAndReferenceImage() throws Exception {
        MultipartFile image = new MockMultipartFile(
                "referenceImage", "mirror.png", "image/png", new byte[] {1, 2, 3});

        var response = controller.submitRequest(
                new BigDecimal("32"),
                new BigDecimal("24"),
                "in",
                "Custom (Request)",
                "Brushed brass finish",
                "Please use a rounded frame.",
                image);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        var body = assertInstanceOf(CustomOrderController.CustomOrderResponse.class, response.getBody());
        assertEquals("CM-000001", body.orderId());
        assertEquals("REQUEST_RECEIVED", body.status());
        assertEquals(1, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM custom_orders", Integer.class));
        assertEquals("Please use a rounded frame.",
                jdbcTemplate.queryForObject("SELECT message FROM custom_orders WHERE order_id = ?",
                        String.class,
                        body.orderId()));
    }

    @Test
    void rejectsCustomFrameWithoutDescription() throws Exception {
        var response = controller.submitRequest(
                new BigDecimal("32"),
                new BigDecimal("24"),
                "in",
                "Custom (Request)",
                " ",
                "",
                null);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        var body = assertInstanceOf(CustomOrderController.ApiError.class, response.getBody());
        assertEquals("Describe your custom frame request in 1 to 200 characters.", body.message());
    }

    @Test
    void rejectsUnsupportedDimensionsAndReferenceImageTypes() throws Exception {
        var dimensionResponse = controller.submitRequest(
                new BigDecimal("501"),
                new BigDecimal("24"),
                "in",
                "Solid Wood",
                "",
                "",
                null);
        assertEquals(HttpStatus.BAD_REQUEST, dimensionResponse.getStatusCode());

        MultipartFile image = new MockMultipartFile(
                "referenceImage", "design.gif", "image/gif", new byte[] {1, 2, 3});
        var imageResponse = controller.submitRequest(
                new BigDecimal("32"),
                new BigDecimal("24"),
                "in",
                "Solid Wood",
                "",
                "",
                image);
        assertEquals(HttpStatus.BAD_REQUEST, imageResponse.getStatusCode());
    }

    @Test
    void calculatesCheckoutQuoteUsingMirrorPriceCalculatorDefaults() {
        var quote = controller.getQuote();

        assertEquals(new BigDecimal("8620.00"), quote.subtotal());
        assertEquals(new BigDecimal("150.00"), quote.deliveryFee());
        assertEquals(new BigDecimal("8770.00"), quote.total());
        assertEquals(new BigDecimal("3508.00"), quote.downpayment());
        assertEquals(new BigDecimal("5262.00"), quote.remainingBalance());
    }

    @Test
    void recordsDemoCheckoutWithShippingAndSelectedPaymentMethod() throws Exception {
        var response = controller.placeDemoOrder(
                new BigDecimal("32"),
                new BigDecimal("24"),
                "in",
                "Solid Wood",
                "",
                "",
                null,
                "Juan Dela Cruz",
                "123 Main Street, Metro Manila",
                "09123456789",
                "GCash",
                "",
                true,
                null,
                createCustomerSession());

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        var body = assertInstanceOf(CustomOrderController.CheckoutResponse.class, response.getBody());
        assertEquals("CM-000001", body.orderId());
        assertEquals("DEMO_SUBMITTED", body.status());
        assertEquals(new BigDecimal("8770.00"), body.total());
        assertEquals(1, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM custom_orders", Integer.class));
        assertEquals(1, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM custom_order_checkouts", Integer.class));
    }

    @Test
    void rejectsDemoCheckoutWithoutTermsConfirmation() throws Exception {
        var response = controller.placeDemoOrder(
                new BigDecimal("32"),
                new BigDecimal("24"),
                "in",
                "Solid Wood",
                "",
                "",
                null,
                "Juan Dela Cruz",
                "123 Main Street, Metro Manila",
                "09123456789",
                "GCash",
                "",
                false,
                null,
                createCustomerSession());

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        var body = assertInstanceOf(CustomOrderController.ApiError.class, response.getBody());
        assertEquals("Confirm the demo checkout details before continuing.", body.message());
    }

    @Test
    void adminCanListAndLoadSubmittedCustomOrderDetails() throws Exception {
        controller.placeDemoOrder(
                new BigDecimal("32"),
                new BigDecimal("24"),
                "in",
                "Solid Wood",
                "",
                "Rounded corners, please",
                new MockMultipartFile("referenceImage", "mirror.png", "image/png", new byte[] {1, 2, 3}),
                "Juan Dela Cruz",
                "123 Main Street",
                "09123456789",
                "GCash",
                "PAY-123",
                true,
                null,
                createCustomerSession());

        var list = adminController.listRequests();
        assertEquals(1, list.size());
        assertEquals("CM-000001", list.getFirst().requestId());
        assertEquals("Juan Dela Cruz", list.getFirst().customerName());
        assertEquals("NEW_REQUEST", list.getFirst().status());

        var response = adminController.getRequest("CM-000001");
        var details = assertInstanceOf(AdminCustomOrderController.CustomOrderDetail.class, response.getBody());
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("123 Main Street", details.address());
        assertEquals("Rounded corners, please", details.message());
        assertEquals("PAY-123", details.paymentReference());
        assertNotNull(details.submittedAt());

        var imageResponse = adminController.getReferenceImage("CM-000001");
        assertEquals(HttpStatus.OK, imageResponse.getStatusCode());
        assertEquals(MediaType.IMAGE_PNG, imageResponse.getHeaders().getContentType());
        assertEquals(3, ((byte[]) imageResponse.getBody()).length);
    }

    @Test
    void customRequestWithoutCheckoutIsAvailableForAdminReview() throws Exception {
        controller.submitRequest(
                new BigDecimal("12"),
                new BigDecimal("12"),
                "in",
                "White",
                "",
                "Thin gold frame",
                null);

        var requests = adminController.listRequests();

        assertEquals(1, requests.size());
        assertEquals("CM-000001", requests.getFirst().requestId());
        assertEquals("NEW_REQUEST", requests.getFirst().status());
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM custom_order_checkouts WHERE order_id = ?",
                Integer.class,
                "CM-000001"));
    }

    @Test
    void customRequestCopiesAuthenticatedCustomerContactDetailsForAdminReview() throws Exception {
        MockHttpSession session = createCustomerSession();
        controller.submitRequest(new BigDecimal("12"), new BigDecimal("12"), "in", "White",
                "", "Thin gold frame", null, session);

        var request = adminController.listRequests().getFirst();
        var details = assertInstanceOf(
                AdminCustomOrderController.CustomOrderDetail.class,
                adminController.getRequest(request.requestId()).getBody());
        assertEquals("Trisha Dela Cruz", request.customerName());
        assertEquals("trisha@example.com", details.email());
        assertEquals("09171234567", details.contactNumber());
        assertEquals("45 Sample Street", details.address());
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM custom_orders WHERE requester_customer_id = ?",
                Integer.class,
                jdbcTemplate.queryForObject(
                        "SELECT id FROM customer_accounts WHERE email = ?",
                        Long.class,
                        "trisha@example.com")));
    }

    @Test
    void customerCanTrackOnlyOwnRequestsWithoutDemoRows() throws Exception {
        MockHttpSession session = createCustomerSession();
        controller.submitRequest(new BigDecimal("12"), new BigDecimal("12"), "in", "White",
                "", "My mirror request", null, session);
        MockHttpSession otherCustomerSession = new MockHttpSession();
        otherCustomerSession.setAttribute(DemoAuthController.SESSION_ROLE, DemoAuthController.USER_ROLE);
        otherCustomerSession.setAttribute(DemoAuthController.SESSION_CUSTOMER_ID, 99999L);

        var response = customerController.listOrders(session);
        var orders = assertInstanceOf(java.util.List.class, response.getBody());
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, orders.size());
        var tracked = (CustomerCustomOrderController.CustomerCustomOrderSummary) orders.getFirst();
        assertEquals("CM-000001", tracked.requestId());
        assertEquals("NEW_REQUEST", tracked.status());

        assertEquals(0, ((java.util.List<?>) customerController.listOrders(otherCustomerSession).getBody()).size());
        assertEquals(HttpStatus.NOT_FOUND,
                customerController.getOrder("CM-000001", otherCustomerSession).getStatusCode());
    }

    @Test
    void trackingApiRequiresAuthenticatedCustomerSession() {
        assertEquals(HttpStatus.UNAUTHORIZED, customerController.listOrders(null).getStatusCode());
    }

    @Test
    void customRequestSubmissionRequiresCustomerAccount() throws Exception {
        var response = controller.submitRequest(
                new BigDecimal("12"),
                new BigDecimal("12"),
                "in",
                "White",
                "",
                "",
                null,
                null);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertEquals(0, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM custom_orders", Integer.class));
    }

    @Test
    void customerCannotCheckoutAnotherCustomersCustomOrder() throws Exception {
        MockHttpSession ownerSession = createCustomerSession();
        var orderResponse = controller.submitRequest(
                new BigDecimal("12"),
                new BigDecimal("12"),
                "in",
                "White",
                "",
                "",
                null,
                ownerSession);
        assertEquals(HttpStatus.CREATED, orderResponse.getStatusCode());

        MockHttpSession otherCustomerSession = createCustomerSession("other@example.com");
        var checkoutResponse = controller.submitCheckout(
                "CM-000001", "GCash", "",
                true,
                null,
                otherCustomerSession);

        assertEquals(HttpStatus.NOT_FOUND, checkoutResponse.getStatusCode());
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM custom_order_checkouts",
                Integer.class));
    }

    @Test
    void adminCanPersistRequestReviewStatusAndNotes() throws Exception {
        controller.placeDemoOrder(
                new BigDecimal("32"),
                new BigDecimal("24"),
                "in",
                "Solid Wood",
                "",
                "",
                null,
                "Juan Dela Cruz",
                "123 Main Street",
                "09123456789",
                "GCash",
                "",
                true,
                null,
                createCustomerSession());

        var response = adminController.updateRequest(
                "CM-000001",
                new AdminCustomOrderController.AdminCustomOrderUpdate("FOR_QUOTATION", "Prepare brass finish quote"),
                adminSession());

        assertEquals(HttpStatus.OK, response.getStatusCode());
        var details = assertInstanceOf(
                AdminCustomOrderController.CustomOrderDetail.class,
                adminController.getRequest("CM-000001").getBody());
        assertEquals("FOR_QUOTATION", details.status());
        assertEquals("Prepare brass finish quote", details.notes());
        assertNotNull(details.statusUpdatedAt());
    }

    @Test
    void adminCanSaveQuoteDraftAndTotalIsCalculated() throws Exception {
        controller.submitRequest(
                new BigDecimal("12"),
                new BigDecimal("12"),
                "in",
                "White",
                "",
                "",
                null);

        var response = adminController.saveQuoteDraft(
                "CM-000001",
                new AdminCustomOrderController.AdminQuoteRequest(
                        new BigDecimal("1200.00"),
                        new BigDecimal("250.50"),
                        new BigDecimal("300.00"),
                        new BigDecimal("150.00"),
                        "Mirror with polished edges"),
                adminSession());

        assertEquals(HttpStatus.OK, response.getStatusCode());
        var details = assertInstanceOf(
                AdminCustomOrderController.CustomOrderDetail.class,
                adminController.getRequest("CM-000001").getBody());
        assertEquals(new BigDecimal("1900.50"), details.quoteTotal());
        assertEquals("Mirror with polished edges", details.notes());
        assertEquals("NEW_REQUEST", details.status());
    }

    @Test
    void savingAnUnchangedZeroValueQuoteStillReturnsSuccess() throws Exception {
        controller.submitRequest(
                new BigDecimal("12"),
                new BigDecimal("12"),
                "in",
                "White",
                "",
                "",
                null);

        var response = adminController.saveQuoteDraft(
                "CM-000001",
                new AdminCustomOrderController.AdminQuoteRequest(
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        ""),
                adminSession());

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void adminCanPublishOfficialQuoteToCustomerWithoutEmail() throws Exception {
        MockHttpSession customerSession = saveAuthenticatedCustomerRequest();
        var draftResponse = adminController.saveQuoteDraft(
                "CM-000001",
                new AdminCustomOrderController.AdminQuoteRequest(
                        new BigDecimal("1200.00"),
                        new BigDecimal("250.50"),
                        new BigDecimal("300.00"),
                        new BigDecimal("150.00"),
                        "Please reply to approve."),
                adminSession());
        assertEquals(HttpStatus.OK, draftResponse.getStatusCode());
        var unpublished = assertInstanceOf(
                CustomerCustomOrderController.CustomerCustomOrderDetail.class,
                customerController.getOrder("CM-000001", customerSession).getBody());
        assertNull(unpublished.quoteSentAt());
        assertNull(unpublished.quoteNotes());
        assertNull(unpublished.quoteTotal());

        var response = adminController.publishOfficialQuote(
                "CM-000001",
                new AdminCustomOrderController.AdminQuoteRequest(
                        new BigDecimal("1200.00"),
                        new BigDecimal("250.50"),
                        new BigDecimal("300.00"),
                        new BigDecimal("150.00"),
                        "Please reply to approve."),
                adminSession());

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verifyNoInteractions(mailSender);
        var details = assertInstanceOf(
                AdminCustomOrderController.CustomOrderDetail.class,
                adminController.getRequest("CM-000001").getBody());
        assertEquals("FOR_QUOTATION", details.status());
        assertEquals(new BigDecimal("1900.50"), details.quoteTotal());
        assertNotNull(details.quoteSentAt());
        var customerRequest = customerController.getOrder("CM-000001", customerSession);
        var tracked = assertInstanceOf(
                CustomerCustomOrderController.CustomerCustomOrderDetail.class,
                customerRequest.getBody());
        assertEquals("QUOTATION_SENT", tracked.status());
        assertEquals(new BigDecimal("1900.50"), tracked.quoteTotal());
        assertEquals(new BigDecimal("760.20"), tracked.quoteDownpayment());
        assertEquals("Please reply to approve.", tracked.quoteNotes());
    }

    @Test
    void customerCanSubmitProofForFortyPercentOfPublishedQuote() throws Exception {
        MockHttpSession customerSession = saveAuthenticatedCustomerRequest();
        adminController.publishOfficialQuote(
                "CM-000001",
                new AdminCustomOrderController.AdminQuoteRequest(
                        new BigDecimal("1200.00"),
                        new BigDecimal("250.50"),
                        new BigDecimal("300.00"),
                        new BigDecimal("150.00"),
                        ""),
                adminSession());
        MockMultipartFile receipt = new MockMultipartFile(
                "receipt", "transfer.png", "image/png", new byte[] {4, 5, 6});

        var response = controller.submitCheckout(
                "CM-000001", "GCash", "TX-123",
                true, receipt, customerSession);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        var body = assertInstanceOf(CustomOrderController.CheckoutResponse.class, response.getBody());
        assertEquals("PAYMENT_SUBMITTED", body.status());
        assertEquals(new BigDecimal("1900.50"), body.total());
        assertEquals(new BigDecimal("760.20"), body.paymentAmount());
        assertEquals(new BigDecimal("760.20"), jdbcTemplate.queryForObject(
                "SELECT payment_amount FROM custom_order_checkouts WHERE order_id = ?",
                BigDecimal.class,
                "CM-000001"));
        assertEquals("SUBMITTED", jdbcTemplate.queryForObject(
                "SELECT payment_status FROM custom_order_checkouts WHERE order_id = ?",
                String.class,
                "CM-000001"));

        var customerDetail = assertInstanceOf(
                CustomerCustomOrderController.CustomerCustomOrderDetail.class,
                customerController.getOrder("CM-000001", customerSession).getBody());
        assertEquals("PAYMENT_SUBMITTED", customerDetail.status());
        assertEquals(new BigDecimal("760.20"), customerDetail.paymentAmount());

        var proofResponse = adminController.getPaymentProof("CM-000001");
        assertEquals(HttpStatus.OK, proofResponse.getStatusCode());
        assertEquals(MediaType.IMAGE_PNG, proofResponse.getHeaders().getContentType());
        assertEquals(3, ((byte[]) proofResponse.getBody()).length);

        var duplicate = controller.submitCheckout(
                "CM-000001", "GCash", "TX-123",
                true, receipt, customerSession);
        assertEquals(HttpStatus.CONFLICT, duplicate.getStatusCode());
    }

    @Test
    void adminPaymentVerificationUpdatesCheckoutAndAuditAtomically() throws Exception {
        MockHttpSession customerSession = saveAuthenticatedCustomerRequest();
        adminController.publishOfficialQuote(
                "CM-000001",
                new AdminCustomOrderController.AdminQuoteRequest(
                        new BigDecimal("1200.00"),
                        new BigDecimal("250.50"),
                        new BigDecimal("300.00"),
                        new BigDecimal("150.00"),
                        ""),
                adminSession());
        var submitted = controller.submitCheckout(
                "CM-000001",
                "GCash",
                "TX-VERIFY",
                true,
                new MockMultipartFile("receipt", "transfer.png", "image/png", new byte[] {1, 2, 3}),
                customerSession);
        assertEquals(HttpStatus.CREATED, submitted.getStatusCode());

        var verified = adminController.verifyPayment(
                "CM-000001",
                new AdminCustomOrderController.PaymentVerificationRequest("VERIFIED", "Transfer checked in bank app."),
                adminSession());

        assertEquals(HttpStatus.OK, verified.getStatusCode());
        var result = assertInstanceOf(
                AdminCustomOrderController.PaymentVerificationResponse.class, verified.getBody());
        assertEquals("VERIFIED", result.paymentStatus());
        assertNotNull(result.verifiedAt());
        assertEquals("admin@example.com", jdbcTemplate.queryForObject(
                "SELECT payment_verified_by_email FROM custom_order_checkouts WHERE order_id = ?",
                String.class,
                "CM-000001"));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_events WHERE event_type = 'CUSTOM_ORDER_PAYMENT_VERIFIED'",
                Integer.class));

        var repeated = adminController.verifyPayment(
                "CM-000001",
                new AdminCustomOrderController.PaymentVerificationRequest("REJECTED", "Duplicate review"),
                adminSession());
        assertEquals(HttpStatus.CONFLICT, repeated.getStatusCode());
    }

    @Test
    void demoCheckoutCannotBeMarkedAsVerifiedPayment() throws Exception {
        controller.placeDemoOrder(
                new BigDecimal("32"),
                new BigDecimal("24"),
                "in",
                "Solid Wood",
                "",
                "",
                null,
                "Juan Dela Cruz",
                "123 Main Street",
                "09123456789",
                "GCash",
                "DEMO-REFERENCE",
                true,
                null,
                createCustomerSession());

        assertEquals("DEMO", jdbcTemplate.queryForObject(
                "SELECT submission_type FROM custom_order_checkouts WHERE order_id = ?",
                String.class,
                "CM-000001"));
        var response = adminController.verifyPayment(
                "CM-000001",
                new AdminCustomOrderController.PaymentVerificationRequest("VERIFIED", "Must stay demo."),
                adminSession());

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals("SUBMITTED", jdbcTemplate.queryForObject(
                "SELECT payment_status FROM custom_order_checkouts WHERE order_id = ?",
                String.class,
                "CM-000001"));
    }

    @Test
    void customerCannotSubmitPaymentWithoutPublishedQuoteOrReceipt() throws Exception {
        MockHttpSession customerSession = saveAuthenticatedCustomerRequest();
        MockMultipartFile receipt = new MockMultipartFile(
                "receipt", "transfer.png", "image/png", new byte[] {4, 5, 6});

        var unpublished = controller.submitCheckout(
                "CM-000001", "GCash", "", true, receipt, customerSession);
        assertEquals(HttpStatus.NOT_FOUND, unpublished.getStatusCode());

        adminController.publishOfficialQuote(
                "CM-000001",
                new AdminCustomOrderController.AdminQuoteRequest(
                        new BigDecimal("100.00"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, ""),
                adminSession());
        var missingReceipt = controller.submitCheckout(
                "CM-000001", "GCash", "", true, null, customerSession);
        assertEquals(HttpStatus.BAD_REQUEST, missingReceipt.getStatusCode());
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM custom_order_checkouts WHERE order_id = ?",
                Integer.class,
                "CM-000001"));
    }

    @Test
    void adminCanPublishQuoteWithoutCustomerEmail() throws Exception {
        controller.submitRequest(
                new BigDecimal("12"),
                new BigDecimal("12"),
                "in",
                "White",
                "",
                "",
                null);

        var response = adminController.publishOfficialQuote(
                "CM-000001",
                new AdminCustomOrderController.AdminQuoteRequest(
                        new BigDecimal("1200.00"),
                        new BigDecimal("250.50"),
                        new BigDecimal("300.00"),
                        new BigDecimal("150.00"),
                        ""),
                adminSession());

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verifyNoInteractions(mailSender);
        var details = assertInstanceOf(
                AdminCustomOrderController.CustomOrderDetail.class,
                adminController.getRequest("CM-000001").getBody());
        assertEquals("FOR_QUOTATION", details.status());
        assertEquals(new BigDecimal("1900.50"), details.quoteTotal());
        assertNotNull(details.quoteSentAt());
    }

    @Test
    void customOrderSchemaMigrationAddsAdminReviewColumnsToExistingDatabase() throws Exception {
        jdbcTemplate.execute("ALTER TABLE custom_orders DROP COLUMN status_updated_at");
        jdbcTemplate.execute("ALTER TABLE custom_orders DROP COLUMN admin_notes");
        jdbcTemplate.execute("ALTER TABLE custom_orders DROP COLUMN admin_status");
        jdbcTemplate.execute("ALTER TABLE custom_orders DROP COLUMN requester_name");
        jdbcTemplate.execute("ALTER TABLE custom_orders DROP COLUMN requester_email");
        jdbcTemplate.execute("ALTER TABLE custom_orders DROP COLUMN requester_phone");
        jdbcTemplate.execute("ALTER TABLE custom_orders DROP COLUMN requester_address");
        jdbcTemplate.execute("ALTER TABLE custom_orders DROP COLUMN requester_customer_id");
        jdbcTemplate.execute("ALTER TABLE custom_orders DROP COLUMN quote_base_amount");
        jdbcTemplate.execute("ALTER TABLE custom_orders DROP COLUMN quote_frame_amount");
        jdbcTemplate.execute("ALTER TABLE custom_orders DROP COLUMN quote_labor_amount");
        jdbcTemplate.execute("ALTER TABLE custom_orders DROP COLUMN quote_delivery_amount");
        jdbcTemplate.execute("ALTER TABLE custom_orders DROP COLUMN quote_total");
        jdbcTemplate.execute("ALTER TABLE custom_orders DROP COLUMN quote_sent_at");
        jdbcTemplate.execute("ALTER TABLE custom_order_checkouts DROP COLUMN payment_amount");
        jdbcTemplate.execute("ALTER TABLE custom_order_checkouts DROP COLUMN payment_status");

        new CustomOrderSchemaMigration(jdbcTemplate).run(null);

        controller.placeDemoOrder(
                new BigDecimal("32"),
                new BigDecimal("24"),
                "in",
                "Solid Wood",
                "",
                "",
                null,
                "Juan Dela Cruz",
                "123 Main Street",
                "09123456789",
                "GCash",
                "",
                true,
                null,
                createCustomerSession());
        assertEquals("NEW_REQUEST", jdbcTemplate.queryForObject(
                "SELECT admin_status FROM custom_orders WHERE order_id = ?",
                String.class,
                "CM-000001"));
        assertEquals("SUBMITTED", jdbcTemplate.queryForObject(
                "SELECT payment_status FROM custom_order_checkouts WHERE order_id = ?",
                String.class,
                "CM-000001"));
        assertEquals(new BigDecimal("3508.00"), jdbcTemplate.queryForObject(
                "SELECT payment_amount FROM custom_order_checkouts WHERE order_id = ?",
                BigDecimal.class,
                "CM-000001"));
        assertInstanceOf(
                AdminCustomOrderController.CustomOrderDetail.class,
                adminController.getRequest("CM-000001").getBody());
    }

    private MockHttpSession saveAuthenticatedCustomerRequest() throws Exception {
        MockHttpSession session = createCustomerSession();
        controller.submitRequest(
                new BigDecimal("12"),
                new BigDecimal("12"),
                "in",
                "White",
                "",
                "Thin gold frame",
                null,
                session);
        return session;
    }

    private MockHttpSession createCustomerSession() {
        return createCustomerSession("trisha@example.com");
    }

    private MockHttpSession createCustomerSession(String email) {
        jdbcTemplate.update("""
                INSERT INTO customer_accounts
                (full_name, email, address, phone, password_salt, password_hash, created_at)
                VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                """,
                "Trisha Dela Cruz",
                email,
                "45 Sample Street",
                "09171234567",
                "salt",
                "hash");
        Long customerId = jdbcTemplate.queryForObject(
                "SELECT id FROM customer_accounts WHERE email = ?",
                Long.class,
                email);
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(DemoAuthController.SESSION_ROLE, DemoAuthController.USER_ROLE);
        session.setAttribute(DemoAuthController.SESSION_CUSTOMER_ID, customerId);
        session.setAttribute(DemoAuthController.SESSION_EMAIL, email);
        return session;
    }

    private MockHttpSession adminSession() {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(DemoAuthController.SESSION_ROLE, DemoAuthController.ADMIN_ROLE);
        session.setAttribute(DemoAuthController.SESSION_EMAIL, "admin@example.com");
        return session;
    }
}
