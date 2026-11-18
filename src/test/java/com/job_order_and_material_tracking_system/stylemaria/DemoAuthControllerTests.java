package com.job_order_and_material_tracking_system.stylemaria;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.nio.charset.StandardCharsets;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;
import javax.imageio.ImageIO;
import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;

class DemoAuthControllerTests {

    private DemoAuthController controller;
    private JdbcTemplate jdbcTemplate;
    private JavaMailSender mailSender;
    private final DemoRoleInterceptor roleInterceptor = new DemoRoleInterceptor();

    @BeforeEach
    void setUp() {
        DataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:auth-" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1",
                "sa",
                "");
        new ResourceDatabasePopulator(new ClassPathResource("schema.sql")).execute(dataSource);
        jdbcTemplate = new JdbcTemplate(dataSource);
        mailSender = org.mockito.Mockito.mock(JavaMailSender.class);
        controller = new DemoAuthController(
                jdbcTemplate, mailSender, "admin@stylemaria.com", "A-test-only-admin-password",
                "https://stylemaria.example", "no-reply@stylemaria.example");
    }

    @Test
    void adminDemoAccountCanAccessAdminPagesButNotCustomerPages() throws Exception {
        MockHttpServletRequest request = requestFor("/login");
        assertEquals("redirect:/admin.html",
                controller.login("admin@stylemaria.com", "A-test-only-admin-password", request));
        assertEquals(DemoAuthController.ADMIN_ROLE,
                request.getSession().getAttribute(DemoAuthController.SESSION_ROLE));

        MockHttpServletResponse response = new MockHttpServletResponse();
        request.setRequestURI("/auto-bom.html");
        assertTrue(roleInterceptor.preHandle(request, response, new Object()));
        request.setRequestURI("/pos.html");
        assertTrue(roleInterceptor.preHandle(request, response, new Object()));

        request.setRequestURI("/payment.html");
        assertFalse(roleInterceptor.preHandle(request, response, new Object()));
        assertEquals("/admin.html", response.getRedirectedUrl());
        request.setRequestURI("/mirror-builder");
        assertFalse(roleInterceptor.preHandle(request, new MockHttpServletResponse(), new Object()));
        request.setRequestURI("/payment");
        assertFalse(roleInterceptor.preHandle(request, new MockHttpServletResponse(), new Object()));
    }

    @Test
    void adminLoginIsDisabledWhenCredentialsAreNotConfigured() {
        DemoAuthController unconfiguredController = new DemoAuthController(
                jdbcTemplate, mailSender, "", "", "https://stylemaria.example", "no-reply@stylemaria.example");

        assertEquals("redirect:/?error=invalid",
                unconfiguredController.login(
                        "admin@stylemaria.com", "A-test-only-admin-password", requestFor("/login")));
    }

    @Test
    void workshopSessionReturnsTheSignedInAdminOrStaffRoleOnly() {
        MockHttpSession adminSession = new MockHttpSession();
        adminSession.setAttribute(DemoAuthController.SESSION_ROLE, DemoAuthController.ADMIN_ROLE);
        adminSession.setAttribute(DemoAuthController.SESSION_EMAIL, "admin@stylemaria.com");
        var adminProfile = controller.workshopSession(adminSession);
        assertEquals(HttpStatus.OK, adminProfile.getStatusCode());
        assertEquals("ADMIN", adminProfile.getBody().get("role"));

        byte[] salt = PasswordSecurity.newSalt();
        jdbcTemplate.update("""
                INSERT INTO staff_accounts
                    (email, display_name, password_salt, password_hash, active, created_at, updated_at, created_by_email)
                VALUES (?, ?, ?, ?, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, ?)
                """,
                "staff@example.com",
                "Test Staff",
                PasswordSecurity.encodeSalt(salt),
                PasswordSecurity.hashPassword("A-test-staff-password", salt),
                "admin@stylemaria.com");
        MockHttpSession staffSession = new MockHttpSession();
        staffSession.setAttribute(DemoAuthController.SESSION_ROLE, DemoAuthController.STAFF_ROLE);
        staffSession.setAttribute(DemoAuthController.SESSION_EMAIL, "staff@example.com");
        var staffProfile = controller.workshopSession(staffSession);
        assertEquals(HttpStatus.OK, staffProfile.getStatusCode());
        assertEquals("STAFF", staffProfile.getBody().get("role"));
        assertEquals("Test Staff", staffProfile.getBody().get("displayName"));

        assertEquals(HttpStatus.UNAUTHORIZED, controller.workshopSession(null).getStatusCode());
        MockHttpSession customerSession = new MockHttpSession();
        customerSession.setAttribute(DemoAuthController.SESSION_ROLE, DemoAuthController.USER_ROLE);
        customerSession.setAttribute(DemoAuthController.SESSION_EMAIL, "customer@example.com");
        assertEquals(HttpStatus.UNAUTHORIZED, controller.workshopSession(customerSession).getStatusCode());
    }

    @Test
    void invitedStaffUsesPersistedCredentialsAndIsLimitedToOperationalPagesAndApis() throws Exception {
        byte[] salt = PasswordSecurity.newSalt();
        jdbcTemplate.update("""
                INSERT INTO staff_accounts
                    (email, display_name, password_salt, password_hash, active, created_at, updated_at, created_by_email)
                VALUES (?, ?, ?, ?, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, ?)
                """,
                "staff@example.com",
                "Shop Floor",
                PasswordSecurity.encodeSalt(salt),
                PasswordSecurity.hashPassword("A-long-staff-passphrase", salt),
                "admin@stylemaria.com");

        MockHttpServletRequest request = requestFor("/login");
        assertEquals("redirect:/pos.html",
                controller.login("staff@example.com", "A-long-staff-passphrase", request));
        MockHttpSession session = (MockHttpSession) request.getSession(false);
        assertEquals(DemoAuthController.STAFF_ROLE,
                session.getAttribute(DemoAuthController.SESSION_ROLE));
        assertEquals("staff@example.com",
                session.getAttribute(DemoAuthController.SESSION_EMAIL));

        MockHttpServletResponse response = new MockHttpServletResponse();
        request.setRequestURI("/inventory.html");
        assertTrue(roleInterceptor.preHandle(request, response, new Object()));
        request.setRequestURI("/wastage.html");
        assertTrue(roleInterceptor.preHandle(request, response, new Object()));
        request.setRequestURI("/pos.html");
        assertTrue(roleInterceptor.preHandle(request, response, new Object()));
        request.setRequestURI("/api/v1/session");
        assertTrue(roleInterceptor.preHandle(request, response, new Object()));
        request.setRequestURI("/reports.html");
        assertFalse(roleInterceptor.preHandle(request, response, new Object()));
        assertEquals("/?error=unauthorized", response.getRedirectedUrl());

        response = new MockHttpServletResponse();
        request.setRequestURI("/api/v1/admin/staff");
        assertFalse(roleInterceptor.preHandle(request, response, new Object()));
        assertEquals(403, response.getStatus());
        assertTrue(response.getContentAsString().contains("\"error\":\"forbidden\""));

        request = requestFor("/api/v1/inventory/materials");
        response = new MockHttpServletResponse();
        assertFalse(roleInterceptor.preHandle(request, response, new Object()));
        assertEquals(401, response.getStatus());
        assertTrue(response.getContentAsString().contains("\"error\":\"unauthenticated\""));
    }

    @Test
    void registeredCustomerCanAccessPaymentButNotAdminPages() throws Exception {
        registerCustomer();
        MockHttpServletRequest request = requestFor("/login");
        assertEquals("redirect:/home.html",
                controller.login("customer@example.com", "A-long-safe-passphrase", request));
        assertEquals("redirect:/", controller.legacyOrderPage());
        assertEquals(DemoAuthController.USER_ROLE,
                request.getSession().getAttribute(DemoAuthController.SESSION_ROLE));

        MockHttpServletResponse response = new MockHttpServletResponse();
        request.setRequestURI("/payment.html");
        assertTrue(roleInterceptor.preHandle(request, response, new Object()));
        request.setRequestURI("/payment");
        assertTrue(roleInterceptor.preHandle(request, response, new Object()));
        request.setRequestURI("/mirror-builder");
        assertTrue(roleInterceptor.preHandle(request, response, new Object()));

        request.setRequestURI("/pos.html");
        assertFalse(roleInterceptor.preHandle(request, response, new Object()));
        assertEquals("/", response.getRedirectedUrl());

        response = new MockHttpServletResponse();
        request.setRequestURI("/reports.html");
        assertFalse(roleInterceptor.preHandle(request, response, new Object()));
        assertEquals("/", response.getRedirectedUrl());
    }

    @Test
    void signingInAsCustomerReplacesAnExistingAdminSession() throws Exception {
        registerCustomer();
        MockHttpServletRequest request = requestFor("/login");
        controller.login("admin@stylemaria.com", "A-test-only-admin-password", request);
        MockHttpSession adminSession = (MockHttpSession) request.getSession(false);

        assertEquals("redirect:/home.html",
                controller.login("customer@example.com", "A-long-safe-passphrase", request));

        assertTrue(adminSession.isInvalid());
        assertEquals(DemoAuthController.USER_ROLE,
                request.getSession(false).getAttribute(DemoAuthController.SESSION_ROLE));
        assertEquals("Customer Name",
                request.getSession(false).getAttribute(DemoAuthController.SESSION_NAME));
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.setRequestURI("/customize-order.html");
        assertTrue(roleInterceptor.preHandle(request, response, new Object()));
    }

    @Test
    void customerProfileReturnsTheRegisteredNameAfterLogin() {
        registerCustomer();
        MockHttpServletRequest request = requestFor("/login");
        controller.login("customer@example.com", "A-long-safe-passphrase", request);

        var profile = controller.customerProfile(request.getSession(false));

        assertEquals(HttpStatus.OK, profile.getStatusCode());
        assertEquals("Customer Name", profile.getBody().get("fullName"));
    }

    @Test
    void customerCanUpdateNameEmailAndAddressWithCurrentPassword() {
        registerCustomer();
        MockHttpServletRequest request = requestFor("/login");
        controller.login("customer@example.com", "A-long-safe-passphrase", request);
        var result = controller.updateCustomerProfile(
                "Updated Customer", "updated@example.com", "123 Workshop Lane", "09171234567",
                "A-long-safe-passphrase", "", "", null, request.getSession(false));
        var profile = controller.customerProfile(request.getSession(false));

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertEquals("Updated Customer", profile.getBody().get("fullName"));
        assertEquals("updated@example.com", profile.getBody().get("email"));
        assertEquals("123 Workshop Lane", profile.getBody().get("address"));
        assertEquals("09171234567", profile.getBody().get("phone"));
        assertEquals("Updated Customer",
                request.getSession(false).getAttribute(DemoAuthController.SESSION_NAME));
        MockHttpServletRequest updatedLogin = requestFor("/login");
        assertEquals("redirect:/home.html",
                controller.login("updated@example.com", "A-long-safe-passphrase", updatedLogin));
    }

    @Test
    void customerCanChangePasswordAfterVerifyingCurrentPassword() {
        registerCustomer();
        MockHttpServletRequest request = requestFor("/login");
        controller.login("customer@example.com", "A-long-safe-passphrase", request);
        assertEquals(HttpStatus.OK,
                controller.updateCustomerProfile(
                        "Customer Name", "customer@example.com", "", "",
                        "A-long-safe-passphrase", "A-new-safe-password", "A-new-safe-password",
                        null, request.getSession(false)).getStatusCode());
        MockHttpServletRequest oldLogin = requestFor("/login");
        assertEquals("redirect:/?error=invalid",
                controller.login("customer@example.com", "A-long-safe-passphrase", oldLogin));
        MockHttpServletRequest newLogin = requestFor("/login");
        assertEquals("redirect:/home.html",
                controller.login("customer@example.com", "A-new-safe-password", newLogin));
    }

    @Test
    void customerCanUploadAndRetrievePngProfilePhoto() throws IOException {
        registerCustomer();
        MockHttpServletRequest request = requestFor("/login");
        controller.login("customer@example.com", "A-long-safe-passphrase", request);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB), "png", output);
        byte[] png = output.toByteArray();
        MockMultipartFile photo = new MockMultipartFile(
                "profilePhoto", "profile.png", "image/png", png);

        var update = controller.updateCustomerProfile(
                "Customer Name", "customer@example.com", "", "",
                "A-long-safe-passphrase", "", "", photo, request.getSession(false));
        var savedPhoto = controller.customerProfilePhoto(request.getSession(false));

        assertEquals(HttpStatus.OK, update.getStatusCode());
        assertEquals(HttpStatus.OK, savedPhoto.getStatusCode());
        assertEquals("image/png", savedPhoto.getHeaders().getFirst("Content-Type"));
        assertArrayEquals(png, savedPhoto.getBody());
    }

    @Test
    void rejectsNonImageProfilePhoto() {
        registerCustomer();
        MockHttpServletRequest request = requestFor("/login");
        controller.login("customer@example.com", "A-long-safe-passphrase", request);
        MockMultipartFile photo = new MockMultipartFile(
                "profilePhoto", "profile.png", "image/png", "not an image".getBytes());

        var update = controller.updateCustomerProfile(
                "Customer Name", "customer@example.com", "", "",
                "A-long-safe-passphrase", "", "", photo, request.getSession(false));

        assertEquals(HttpStatus.BAD_REQUEST, update.getStatusCode());
        assertEquals(HttpStatus.NOT_FOUND,
                controller.customerProfilePhoto(request.getSession(false)).getStatusCode());
    }

    @Test
    void rejectsProfileChangesWithWrongPasswordOrDuplicateEmail() {
        registerCustomer();
        controller.signup("Other Customer", "other@example.com",
                "Another-safe-password", "Another-safe-password");
        MockHttpServletRequest request = requestFor("/login");
        controller.login("customer@example.com", "A-long-safe-passphrase", request);

        assertEquals(HttpStatus.FORBIDDEN,
                controller.updateCustomerProfile(
                        "Changed Name", "customer@example.com", "New address", "",
                        "wrong-password", "", "", null, request.getSession(false)).getStatusCode());

        assertEquals(HttpStatus.CONFLICT,
                controller.updateCustomerProfile(
                        "Changed Name", "other@example.com", "New address", "",
                        "A-long-safe-passphrase", "", "", null, request.getSession(false)).getStatusCode());
        assertEquals("Customer Name",
                controller.customerProfile(request.getSession(false)).getBody().get("fullName"));
    }

    @Test
    void addsProfileColumnsToExistingCustomerSchema() throws Exception {
        jdbcTemplate.execute("ALTER TABLE customer_accounts DROP COLUMN address");
        jdbcTemplate.execute("ALTER TABLE customer_accounts DROP COLUMN phone");
        jdbcTemplate.execute("ALTER TABLE customer_accounts DROP COLUMN profile_photo_content_type");
        jdbcTemplate.execute("ALTER TABLE customer_accounts DROP COLUMN profile_photo");
        jdbcTemplate.execute("ALTER TABLE customer_accounts DROP COLUMN email_verified");
        new CustomerAccountSchemaMigration(jdbcTemplate).run(new DefaultApplicationArguments());

        Integer profileColumnCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM INFORMATION_SCHEMA.COLUMNS
                WHERE UPPER(TABLE_NAME) = 'CUSTOMER_ACCOUNTS'
                  AND UPPER(COLUMN_NAME) IN (
                    'ADDRESS', 'PHONE', 'PROFILE_PHOTO', 'PROFILE_PHOTO_CONTENT_TYPE', 'EMAIL_VERIFIED')
                """, Integer.class);
        assertEquals(5, profileColumnCount);
    }

    @Test
    void invalidCredentialsAndUnauthenticatedAdminAccessAreRejected() throws Exception {
        MockHttpServletRequest request = requestFor("/login");
        assertEquals("redirect:/?error=invalid",
                controller.login("customer@example.com", "wrong", request));
        assertNull(request.getSession(false));

        MockHttpServletResponse response = new MockHttpServletResponse();
        request.setRequestURI("/admin.html");
        assertFalse(roleInterceptor.preHandle(request, response, new Object()));
        assertEquals("/?error=unauthorized", response.getRedirectedUrl());
    }

    @Test
    void logoutInvalidatesTheDemoSession() {
        registerCustomer();
        MockHttpServletRequest request = requestFor("/login");
        controller.login("customer@example.com", "A-long-safe-passphrase", request);
        MockHttpSession session = (MockHttpSession) request.getSession(false);

        assertEquals("redirect:/?logout", controller.logout(session));
        assertTrue(session.isInvalid());
    }

    @Test
    void rejectsWeakAndDuplicateCustomerSignups() {
        assertEquals("redirect:/?signupError=invalid",
                controller.signup("Customer Name", "customer@example.com", "short", "short"));
        assertEquals("redirect:/?verificationSent",
                controller.signup("Customer Name", "customer@example.com",
                        "A-long-safe-passphrase", "A-long-safe-passphrase"));
        assertEquals("redirect:/?signupError=exists",
                controller.signup("Another Name", "CUSTOMER@example.com",
                        "Another-safe-passphrase", "Another-safe-passphrase"));
        assertEquals("redirect:/?signupError=invalid",
                controller.signup("Another Name", "another@example.com",
                        "A-long-safe-passphrase", "A-different-passphrase"));
    }

    @Test
    void customerSignupCannotClaimConfiguredAdminOrStaffEmailAddresses() {
        byte[] salt = PasswordSecurity.newSalt();
        jdbcTemplate.update("""
                INSERT INTO staff_accounts
                    (email, display_name, password_salt, password_hash, active, created_at, updated_at, created_by_email)
                VALUES (?, ?, ?, ?, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, ?)
                """, "staff@example.com", "Staff",
                PasswordSecurity.encodeSalt(salt),
                PasswordSecurity.hashPassword("A-test-staff-password", salt),
                "admin@stylemaria.com");

        assertEquals("redirect:/?signupError=exists",
                controller.signup("Claim Admin", "admin@stylemaria.com",
                        "A-long-safe-passphrase", "A-long-safe-passphrase"));
        assertEquals("redirect:/?signupError=exists",
                controller.signup("Claim Staff", "staff@example.com",
                        "A-long-safe-passphrase", "A-long-safe-passphrase"));
        verifyNoInteractions(mailSender);
    }

    @Test
    void customerRegistrationAcceptsNonGmailAndRequiresSingleUseEmailVerification()
            throws NoSuchAlgorithmException {
        assertEquals("redirect:/?verificationSent",
                controller.signup("Customer Name", "customer@outlook.com",
                        "A-long-safe-passphrase", "A-long-safe-passphrase"));

        ArgumentCaptor<SimpleMailMessage> messageCaptor =
                ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(messageCaptor.capture());
        SimpleMailMessage message = messageCaptor.getValue();
        assertEquals("customer@outlook.com", message.getTo()[0]);
        assertTrue(message.getText().contains("https://stylemaria.example/verify-email.html?token="));
        String token = verificationTokenFrom(message);
        String storedHash = jdbcTemplate.queryForObject(
                "SELECT token_hash FROM customer_email_verification_tokens", String.class);
        assertEquals(hashToken(token), storedHash);
        assertFalse(storedHash.equals(token));
        assertFalse(jdbcTemplate.queryForObject(
                "SELECT email_verified FROM customer_accounts WHERE email = ?",
                Boolean.class, "customer@outlook.com"));

        MockHttpServletRequest unverifiedLogin = requestFor("/login");
        assertEquals("redirect:/?error=unverified",
                controller.login("customer@outlook.com", "A-long-safe-passphrase", unverifiedLogin));
        assertNull(unverifiedLogin.getSession(false));

        assertEquals("redirect:/?verified", controller.verifyEmail(token));
        assertEquals("redirect:/?verificationError=invalid", controller.verifyEmail(token));
        MockHttpServletRequest verifiedLogin = requestFor("/login");
        assertEquals("redirect:/home.html",
                controller.login("customer@outlook.com", "A-long-safe-passphrase", verifiedLogin));
    }

    @Test
    void resendVerificationRotatesOldTokensWithoutRevealingUnknownAccounts() throws Exception {
        controller.signup("Customer Name", "customer@example.com",
                "A-long-safe-passphrase", "A-long-safe-passphrase");
        ArgumentCaptor<SimpleMailMessage> firstMessage =
                ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(firstMessage.capture());
        String firstToken = verificationTokenFrom(firstMessage.getValue());
        org.mockito.Mockito.clearInvocations(mailSender);

        assertEquals("redirect:/verify-email.html?sent",
                controller.resendVerification("customer@example.com"));
        ArgumentCaptor<SimpleMailMessage> resentMessage =
                ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(resentMessage.capture());
        String resentToken = verificationTokenFrom(resentMessage.getValue());
        assertFalse(firstToken.equals(resentToken));
        assertEquals("redirect:/?verificationError=invalid", controller.verifyEmail(firstToken));
        assertEquals("redirect:/?verified", controller.verifyEmail(resentToken));

        org.mockito.Mockito.clearInvocations(mailSender);
        assertEquals("redirect:/verify-email.html?sent",
                controller.resendVerification("unknown@example.com"));
        verifyNoInteractions(mailSender);
    }

    @Test
    void verificationDeliveryFailureDoesNotLeaveAnUsableUnverifiedAccount() {
        doThrow(new MailSendException("SMTP unavailable"))
                .when(mailSender).send(any(SimpleMailMessage.class));

        assertEquals("redirect:/?signupError=delivery",
                controller.signup("Customer Name", "customer@example.com",
                        "A-long-safe-passphrase", "A-long-safe-passphrase"));
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM customer_accounts WHERE email = ?",
                Integer.class, "customer@example.com"));
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM customer_email_verification_tokens",
                Integer.class));
    }

    @Test
    void activeStaffCanRecoverPasswordUsingTheSharedResetFlow() throws Exception {
        byte[] salt = PasswordSecurity.newSalt();
        jdbcTemplate.update("""
                INSERT INTO staff_accounts
                    (email, display_name, password_salt, password_hash, active, created_at, updated_at, created_by_email)
                VALUES (?, ?, ?, ?, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, ?)
                """, "staff@example.com", "Workshop Staff",
                PasswordSecurity.encodeSalt(salt),
                PasswordSecurity.hashPassword("Old-staff-password", salt),
                "admin@stylemaria.com");

        assertEquals("redirect:/forgot-password.html?requested",
                controller.forgotPassword("staff@example.com"));
        ArgumentCaptor<SimpleMailMessage> messageCaptor =
                ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(messageCaptor.capture());
        String token = resetTokenFrom(messageCaptor.getValue());

        assertEquals("redirect:/?passwordReset",
                controller.resetPassword(token, "New-staff-password", "New-staff-password"));
        MockHttpServletRequest oldLogin = requestFor("/login");
        assertEquals("redirect:/?error=invalid",
                controller.login("staff@example.com", "Old-staff-password", oldLogin));
        MockHttpServletRequest newLogin = requestFor("/login");
        assertEquals("redirect:/pos.html",
                controller.login("staff@example.com", "New-staff-password", newLogin));
        assertEquals(1, jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM audit_events
                WHERE event_type = 'STAFF_PASSWORD_RESET' AND actor_email = ?
                """, Integer.class, "staff@example.com"));
    }

    @Test
    void forgotPasswordUsesSameResponseForUnknownEmails() {
        assertEquals("redirect:/forgot-password.html?requested",
                controller.forgotPassword("unknown@example.com"));
        verifyNoInteractions(mailSender);
    }

    @Test
    void forgotPasswordEmailsResetLinkAndStoresOnlyTokenHash() throws Exception {
        registerCustomer();

        assertEquals("redirect:/forgot-password.html?requested",
                controller.forgotPassword(" CUSTOMER@example.com "));

        ArgumentCaptor<SimpleMailMessage> messageCaptor =
                ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(messageCaptor.capture());
        SimpleMailMessage message = messageCaptor.getValue();
        assertEquals("customer@example.com", message.getTo()[0]);
        assertEquals("no-reply@stylemaria.example", message.getFrom());
        assertTrue(message.getText().contains(
                "https://stylemaria.example/reset-password.html?token="));

        String token = resetTokenFrom(message);
        String storedHash = jdbcTemplate.queryForObject(
                "SELECT token_hash FROM password_reset_tokens", String.class);
        assertEquals(64, storedHash.length());
        assertEquals(hashToken(token), storedHash);
        assertFalse(storedHash.equals(token));
    }

    @Test
    void resetPasswordChangesCredentialsAndConsumesTokenOnce() throws Exception {
        registerCustomer();
        String token = requestResetToken();

        assertEquals("redirect:/?passwordReset",
                controller.resetPassword(token, "A-new-safe-passphrase", "A-new-safe-passphrase"));
        assertEquals("redirect:/reset-password.html?error=expired",
                controller.resetPassword(token, "Another-new-passphrase", "Another-new-passphrase"));

        MockHttpServletRequest oldPasswordRequest = requestFor("/login");
        assertEquals("redirect:/?error=invalid",
                controller.login("customer@example.com", "A-long-safe-passphrase", oldPasswordRequest));
        assertNull(oldPasswordRequest.getSession(false));

        MockHttpServletRequest newPasswordRequest = requestFor("/login");
        assertEquals("redirect:/home.html",
                controller.login("customer@example.com", "A-new-safe-passphrase", newPasswordRequest));
    }

    @Test
    void rejectsExpiredResetToken() throws Exception {
        registerCustomer();
        String token = requestResetToken();
        jdbcTemplate.update("UPDATE password_reset_tokens SET expires_at = ? WHERE token_hash = ?",
                Timestamp.from(Instant.now().minusSeconds(1)), hashToken(token));

        assertEquals("redirect:/reset-password.html?error=expired",
                controller.resetPassword(token, "A-new-safe-passphrase", "A-new-safe-passphrase"));
    }

    @Test
    void rejectsWeakAndMismatchedResetPasswordsWithoutConsumingToken() throws Exception {
        registerCustomer();
        String token = requestResetToken();

        assertEquals("redirect:/reset-password.html?error=invalid",
                controller.resetPassword(token, "too-short", "too-short"));
        assertEquals("redirect:/reset-password.html?error=invalid",
                controller.resetPassword(token, "A-new-safe-passphrase", "A-different-passphrase"));
        assertEquals("redirect:/?passwordReset",
                controller.resetPassword(token, "A-new-safe-passphrase", "A-new-safe-passphrase"));
    }

    @Test
    void handlesEmailDeliveryFailureAndRemovesPendingToken() {
        registerCustomer();
        doThrow(new MailSendException("SMTP unavailable"))
                .when(mailSender).send(any(SimpleMailMessage.class));

        assertEquals("redirect:/forgot-password.html?deliveryError",
                controller.forgotPassword("customer@example.com"));
        Integer pendingTokens = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM password_reset_tokens WHERE used_at IS NULL", Integer.class);
        assertEquals(0, pendingTokens);
    }

    private void registerCustomer() {
        assertEquals("redirect:/?verificationSent",
                controller.signup("Customer Name", "customer@example.com",
                        "A-long-safe-passphrase", "A-long-safe-passphrase"));
        ArgumentCaptor<SimpleMailMessage> messageCaptor =
                ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(messageCaptor.capture());
        assertEquals("redirect:/?verified",
                controller.verifyEmail(verificationTokenFrom(messageCaptor.getValue())));
        org.mockito.Mockito.clearInvocations(mailSender);
    }

    private String verificationTokenFrom(SimpleMailMessage message) {
        String text = message.getText();
        String marker = "verify-email.html?token=";
        int tokenStart = text.indexOf(marker);
        assertTrue(tokenStart >= 0, "Verification email should contain its verification URL");
        tokenStart += marker.length();
        int tokenEnd = text.indexOf('\n', tokenStart);
        return text.substring(tokenStart, tokenEnd < 0 ? text.length() : tokenEnd).trim();
    }

    private String requestResetToken() throws Exception {
        controller.forgotPassword("customer@example.com");
        ArgumentCaptor<SimpleMailMessage> messageCaptor =
                ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(messageCaptor.capture());
        return resetTokenFrom(messageCaptor.getValue());
    }

    private String resetTokenFrom(SimpleMailMessage message) {
        String text = message.getText();
        String marker = "reset-password.html?token=";
        int tokenStart = text.indexOf(marker);
        assertTrue(tokenStart >= 0, "Reset email should contain the reset URL");
        tokenStart += marker.length();
        int tokenEnd = text.indexOf('\n', tokenStart);
        return text.substring(tokenStart, tokenEnd < 0 ? text.length() : tokenEnd).trim();
    }

    private String hashToken(String token) throws NoSuchAlgorithmException {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(token.getBytes(StandardCharsets.UTF_8)));
    }

    private MockHttpServletRequest requestFor(String path) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI(path);
        return request;
    }
}
