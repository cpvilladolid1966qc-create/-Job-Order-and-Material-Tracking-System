package com.job_order_and_material_tracking_system.stylemaria;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.imageio.ImageIO;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

// Application entry point
@SpringBootApplication
public class StylemariaApplication {

    public static void main(String[] args) {
        SpringApplication.run(StylemariaApplication.class, args);
    }
}

// Demo authentication controller
@Controller
class DemoAuthController {

    static final String SESSION_ROLE = "demoRole";
    static final String SESSION_NAME = "customerName";
    static final String SESSION_CUSTOMER_ID = "customerId";
    static final String SESSION_EMAIL = "actorEmail";
    static final String ADMIN_ROLE = "ADMIN";
    static final String STAFF_ROLE = "STAFF";
    static final String USER_ROLE = "USER";
    private static final String UNVERIFIED_ROLE = "UNVERIFIED";

    private static final int PASSWORD_ITERATIONS = 210_000;
    private static final int PASSWORD_KEY_LENGTH = 256;
    private static final int PASSWORD_SALT_BYTES = 16;
    private static final int RESET_TOKEN_BYTES = 32;
    private static final long MAX_PROFILE_PHOTO_BYTES = 2 * 1024 * 1024;
    private static final long RESET_TOKEN_LIFETIME_MINUTES = 30;
    private static final long EMAIL_VERIFICATION_LIFETIME_HOURS = 24;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final Logger LOGGER = LoggerFactory.getLogger(DemoAuthController.class);

    @GetMapping("/login")
    public String loginPage() {
        return "redirect:/";
    }

    private final JdbcTemplate jdbcTemplate;
    private final JavaMailSender mailSender;
    private final String adminEmail;
    private final String adminPassword;
    private final String publicUrl;
    private final String mailFrom;

    DemoAuthController(
            JdbcTemplate jdbcTemplate,
            JavaMailSender mailSender,
            @Value("${app.admin.email:}") String adminEmail,
            @Value("${app.admin.password:}") String adminPassword,
            @Value("${app.public-url:http://localhost:8080}") String publicUrl,
            @Value("${app.mail.from:no-reply@stylemaria.com}") String mailFrom) {
        this.jdbcTemplate = jdbcTemplate;
        this.mailSender = mailSender;
        this.adminEmail = adminEmail == null ? "" : adminEmail.trim();
        this.adminPassword = adminPassword == null ? "" : adminPassword;
        this.publicUrl = publicUrl.replaceAll("/+$", "");
        this.mailFrom = mailFrom;
    }

    @GetMapping("/order.html")
    public String legacyOrderPage() {
        return "redirect:/";
    }

    @PostMapping("/login")
    public String login(
            @RequestParam String username,
            @RequestParam String password,
            HttpServletRequest request) {
        String role = authenticate(username, password);
        if (UNVERIFIED_ROLE.equals(role)) {
            return "redirect:/?error=unverified";
        }
        if (role == null) {
            return "redirect:/?error=invalid";
        }

        HttpSession existingSession = request.getSession(false);
        if (existingSession != null) {
            existingSession.invalidate();
        }
        HttpSession session = request.getSession(true);
        session.setAttribute(SESSION_ROLE, role);
        if (USER_ROLE.equals(role)) {
            String normalizedEmail = username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
            try {
                CustomerProfile profile = jdbcTemplate.queryForObject("""
                        SELECT id, full_name, email, address, phone
                        FROM customer_accounts
                        WHERE email = ?
                        """, (result, rowNumber) -> new CustomerProfile(
                        result.getLong("id"),
                        result.getString("full_name"),
                        result.getString("email"),
                        result.getString("address"),
                        result.getString("phone")), normalizedEmail);
                setCustomerSession(session, profile);
            } catch (EmptyResultDataAccessException exception) {
                session.removeAttribute(SESSION_ROLE);
                session.removeAttribute(SESSION_CUSTOMER_ID);
                session.removeAttribute(SESSION_NAME);
                return "redirect:/?error=invalid";
            }
        } else if (STAFF_ROLE.equals(role)) {
            String normalizedEmail = username.trim().toLowerCase(Locale.ROOT);
            List<StaffSessionIdentity> staff = jdbcTemplate.query("""
                    SELECT email, display_name
                    FROM staff_accounts
                    WHERE email = ? AND active = TRUE
                    """, (result, rowNumber) -> new StaffSessionIdentity(
                    result.getString("email"), result.getString("display_name")), normalizedEmail);
            if (staff.isEmpty()) {
                session.invalidate();
                return "redirect:/?error=invalid";
            }
            session.setAttribute(SESSION_EMAIL, staff.getFirst().email());
            session.setAttribute(SESSION_NAME, staff.getFirst().displayName());
        } else if (ADMIN_ROLE.equals(role)) {
            session.setAttribute(SESSION_EMAIL, username.trim().toLowerCase(Locale.ROOT));
        }
        if (ADMIN_ROLE.equals(role)) {
            return "redirect:/admin.html";
        }
        return STAFF_ROLE.equals(role) ? "redirect:/pos.html" : "redirect:/home.html";
    }

    @GetMapping("/api/v1/session")
    public ResponseEntity<Map<String, String>> workshopSession(HttpSession session) {
        Object roleValue = session == null ? null : session.getAttribute(SESSION_ROLE);
        Object emailValue = session == null ? null : session.getAttribute(SESSION_EMAIL);
        if (!(roleValue instanceof String role)
                || !(emailValue instanceof String email)
                || email.isBlank()
                || !(ADMIN_ROLE.equals(role) || STAFF_ROLE.equals(role))) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        String displayName = "Admin";
        if (STAFF_ROLE.equals(role)) {
            List<String> staffNames = jdbcTemplate.query("""
                    SELECT display_name
                    FROM staff_accounts
                    WHERE email = ? AND active = TRUE
                    """, (result, row) -> result.getString("display_name"), email);
            if (staffNames.isEmpty()) {
                session.invalidate();
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }
            displayName = staffNames.getFirst();
        }
        return ResponseEntity.ok(Map.of(
                "role", role,
                "displayName", displayName,
                "email", email));
    }

    @PatchMapping("/api/v1/session/profile")
    @Transactional
    public ResponseEntity<?> updateWorkshopProfile(
            @RequestBody WorkshopProfileUpdate input,
            HttpSession session) {
        AuthenticatedActor actor = AuthenticatedActor.from(session);
        if (!STAFF_ROLE.equals(actor.role())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Only staff display names can be changed here."));
        }
        String displayName = input == null || input.displayName() == null
                ? "" : input.displayName().trim();
        if (displayName.isBlank() || displayName.length() > 100) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Display name must contain 1 to 100 characters."));
        }
        List<String> previousNames = jdbcTemplate.query("""
                SELECT display_name
                FROM staff_accounts
                WHERE email = ? AND active = TRUE
                FOR UPDATE
                """, (result, row) -> result.getString("display_name"), actor.email());
        if (previousNames.isEmpty()) {
            session.invalidate();
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        jdbcTemplate.update("""
                UPDATE staff_accounts SET display_name = ?, updated_at = ?
                WHERE email = ? AND active = TRUE
                """, displayName, Timestamp.from(Instant.now()), actor.email());
        session.setAttribute(SESSION_NAME, displayName);
        AuditEvents.append(jdbcTemplate, actor, "STAFF_PROFILE_UPDATED", "STAFF_ACCOUNT",
                actor.email(), Map.of("before", previousNames.getFirst(), "after", displayName));
        return ResponseEntity.ok(Map.of(
                "role", STAFF_ROLE,
                "displayName", displayName,
                "email", actor.email()));
    }

    public record WorkshopProfileUpdate(String displayName) {
    }

    @GetMapping("/api/v1/customer/profile")
    public ResponseEntity<Map<String, String>> customerProfile(HttpSession session) {
        Long customerId = customerId(session);
        if (customerId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        List<CustomerProfile> profiles = jdbcTemplate.query("""
                SELECT id, full_name, email, address, phone
                FROM customer_accounts
                WHERE id = ?
                """, (result, rowNumber) -> new CustomerProfile(
                result.getLong("id"),
                result.getString("full_name"),
                result.getString("email"),
                result.getString("address"),
                result.getString("phone")), customerId);
        if (profiles.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        CustomerProfile profile = profiles.getFirst();
        return ResponseEntity.ok(profileDetails(profile));
    }

    @GetMapping("/api/v1/customer/profile/photo")
    public ResponseEntity<byte[]> customerProfilePhoto(HttpSession session) {
        Long customerId = customerId(session);
        if (customerId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        List<ProfilePhoto> photos = jdbcTemplate.query("""
                SELECT profile_photo, profile_photo_content_type
                FROM customer_accounts
                WHERE id = ? AND profile_photo IS NOT NULL
                """, (result, rowNumber) -> new ProfilePhoto(
                result.getBytes("profile_photo"),
                result.getString("profile_photo_content_type")), customerId);
        if (photos.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        ProfilePhoto photo = photos.getFirst();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, photo.contentType())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(photo.bytes());
    }

    @PutMapping("/api/v1/customer/profile")
    @Transactional
    public ResponseEntity<?> updateCustomerProfile(
            @RequestParam String fullName,
            @RequestParam String email,
            @RequestParam(required = false, defaultValue = "") String address,
            @RequestParam(required = false, defaultValue = "") String phone,
            @RequestParam String currentPassword,
            @RequestParam(required = false, defaultValue = "") String newPassword,
            @RequestParam(required = false, defaultValue = "") String confirmPassword,
            @RequestParam(required = false) MultipartFile profilePhoto,
            HttpSession session) {
        Long customerId = customerId(session);
        if (customerId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        String normalizedFullName = fullName == null ? "" : fullName.trim();
        String normalizedEmail = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        String normalizedAddress = address == null ? "" : address.trim();
        String normalizedPhone = phone == null ? "" : phone.trim();
        if (normalizedFullName.isBlank() || normalizedFullName.length() > 100
                || !normalizedEmail.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$") || normalizedEmail.length() > 254
                || normalizedAddress.length() > 500 || normalizedPhone.length() > 40) {
            return ResponseEntity.badRequest().body(Map.of("error",
                    "Enter a valid name and email. Address must be 500 characters or fewer and phone 40 or fewer."));
        }

        List<CustomerCredential> credentials = jdbcTemplate.query("""
                SELECT password_salt, password_hash, email_verified
                FROM customer_accounts
                WHERE id = ?
                """, (result, rowNumber) -> new CustomerCredential(
                result.getString("password_salt"),
                result.getString("password_hash"),
                result.getBoolean("email_verified")), customerId);
        if (credentials.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        CustomerCredential credential = credentials.getFirst();
        if (currentPassword == null || !passwordMatches(currentPassword, credential)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Enter your current password to save profile changes."));
        }
        String currentEmail = (String) session.getAttribute(SESSION_EMAIL);
        if (currentEmail != null && !normalizedEmail.equalsIgnoreCase(currentEmail)
                && isReservedWorkshopEmail(normalizedEmail)) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "That email address is assigned to a workshop account."));
        }

        boolean changingPassword = !newPassword.isEmpty() || !confirmPassword.isEmpty();
        if (changingPassword && (newPassword.length() < 12 || newPassword.length() > 128
                || !newPassword.equals(confirmPassword))) {
            return ResponseEntity.badRequest().body(Map.of("error",
                    "A new password must be 12–128 characters and both password fields must match."));
        }

        byte[] photoBytes = null;
        String photoContentType = null;
        if (profilePhoto != null && !profilePhoto.isEmpty()) {
            if (profilePhoto.getSize() > MAX_PROFILE_PHOTO_BYTES) {
                return ResponseEntity.badRequest().body(Map.of("error", "Profile photos must be 2 MB or smaller."));
            }
            try {
                photoBytes = profilePhoto.getBytes();
            } catch (IOException exception) {
                LOGGER.warn("Unable to read an uploaded customer profile photo.", exception);
                return ResponseEntity.badRequest().body(Map.of("error", "Unable to read the selected photo."));
            }
            if (photoBytes.length > MAX_PROFILE_PHOTO_BYTES) {
                return ResponseEntity.badRequest().body(Map.of("error", "Profile photos must be 2 MB or smaller."));
            }
            photoContentType = profilePhotoContentType(photoBytes);
            if (photoContentType == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "Choose a valid JPG or PNG image."));
            }
        }

        try {
            StringBuilder sql = new StringBuilder("""
                    UPDATE customer_accounts
                    SET full_name = ?, email = ?, address = ?, phone = ?
                    """);
            List<Object> parameters = new ArrayList<>(List.of(normalizedFullName, normalizedEmail, normalizedAddress, normalizedPhone));
            if (changingPassword) {
                byte[] salt = new byte[PASSWORD_SALT_BYTES];
                SECURE_RANDOM.nextBytes(salt);
                sql.append(", password_salt = ?, password_hash = ?");
                parameters.add(Base64.getEncoder().encodeToString(salt));
                parameters.add(hashPassword(newPassword, salt));
            }
            if (photoBytes != null) {
                sql.append(", profile_photo = ?, profile_photo_content_type = ?");
                parameters.add(photoBytes);
                parameters.add(photoContentType);
            }
            sql.append(" WHERE id = ?");
            parameters.add(customerId);
            int updated = jdbcTemplate.update(sql.toString(), parameters.toArray());
            if (updated != 1) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }
        } catch (DuplicateKeyException exception) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "That email address is already used by another account."));
        }

        CustomerProfile profile = new CustomerProfile(customerId, normalizedFullName, normalizedEmail, normalizedAddress, normalizedPhone);
        setCustomerSession(session, profile);
        return ResponseEntity.ok(profileDetails(profile));
    }

    private String profilePhotoContentType(byte[] bytes) {
        String contentType;
        if (bytes.length >= 8
                && (bytes[0] & 0xff) == 0x89
                && bytes[1] == 0x50
                && bytes[2] == 0x4e
                && bytes[3] == 0x47
                && bytes[4] == 0x0d
                && bytes[5] == 0x0a
                && bytes[6] == 0x1a
                && bytes[7] == 0x0a) {
            contentType = MediaType.IMAGE_PNG_VALUE;
        } else if (bytes.length >= 3
                && (bytes[0] & 0xff) == 0xff
                && (bytes[1] & 0xff) == 0xd8
                && (bytes[2] & 0xff) == 0xff) {
            contentType = MediaType.IMAGE_JPEG_VALUE;
        } else {
            return null;
        }
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
            if (image == null || image.getWidth() > 4096 || image.getHeight() > 4096) {
                return null;
            }
            return contentType;
        } catch (IOException exception) {
            return null;
        }
    }

    private Map<String, String> profileDetails(CustomerProfile profile) {
        return Map.of(
                "fullName", profile.fullName(),
                "email", profile.email(),
                "address", profile.address(),
                "phone", profile.phone());
    }

    private Long customerId(HttpSession session) {
        if (session == null || !USER_ROLE.equals(session.getAttribute(SESSION_ROLE))) {
            return null;
        }
        Object id = session.getAttribute(SESSION_CUSTOMER_ID);
        return id instanceof Number number ? number.longValue() : null;
    }

    private void setCustomerSession(HttpSession session, CustomerProfile profile) {
        session.setAttribute(SESSION_CUSTOMER_ID, profile.id());
        session.setAttribute(SESSION_NAME, profile.fullName());
        session.setAttribute(SESSION_EMAIL, profile.email());
    }

    @PostMapping("/signup")
    @Transactional
    public String signup(
            @RequestParam String fullName,
            @RequestParam String email,
            @RequestParam String password,
            @RequestParam String confirmPassword) {
        String normalizedName = fullName == null ? "" : fullName.trim();
        String normalizedEmail = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        if (normalizedName.isBlank() || normalizedName.length() > 100
                || !normalizedEmail.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")
                || normalizedEmail.length() > 254
                || password == null || password.length() < 12 || password.length() > 128
                || !password.equals(confirmPassword)) {
            return "redirect:/?signupError=invalid";
        }
        if (isReservedWorkshopEmail(normalizedEmail)) {
            return "redirect:/?signupError=exists";
        }

        byte[] salt = new byte[PASSWORD_SALT_BYTES];
        SECURE_RANDOM.nextBytes(salt);
        String saltValue = Base64.getEncoder().encodeToString(salt);
        String passwordHash = hashPassword(password, salt);
        long customerId;
        try {
            jdbcTemplate.update("""
                    INSERT INTO customer_accounts
                        (full_name, email, password_salt, password_hash, email_verified, created_at)
                    VALUES (?, ?, ?, ?, FALSE, ?)
                    """,
                    normalizedName,
                    normalizedEmail,
                    saltValue,
                    passwordHash,
                    Timestamp.from(Instant.now()));
            customerId = jdbcTemplate.queryForObject(
                    "SELECT id FROM customer_accounts WHERE email = ?", Long.class, normalizedEmail);
        } catch (DuplicateKeyException exception) {
            return "redirect:/?signupError=exists";
        }

        String token = createResetToken();
        String tokenHash = hashResetToken(token);
        Instant now = Instant.now();
        jdbcTemplate.update("""
                INSERT INTO customer_email_verification_tokens (customer_id, token_hash, expires_at, created_at)
                VALUES (?, ?, ?, ?)
                """, customerId, tokenHash,
                Timestamp.from(now.plusSeconds(EMAIL_VERIFICATION_LIFETIME_HOURS * 60 * 60)),
                Timestamp.from(now));
        if (!sendVerificationEmail(normalizedName, normalizedEmail, token)) {
            jdbcTemplate.update("DELETE FROM customer_email_verification_tokens WHERE customer_id = ?", customerId);
            jdbcTemplate.update("DELETE FROM customer_accounts WHERE id = ?", customerId);
            return "redirect:/?signupError=delivery";
        }
        return "redirect:/?verificationSent";
    }

    @PostMapping("/verify-email")
    @Transactional
    public String verifyEmail(@RequestParam String token) {
        if (token == null || !token.matches("^[A-Za-z0-9_-]{20,100}$")) {
            return "redirect:/?verificationError=invalid";
        }
        String tokenHash = hashResetToken(token);
        Timestamp now = Timestamp.from(Instant.now());
        List<Long> customerIds = jdbcTemplate.query("""
                SELECT customer_id
                FROM customer_email_verification_tokens
                WHERE token_hash = ? AND used_at IS NULL AND expires_at > ?
                FOR UPDATE
                """, (result, rowNumber) -> result.getLong("customer_id"), tokenHash, now);
        if (customerIds.isEmpty()) {
            return "redirect:/?verificationError=invalid";
        }
        int consumed = jdbcTemplate.update("""
                UPDATE customer_email_verification_tokens
                SET used_at = ?
                WHERE token_hash = ? AND used_at IS NULL AND expires_at > ?
                """, now, tokenHash, now);
        if (consumed != 1) {
            return "redirect:/?verificationError=invalid";
        }
        jdbcTemplate.update(
                "UPDATE customer_accounts SET email_verified = TRUE WHERE id = ?",
                customerIds.getFirst());
        return "redirect:/?verified";
    }

    @PostMapping("/resend-verification")
    @Transactional
    public String resendVerification(@RequestParam String email) {
        String normalizedEmail = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        if (!normalizedEmail.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$") || normalizedEmail.length() > 254) {
            return "redirect:/verify-email.html?sent";
        }
        List<CustomerAccount> accounts = jdbcTemplate.query("""
                SELECT id, full_name, email
                FROM customer_accounts
                WHERE email = ? AND email_verified = FALSE
                FOR UPDATE
                """, (result, rowNumber) -> new CustomerAccount(
                result.getLong("id"), result.getString("full_name"), result.getString("email")),
                normalizedEmail);
        if (accounts.isEmpty()) {
            return "redirect:/verify-email.html?sent";
        }

        CustomerAccount account = accounts.getFirst();
        Instant now = Instant.now();
        jdbcTemplate.update("""
                UPDATE customer_email_verification_tokens SET used_at = ?
                WHERE customer_id = ? AND used_at IS NULL
                """, Timestamp.from(now), account.id());
        String token = createResetToken();
        String tokenHash = hashResetToken(token);
        jdbcTemplate.update("""
                INSERT INTO customer_email_verification_tokens (customer_id, token_hash, expires_at, created_at)
                VALUES (?, ?, ?, ?)
                """, account.id(), tokenHash,
                Timestamp.from(now.plusSeconds(EMAIL_VERIFICATION_LIFETIME_HOURS * 60 * 60)),
                Timestamp.from(now));
        if (!sendVerificationEmail(account.fullName(), account.email(), token)) {
            jdbcTemplate.update("""
                    UPDATE customer_email_verification_tokens SET used_at = ?
                    WHERE token_hash = ? AND used_at IS NULL
                    """, Timestamp.from(Instant.now()), tokenHash);
            return "redirect:/verify-email.html?deliveryError";
        }
        return "redirect:/verify-email.html?sent";
    }

    private boolean sendVerificationEmail(String fullName, String email, String token) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(mailFrom);
        message.setTo(email);
        message.setSubject("Verify your Style Maria email");
        message.setText("""
                Hello %s,

                Verify your email address to activate your Style Maria customer account.
                This link expires in 24 hours and can only be used once:

                %s/verify-email.html?token=%s

                If you did not create this account, you can ignore this message.
                """.formatted(fullName, publicUrl, token));
        try {
            mailSender.send(message);
            return true;
        } catch (MailException exception) {
            LOGGER.warn("Unable to deliver a customer email verification message.", exception);
            return false;
        }
    }

    @PostMapping("/forgot-password")
    public String forgotPassword(@RequestParam String email) {
        String normalizedEmail = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        if (!normalizedEmail.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$") || normalizedEmail.length() > 254) {
            return "redirect:/forgot-password.html?requested";
        }

        List<CustomerAccount> accounts = jdbcTemplate.query("""
                SELECT id, full_name, email
                FROM customer_accounts
                WHERE email = ?
                """, (result, rowNumber) -> new CustomerAccount(
                result.getLong("id"),
                result.getString("full_name"),
                result.getString("email")), normalizedEmail);
        String token = createResetToken();
        String tokenHash = hashResetToken(token);
        Instant now = Instant.now();
        if (accounts.isEmpty()) {
            List<StaffAccount> staffAccounts = jdbcTemplate.query("""
                    SELECT id, display_name, email
                    FROM staff_accounts
                    WHERE email = ? AND active = TRUE
                    """, (result, rowNumber) -> new StaffAccount(
                    result.getLong("id"), result.getString("display_name"), result.getString("email")),
                    normalizedEmail);
            if (staffAccounts.isEmpty()) {
                return "redirect:/forgot-password.html?requested";
            }
            StaffAccount staff = staffAccounts.getFirst();
            jdbcTemplate.update(
                    "DELETE FROM staff_password_reset_tokens WHERE staff_id = ? AND used_at IS NULL",
                    staff.id());
            jdbcTemplate.update("""
                    INSERT INTO staff_password_reset_tokens (staff_id, token_hash, expires_at, created_at)
                    VALUES (?, ?, ?, ?)
                    """, staff.id(), tokenHash,
                    Timestamp.from(now.plusSeconds(RESET_TOKEN_LIFETIME_MINUTES * 60)),
                    Timestamp.from(now));
            if (!sendPasswordResetEmail(staff.displayName(), staff.email(), token, true)) {
                jdbcTemplate.update(
                        "DELETE FROM staff_password_reset_tokens WHERE token_hash = ? AND used_at IS NULL",
                        tokenHash);
                return "redirect:/forgot-password.html?deliveryError";
            }
            return "redirect:/forgot-password.html?requested";
        }

        CustomerAccount account = accounts.getFirst();
        jdbcTemplate.update(
                "DELETE FROM password_reset_tokens WHERE customer_id = ? AND used_at IS NULL",
                account.id());
        jdbcTemplate.update("""
                INSERT INTO password_reset_tokens (customer_id, token_hash, expires_at, created_at)
                VALUES (?, ?, ?, ?)
                """,
                account.id(),
                tokenHash,
                Timestamp.from(now.plusSeconds(RESET_TOKEN_LIFETIME_MINUTES * 60)),
                Timestamp.from(now));

        if (!sendPasswordResetEmail(account.fullName(), account.email(), token, false)) {
            jdbcTemplate.update(
                    "DELETE FROM password_reset_tokens WHERE token_hash = ? AND used_at IS NULL",
                    tokenHash);
            return "redirect:/forgot-password.html?deliveryError";
        }
        return "redirect:/forgot-password.html?requested";
    }

    private boolean sendPasswordResetEmail(String fullName, String email, String token, boolean staff) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(mailFrom);
        message.setTo(email);
        message.setSubject("Reset your Style Maria password");
        message.setText("""
                Hello %s,

                We received a request to reset your Style Maria password.
                Use the link below within 30 minutes. It can only be used once.

                %s/reset-password.html?token=%s

                If you did not request this, you can ignore this email.
                """.formatted(fullName, publicUrl, token));
        try {
            mailSender.send(message);
            return true;
        } catch (MailException exception) {
            LOGGER.warn("Unable to send a {} password reset email.", staff ? "staff" : "customer", exception);
            return false;
        }
    }

    @PostMapping("/reset-password")
    @Transactional
    public String resetPassword(
            @RequestParam String token,
            @RequestParam String password,
            @RequestParam String confirmPassword) {
        if (token == null || token.isBlank()
                || password == null || password.length() < 12 || password.length() > 128
                || !password.equals(confirmPassword)) {
            return "redirect:/reset-password.html?error=invalid";
        }

        String tokenHash = hashResetToken(token);
        Instant now = Instant.now();
        List<Long> customerIds = jdbcTemplate.query("""
                SELECT customer_id
                FROM password_reset_tokens
                WHERE token_hash = ? AND used_at IS NULL AND expires_at > ?
                """, (result, rowNumber) -> result.getLong("customer_id"),
                tokenHash, Timestamp.from(now));
        byte[] salt = new byte[PASSWORD_SALT_BYTES];
        SECURE_RANDOM.nextBytes(salt);
        if (!customerIds.isEmpty()) {
            int tokenUpdated = jdbcTemplate.update("""
                    UPDATE password_reset_tokens
                    SET used_at = ?
                    WHERE token_hash = ? AND used_at IS NULL AND expires_at > ?
                    """, Timestamp.from(now), tokenHash, Timestamp.from(now));
            if (tokenUpdated != 1) {
                return "redirect:/reset-password.html?error=expired";
            }
            jdbcTemplate.update("""
                    UPDATE customer_accounts
                    SET password_salt = ?, password_hash = ?
                    WHERE id = ?
                    """,
                    Base64.getEncoder().encodeToString(salt),
                    hashPassword(password, salt),
                    customerIds.getFirst());
            return "redirect:/?passwordReset";
        }

        List<StaffAccount> staffAccounts = jdbcTemplate.query("""
                SELECT s.id, s.display_name, s.email
                FROM staff_password_reset_tokens t
                JOIN staff_accounts s ON s.id = t.staff_id
                WHERE t.token_hash = ? AND t.used_at IS NULL AND t.expires_at > ?
                    AND s.active = TRUE
                FOR UPDATE
                """, (result, rowNumber) -> new StaffAccount(
                result.getLong("id"), result.getString("display_name"), result.getString("email")),
                tokenHash, Timestamp.from(now));
        if (staffAccounts.isEmpty()) {
            return "redirect:/reset-password.html?error=expired";
        }
        int tokenUpdated = jdbcTemplate.update("""
                UPDATE staff_password_reset_tokens
                SET used_at = ?
                WHERE token_hash = ? AND used_at IS NULL AND expires_at > ?
                """, Timestamp.from(now), tokenHash, Timestamp.from(now));
        int staffUpdated = jdbcTemplate.update("""
                UPDATE staff_accounts SET password_salt = ?, password_hash = ?, updated_at = ?
                WHERE id = ? AND active = TRUE
                """, Base64.getEncoder().encodeToString(salt),
                hashPassword(password, salt), Timestamp.from(now), staffAccounts.getFirst().id());
        if (tokenUpdated != 1 || staffUpdated != 1) {
            return "redirect:/reset-password.html?error=expired";
        }
        AuditEvents.append(jdbcTemplate,
                new AuthenticatedActor(STAFF_ROLE, staffAccounts.getFirst().email()),
                "STAFF_PASSWORD_RESET", "STAFF_ACCOUNT",
                Long.toString(staffAccounts.getFirst().id()), Map.of("method", "email_token"));
        return "redirect:/?passwordReset";
    }

    @PostMapping("/logout")
    public String logout(HttpSession session) {
        if (session != null) {
            session.invalidate();
        }
        return "redirect:/?logout";
    }

    private String authenticate(String username, String password) {
        if (!adminEmail.isBlank() && !adminPassword.isBlank()
                && adminEmail.equalsIgnoreCase(username) && adminPassword.equals(password)) {
            return ADMIN_ROLE;
        }
        if (username == null || password == null) {
            return null;
        }
        String normalizedEmail = username.trim().toLowerCase(Locale.ROOT);
        try {
            StaffCredential credential = jdbcTemplate.queryForObject("""
                    SELECT password_salt, password_hash
                    FROM staff_accounts
                    WHERE email = ? AND active = TRUE
                    """, (result, rowNumber) -> new StaffCredential(
                    result.getString("password_salt"),
                    result.getString("password_hash")), normalizedEmail);
            if (PasswordSecurity.passwordMatches(
                    password, credential.passwordSalt(), credential.passwordHash())) {
                return STAFF_ROLE;
            }
        } catch (EmptyResultDataAccessException exception) {
            // Continue with the existing customer credential lookup.
        }
        try {
            CustomerCredential credential = jdbcTemplate.queryForObject("""
                    SELECT password_salt, password_hash, email_verified
                    FROM customer_accounts
                    WHERE email = ?
                    """, (result, rowNumber) -> new CustomerCredential(
                    result.getString("password_salt"),
                    result.getString("password_hash"),
                    result.getBoolean("email_verified")),
                    normalizedEmail);
            if (passwordMatches(password, credential)) {
                return credential.emailVerified() ? USER_ROLE : UNVERIFIED_ROLE;
            }
        } catch (EmptyResultDataAccessException exception) {
            return null;
        }
        return null;
    }

    private boolean passwordMatches(String password, CustomerCredential credential) {
        return credential != null
                && PasswordSecurity.passwordMatches(
                        password, credential.passwordSalt(), credential.passwordHash());
    }

    private boolean isReservedWorkshopEmail(String email) {
        return (!adminEmail.isBlank() && adminEmail.equalsIgnoreCase(email))
                || jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM staff_accounts WHERE email = ?",
                        Integer.class, email) > 0;
    }

    private String hashPassword(String password, byte[] salt) {
        return PasswordSecurity.hashPassword(password, salt);
    }

    private String createResetToken() {
        byte[] token = new byte[RESET_TOKEN_BYTES];
        SECURE_RANDOM.nextBytes(token);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(token);
    }

    private String hashResetToken(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Password reset token hashing is unavailable.", exception);
        }
    }

    private record CustomerAccount(long id, String fullName, String email) {
    }

    record CustomerProfile(long id, String fullName, String email, String address, String phone) {
    }

    private record ProfilePhoto(byte[] bytes, String contentType) {
    }

    private record CustomerCredential(String passwordSalt, String passwordHash, boolean emailVerified) {
    }

    private record StaffCredential(String passwordSalt, String passwordHash) {
    }

    private record StaffAccount(long id, String displayName, String email) {
    }

    private record StaffSessionIdentity(String email, String displayName) {
    }
}

@Component
class CustomerAccountSchemaMigration implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    CustomerAccountSchemaMigration(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<SchemaColumn> columns = List.of(
                new SchemaColumn("address", "VARCHAR(500) NOT NULL DEFAULT ''"),
                new SchemaColumn("phone", "VARCHAR(40) NOT NULL DEFAULT ''"),
                new SchemaColumn("profile_photo", "MEDIUMBLOB"),
                new SchemaColumn("profile_photo_content_type", "VARCHAR(40)"),
                new SchemaColumn("email_verified", "BOOLEAN NOT NULL DEFAULT TRUE"));
        List<String> missingColumns = jdbcTemplate.execute((ConnectionCallback<List<String>>) connection -> {
            DatabaseMetaData metadata = connection.getMetaData();
            List<String> missing = new ArrayList<>();
            for (SchemaColumn column : columns) {
                boolean exists = false;
                for (String tableName : List.of("customer_accounts", "CUSTOMER_ACCOUNTS")) {
                    for (String columnName : List.of(column.name(), column.name().toUpperCase(Locale.ROOT))) {
                        try (ResultSet result = metadata.getColumns(
                                connection.getCatalog(), null, tableName, columnName)) {
                            if (result.next()) {
                                exists = true;
                                break;
                            }
                        }
                    }
                    if (exists) break;
                }
                if (!exists) missing.add(column.name() + " " + column.definition());
            }
            return missing;
        });
        missingColumns.forEach(column -> jdbcTemplate.execute(
                "ALTER TABLE customer_accounts ADD COLUMN " + column));
    }

    private record SchemaColumn(String name, String definition) {
    }
}

@Component
class CustomOrderSchemaMigration implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    CustomOrderSchemaMigration(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<SchemaColumn> columns = List.of(
                new SchemaColumn("admin_status", "VARCHAR(30) NOT NULL DEFAULT 'NEW_REQUEST'"),
                new SchemaColumn("admin_notes", "VARCHAR(2000) NOT NULL DEFAULT ''"),
                new SchemaColumn("status_updated_at", "TIMESTAMP(6)"),
                new SchemaColumn("requester_name", "VARCHAR(100) NOT NULL DEFAULT ''"),
                new SchemaColumn("requester_email", "VARCHAR(254) NOT NULL DEFAULT ''"),
                new SchemaColumn("requester_phone", "VARCHAR(40) NOT NULL DEFAULT ''"),
                new SchemaColumn("requester_address", "VARCHAR(500) NOT NULL DEFAULT ''"),
                new SchemaColumn("requester_customer_id", "BIGINT"),
                new SchemaColumn("quote_base_amount", "DECIMAL(12, 2)"),
                new SchemaColumn("quote_frame_amount", "DECIMAL(12, 2)"),
                new SchemaColumn("quote_labor_amount", "DECIMAL(12, 2)"),
                new SchemaColumn("quote_delivery_amount", "DECIMAL(12, 2)"),
                new SchemaColumn("quote_total", "DECIMAL(12, 2)"),
                new SchemaColumn("quote_sent_at", "TIMESTAMP(6)"));
        List<String> missingColumns = jdbcTemplate.execute((ConnectionCallback<List<String>>) connection -> {
            DatabaseMetaData metadata = connection.getMetaData();
            List<String> missing = new ArrayList<>();
            for (SchemaColumn column : columns) {
                boolean exists = false;
                for (String tableName : List.of("custom_orders", "CUSTOM_ORDERS")) {
                    for (String columnName : List.of(column.name(), column.name().toUpperCase(Locale.ROOT))) {
                        try (ResultSet result = metadata.getColumns(
                                connection.getCatalog(), null, tableName, columnName)) {
                            if (result.next()) {
                                exists = true;
                                break;
                            }
                        }
                    }
                    if (exists) break;
                }
                if (!exists) missing.add(column.name() + " " + column.definition());
            }
            return missing;
        });
        missingColumns.forEach(column -> jdbcTemplate.execute(
                "ALTER TABLE custom_orders ADD COLUMN " + column));

        List<String> missingCheckoutColumns = jdbcTemplate.execute((ConnectionCallback<List<String>>) connection -> {
            DatabaseMetaData metadata = connection.getMetaData();
            List<String> missing = new ArrayList<>();
            for (SchemaColumn column : List.of(
                    new SchemaColumn("payment_amount", "DECIMAL(12, 2)"),
                    new SchemaColumn("payment_status", "VARCHAR(30) NOT NULL DEFAULT 'SUBMITTED'"),
                    new SchemaColumn("submission_type", "VARCHAR(24) NOT NULL DEFAULT 'UNCLASSIFIED'"),
                    new SchemaColumn("payment_verified_at", "TIMESTAMP(6)"),
                    new SchemaColumn("payment_verified_by_email", "VARCHAR(254)"))) {
                boolean exists = false;
                for (String tableName : List.of("custom_order_checkouts", "CUSTOM_ORDER_CHECKOUTS")) {
                    for (String columnName : List.of(column.name(), column.name().toUpperCase(Locale.ROOT))) {
                        try (ResultSet result = metadata.getColumns(
                                connection.getCatalog(), null, tableName, columnName)) {
                            if (result.next()) {
                                exists = true;
                                break;
                            }
                        }
                    }
                    if (exists) break;
                }
                if (!exists) missing.add(column.name() + " " + column.definition());
            }
            return missing;
        });
        missingCheckoutColumns.forEach(column -> jdbcTemplate.execute(
                "ALTER TABLE custom_order_checkouts ADD COLUMN " + column));
        jdbcTemplate.update("""
                UPDATE custom_order_checkouts
                SET submission_type = CASE
                    WHEN (SELECT quote_sent_at FROM custom_orders
                          WHERE custom_orders.order_id = custom_order_checkouts.order_id) IS NOT NULL
                    THEN 'QUOTE_BASED'
                    ELSE 'DEMO_OR_UNQUOTED'
                END
                WHERE submission_type = 'UNCLASSIFIED'
                """);
    }

    private record SchemaColumn(String name, String definition) {
    }
}

// Demo role-based access interceptor
class DemoRoleInterceptor implements HandlerInterceptor {

    private static final Set<String> ADMIN_ONLY = Set.of(DemoAuthController.ADMIN_ROLE);
    private static final Set<String> STAFF_AND_ADMIN = Set.of(
            DemoAuthController.ADMIN_ROLE, DemoAuthController.STAFF_ROLE);
    private static final Set<String> USER_ONLY = Set.of(DemoAuthController.USER_ROLE);
    private static final Map<String, Set<String>> PAGE_ROLES = Map.ofEntries(
            Map.entry("/admin.html", ADMIN_ONLY),
            Map.entry("/inventory.html", STAFF_AND_ADMIN),
            Map.entry("/reports.html", ADMIN_ONLY),
            Map.entry("/wastage.html", STAFF_AND_ADMIN),
            Map.entry("/auto-bom.html", ADMIN_ONLY),
            Map.entry("/pos.html", STAFF_AND_ADMIN),
            Map.entry("/Custom-req.html", ADMIN_ONLY),
            Map.entry("/custom-request-detail.html", ADMIN_ONLY),
            Map.entry("/mirror-builder", USER_ONLY),
            Map.entry("/payment", USER_ONLY),
            Map.entry("/home.html", USER_ONLY),
            Map.entry("/customize-order.html", USER_ONLY),
            Map.entry("/see-all.html", USER_ONLY),
            Map.entry("/track-order.html", USER_ONLY),
            Map.entry("/payment.html", USER_ONLY));

    private final JdbcTemplate jdbcTemplate;

    DemoRoleInterceptor() {
        this.jdbcTemplate = null;
    }

    DemoRoleInterceptor(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public boolean preHandle(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler) throws IOException {
        if (!isSafeMethod(request.getMethod()) && !isSameOriginMutation(request)) {
            response.sendError(HttpStatus.FORBIDDEN.value(), "Cross-origin request rejected.");
            return false;
        }

        String path = request.getRequestURI().substring(request.getContextPath().length());
        Set<String> requiredRoles = requiredRoles(path, request.getMethod());
        if (requiredRoles.isEmpty()) {
            return true;
        }

        HttpSession session = request.getSession(false);
        Object role = session == null ? null : session.getAttribute(DemoAuthController.SESSION_ROLE);
        if (DemoAuthController.STAFF_ROLE.equals(role)
                && jdbcTemplate != null
                && !isActiveStaff(session)) {
            session.invalidate();
            role = null;
        }
        if (role instanceof String roleValue && requiredRoles.contains(roleValue)) {
            return true;
        }

        if (path.startsWith("/api/")) {
            response.setStatus(role == null ? HttpStatus.UNAUTHORIZED.value() : HttpStatus.FORBIDDEN.value());
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write(role == null
                    ? "{\"error\":\"unauthenticated\",\"message\":\"Sign in to continue.\"}"
                    : "{\"error\":\"forbidden\",\"message\":\"You do not have permission to perform this action.\"}");
            return false;
        }

        if (DemoAuthController.ADMIN_ROLE.equals(role)) {
            response.sendRedirect("/admin.html");
        } else if (DemoAuthController.USER_ROLE.equals(role)) {
            response.sendRedirect("/");
        } else {
            response.sendRedirect("/?error=unauthorized");
        }
        return false;
    }

    private boolean isSafeMethod(String method) {
        return "GET".equals(method) || "HEAD".equals(method) || "OPTIONS".equals(method);
    }

    private boolean isSameOriginMutation(HttpServletRequest request) {
        String fetchSite = request.getHeader("Sec-Fetch-Site");
        if ("cross-site".equalsIgnoreCase(fetchSite) || "same-site".equalsIgnoreCase(fetchSite)) {
            return false;
        }

        String origin = request.getHeader("Origin");
        if (origin == null || origin.isBlank()) {
            return fetchSite == null || "same-origin".equalsIgnoreCase(fetchSite);
        }
        if ("null".equalsIgnoreCase(origin)) {
            return false;
        }

        try {
            java.net.URI parsedOrigin = java.net.URI.create(origin);
            String scheme = parsedOrigin.getScheme();
            String host = parsedOrigin.getHost();
            int originPort = effectivePort(scheme, parsedOrigin.getPort());
            int requestPort = effectivePort(request.getScheme(), request.getServerPort());
            return scheme != null
                    && scheme.equalsIgnoreCase(request.getScheme())
                    && host != null
                    && host.equalsIgnoreCase(request.getServerName())
                    && originPort == requestPort
                    && parsedOrigin.getUserInfo() == null
                    && parsedOrigin.getRawQuery() == null
                    && parsedOrigin.getRawFragment() == null
                    && (parsedOrigin.getRawPath() == null || parsedOrigin.getRawPath().isEmpty()
                            || "/".equals(parsedOrigin.getRawPath()));
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private int effectivePort(String scheme, int port) {
        if (port >= 0) {
            return port;
        }
        if ("https".equalsIgnoreCase(scheme)) {
            return 443;
        }
        if ("http".equalsIgnoreCase(scheme)) {
            return 80;
        }
        return -1;
    }

    private boolean isActiveStaff(HttpSession session) {
        Object email = session.getAttribute(DemoAuthController.SESSION_EMAIL);
        if (!(email instanceof String emailValue) || emailValue.isBlank()) {
            return false;
        }
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM staff_accounts WHERE email = ? AND active = TRUE",
                Integer.class,
                emailValue);
        return count != null && count > 0;
    }

    private Set<String> requiredRoles(String path, String method) {
        Set<String> pageRoles = PAGE_ROLES.get(path);
        if (pageRoles != null) {
            return pageRoles;
        }
        if (path.equals("/api/v1/staff/invitations/accept")
                || (path.equals("/api/v1/custom-orders/quote")
                || path.equals("/api/custom-orders/quote")) && "GET".equals(method)) {
            return Set.of();
        }
        if (path.startsWith("/api/v1/admin/")
                || path.equals("/api/v1/admin")
                || path.startsWith("/api/v1/admin-custom-orders")) {
            return ADMIN_ONLY;
        }
        if (path.equals("/api/v1/session")) {
            return STAFF_AND_ADMIN;
        }
        if (path.equals("/api/v1/session/profile")) {
            return STAFF_AND_ADMIN;
        }
        if (path.startsWith("/api/v1/inventory/materials")
                || path.startsWith("/api/v1/inventory/categories")) {
            return "GET".equals(method) ? STAFF_AND_ADMIN : ADMIN_ONLY;
        }
        if (path.startsWith("/api/v1/inventory/units")
                || path.startsWith("/api/v1/inventory/movements")
                || path.equals("/api/v1/orders")
                || path.startsWith("/api/v1/orders/")
                || path.equals("/api/v1/wastage")
                || path.startsWith("/api/v1/wastage/")
                || path.equals("/api/wastage")
                || path.startsWith("/api/wastage/")) {
            return STAFF_AND_ADMIN;
        }
        if (path.startsWith("/api/v1/customer/")
                || path.equals("/api/v1/customer")
                || path.equals("/api/v1/custom-orders")
                || path.startsWith("/api/v1/custom-orders/")
                || path.equals("/api/custom-orders")
                || path.startsWith("/api/custom-orders/")) {
            return USER_ONLY;
        }
        return Set.of();
    }
}

// Web access configuration
@Configuration
class DemoWebConfig implements WebMvcConfigurer {

    private final JdbcTemplate jdbcTemplate;

    DemoWebConfig(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new DemoRoleInterceptor(jdbcTemplate))
                .addPathPatterns("/**")
                .excludePathPatterns("/", "/index.html", "/css/**", "/js/**");
    }
}

// Redirect legacy routes to the available static pages.
@Controller
@SuppressWarnings("unused")
class DashboardController {

    @GetMapping("/mirror-builder")
    public String mirrorBuilder() {
        return "redirect:/customize-order.html";
    }

    @GetMapping("/payment")
    public String payment() {
        return "redirect:/payment.html";
    }
}

@RestController
@RequestMapping("/api/v1/customer/custom-orders")
class CustomerCustomOrderController {

    private final JdbcTemplate jdbcTemplate;

    CustomerCustomOrderController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping
    public ResponseEntity<?> listOrders(HttpSession session) {
        Long customerId = customerId(session);
        if (customerId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new CustomerCustomOrderError("Sign in to view your custom order requests."));
        }
        List<CustomerCustomOrderSummary> orders = jdbcTemplate.query("""
                SELECT o.order_id, o.reference_image_name, o.submitted_at, o.admin_status,
                       o.status_updated_at, o.quote_sent_at, c.submitted_at AS payment_submitted_at
                FROM custom_orders o
                LEFT JOIN custom_order_checkouts c ON c.order_id = o.order_id
                WHERE o.requester_customer_id = ?
                ORDER BY o.submitted_at DESC, o.id DESC
                """, (result, rowNumber) -> new CustomerCustomOrderSummary(
                result.getString("order_id"),
                result.getString("reference_image_name"),
                result.getTimestamp("submitted_at"),
                customerStatus(
                        result.getString("admin_status"),
                        result.getTimestamp("quote_sent_at"),
                        result.getTimestamp("payment_submitted_at")),
                result.getTimestamp("status_updated_at"),
                result.getTimestamp("quote_sent_at"),
                result.getTimestamp("payment_submitted_at")), customerId);
        return ResponseEntity.ok(orders);
    }

    @GetMapping("/{requestId}")
    public ResponseEntity<?> getOrder(@PathVariable String requestId, HttpSession session) {
        Long customerId = customerId(session);
        if (customerId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new CustomerCustomOrderError("Sign in to view your custom order request."));
        }
        List<CustomerCustomOrderDetail> orders = jdbcTemplate.query("""
                SELECT o.order_id, o.length, o.width, o.unit, o.frame, o.custom_frame_request,
                       o.message, o.reference_image_name, o.submitted_at, o.admin_status,
                       o.status_updated_at, o.quote_sent_at, o.admin_notes,
                       o.quote_base_amount, o.quote_frame_amount, o.quote_labor_amount,
                       o.quote_delivery_amount, o.quote_total,
                       c.submitted_at AS payment_submitted_at, c.payment_amount, c.payment_status
                FROM custom_orders o
                LEFT JOIN custom_order_checkouts c ON c.order_id = o.order_id
                WHERE o.requester_customer_id = ? AND o.order_id = ?
                """, (result, rowNumber) -> {
            Timestamp quoteSentAt = result.getTimestamp("quote_sent_at");
            Timestamp paymentSubmittedAt = result.getTimestamp("payment_submitted_at");
                    BigDecimal quoteTotal = result.getBigDecimal("quote_total");
                    BigDecimal publishedQuoteTotal = quoteSentAt == null ? null : quoteTotal;
                    return new CustomerCustomOrderDetail(
                    result.getString("order_id"),
                    result.getBigDecimal("length"),
                    result.getBigDecimal("width"),
                    result.getString("unit"),
                    result.getString("frame"),
                    result.getString("custom_frame_request"),
                    result.getString("message"),
                    result.getString("reference_image_name"),
                    result.getTimestamp("submitted_at"),
                    customerStatus(result.getString("admin_status"), quoteSentAt, paymentSubmittedAt),
                    result.getTimestamp("status_updated_at"),
                    quoteSentAt,
                    paymentSubmittedAt,
                    result.getBigDecimal("payment_amount"),
                    result.getString("payment_status"),
                    quoteSentAt == null ? null : result.getString("admin_notes"),
                    quoteSentAt == null ? null : result.getBigDecimal("quote_base_amount"),
                    quoteSentAt == null ? null : result.getBigDecimal("quote_frame_amount"),
                    quoteSentAt == null ? null : result.getBigDecimal("quote_labor_amount"),
                    quoteSentAt == null ? null : result.getBigDecimal("quote_delivery_amount"),
                    publishedQuoteTotal,
                    publishedQuoteTotal == null
                            ? null
                            : MirrorPriceCalculator.calculateDownpayment(publishedQuoteTotal));
        }, customerId, requestId);
        return orders.isEmpty()
                ? ResponseEntity.notFound().build()
                : ResponseEntity.ok(orders.getFirst());
    }

    @GetMapping("/{requestId}/reference-image")
    public ResponseEntity<?> getReferenceImage(@PathVariable String requestId, HttpSession session) {
        Long customerId = customerId(session);
        if (customerId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        List<CustomerReferenceImage> images = jdbcTemplate.query("""
                SELECT reference_image_name, reference_image
                FROM custom_orders
                WHERE requester_customer_id = ? AND order_id = ? AND reference_image IS NOT NULL
                """, (result, rowNumber) -> new CustomerReferenceImage(
                result.getString("reference_image_name"),
                result.getBytes("reference_image")), customerId, requestId);
        if (images.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        CustomerReferenceImage image = images.getFirst();
        MediaType contentType = image.name() != null
                && image.name().toLowerCase(Locale.ROOT).endsWith(".png")
                        ? MediaType.IMAGE_PNG
                        : MediaType.IMAGE_JPEG;
        return ResponseEntity.ok()
                .contentType(contentType)
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(image.bytes());
    }

    private Long customerId(HttpSession session) {
        if (session == null
                || !DemoAuthController.USER_ROLE.equals(
                        session.getAttribute(DemoAuthController.SESSION_ROLE))) {
            return null;
        }
        Object id = session.getAttribute(DemoAuthController.SESSION_CUSTOMER_ID);
        return id instanceof Number number ? number.longValue() : null;
    }

    private String customerStatus(String status, Timestamp quoteSentAt, Timestamp paymentSubmittedAt) {
        if (paymentSubmittedAt != null) return "PAYMENT_SUBMITTED";
        if (quoteSentAt != null) return "QUOTATION_SENT";
        return status;
    }

    public record CustomerCustomOrderSummary(
            String requestId,
            String referenceImageName,
            Timestamp submittedAt,
            String status,
            Timestamp statusUpdatedAt,
            Timestamp quoteSentAt,
            Timestamp paymentSubmittedAt) {
    }

    public record CustomerCustomOrderDetail(
            String requestId,
            BigDecimal length,
            BigDecimal width,
            String unit,
            String frame,
            String customFrameRequest,
            String message,
            String referenceImageName,
            Timestamp submittedAt,
            String status,
            Timestamp statusUpdatedAt,
            Timestamp quoteSentAt,
            Timestamp paymentSubmittedAt,
            BigDecimal paymentAmount,
            String paymentStatus,
            String quoteNotes,
            BigDecimal quoteBaseMirrorAmount,
            BigDecimal quoteFrameAmount,
            BigDecimal quoteLaborAmount,
            BigDecimal quoteDeliveryAmount,
            BigDecimal quoteTotal,
            BigDecimal quoteDownpayment) {
    }

    public record CustomerCustomOrderError(String message) {
    }

    private record CustomerReferenceImage(String name, byte[] bytes) {
    }
}

@RestController
@RequestMapping("/api/v1/admin/custom-orders")
class AdminCustomOrderController {

    private static final Set<String> ALLOWED_STATUSES = Set.of(
            "NEW_REQUEST", "FOR_QUOTATION", "NEEDS_INFO");
    private static final BigDecimal MAX_QUOTE_AMOUNT = new BigDecimal("9999999999.99");

    private final JdbcTemplate jdbcTemplate;

    AdminCustomOrderController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping
    public List<CustomOrderSummary> listRequests() {
        return jdbcTemplate.query("""
                SELECT o.order_id,
                       COALESCE(NULLIF(c.customer_name, ''), NULLIF(o.requester_name, ''), '') AS customer_name,
                       o.reference_image_name, o.submitted_at, o.admin_status
                FROM custom_orders o
                LEFT JOIN custom_order_checkouts c ON c.order_id = o.order_id
                ORDER BY o.submitted_at DESC, o.id DESC
                """, (result, rowNumber) -> new CustomOrderSummary(
                result.getString("order_id"),
                result.getString("customer_name"),
                result.getString("reference_image_name"),
                result.getTimestamp("submitted_at"),
                result.getString("admin_status")));
    }

    @GetMapping("/{requestId}")
    public ResponseEntity<?> getRequest(@PathVariable String requestId) {
        List<CustomOrderDetail> requests = jdbcTemplate.query("""
                SELECT o.order_id, o.length, o.width, o.unit, o.frame, o.custom_frame_request,
                       o.message, o.reference_image_name, o.submitted_at, o.total, o.admin_status,
                       o.admin_notes, o.status_updated_at,
                       COALESCE(NULLIF(c.customer_name, ''), o.requester_name, '') AS customer_name,
                       COALESCE(NULLIF(c.address, ''), o.requester_address, '') AS customer_address,
                       COALESCE(NULLIF(c.contact_number, ''), o.requester_phone, '') AS contact_number,
                       o.requester_email,
                       o.quote_base_amount, o.quote_frame_amount, o.quote_labor_amount,
                       o.quote_delivery_amount, o.quote_total, o.quote_sent_at,
                       COALESCE(c.payment_method, '') AS payment_method,
                       COALESCE(c.payment_reference, '') AS payment_reference,
                       c.receipt_name, c.submitted_at AS payment_submitted_at,
                       c.payment_amount, c.payment_status
                FROM custom_orders o
                LEFT JOIN custom_order_checkouts c ON c.order_id = o.order_id
                WHERE o.order_id = ?
                """, (result, rowNumber) -> new CustomOrderDetail(
                result.getString("order_id"),
                result.getBigDecimal("length"),
                result.getBigDecimal("width"),
                result.getString("unit"),
                result.getString("frame"),
                result.getString("custom_frame_request"),
                result.getString("message"),
                result.getString("reference_image_name"),
                result.getTimestamp("submitted_at"),
                result.getBigDecimal("total"),
                result.getString("admin_status"),
                result.getString("admin_notes"),
                result.getTimestamp("status_updated_at"),
                result.getString("customer_name"),
                result.getString("customer_address"),
                result.getString("contact_number"),
                result.getString("requester_email"),
                result.getString("payment_method"),
                result.getString("payment_reference"),
                result.getBigDecimal("quote_base_amount"),
                result.getBigDecimal("quote_frame_amount"),
                result.getBigDecimal("quote_labor_amount"),
                result.getBigDecimal("quote_delivery_amount"),
                result.getBigDecimal("quote_total"),
                result.getTimestamp("quote_sent_at"),
                result.getString("receipt_name"),
                result.getTimestamp("payment_submitted_at"),
                result.getBigDecimal("payment_amount"),
                result.getString("payment_status")), requestId);
        return requests.isEmpty() ? ResponseEntity.notFound().build() : ResponseEntity.ok(requests.getFirst());
    }

    @GetMapping("/{requestId}/reference-image")
    public ResponseEntity<?> getReferenceImage(@PathVariable String requestId) {
        List<ReferenceImage> images = jdbcTemplate.query("""
                SELECT reference_image_name, reference_image
                FROM custom_orders
                WHERE order_id = ? AND reference_image IS NOT NULL
                """, (result, rowNumber) -> new ReferenceImage(
                result.getString("reference_image_name"),
                result.getBytes("reference_image")), requestId);
        if (images.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        ReferenceImage image = images.getFirst();
        String filename = image.name() == null ? "" : image.name().toLowerCase(Locale.ROOT);
        MediaType contentType = filename.endsWith(".png") ? MediaType.IMAGE_PNG : MediaType.IMAGE_JPEG;
        return ResponseEntity.ok()
                .contentType(contentType)
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(image.bytes());
    }

    @GetMapping("/{requestId}/payment-proof")
    public ResponseEntity<?> getPaymentProof(@PathVariable String requestId) {
        List<PaymentProof> proofs = jdbcTemplate.query("""
                SELECT receipt_name, receipt
                FROM custom_order_checkouts
                WHERE order_id = ? AND receipt IS NOT NULL
                """, (result, rowNumber) -> new PaymentProof(
                result.getString("receipt_name"),
                result.getBytes("receipt")), requestId);
        if (proofs.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        PaymentProof proof = proofs.getFirst();
        String filename = proof.name() == null ? "" : proof.name().toLowerCase(Locale.ROOT);
        MediaType contentType = filename.endsWith(".pdf")
                ? MediaType.APPLICATION_PDF
                : filename.endsWith(".png") ? MediaType.IMAGE_PNG : MediaType.IMAGE_JPEG;
        return ResponseEntity.ok()
                .contentType(contentType)
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                .body(proof.bytes());
    }

    @PutMapping("/{requestId}")
    @Transactional
    public ResponseEntity<?> updateRequest(
            @PathVariable String requestId,
            @RequestBody AdminCustomOrderUpdate update,
            HttpSession session) {
        if (update == null
                || update.status() == null
                || !ALLOWED_STATUSES.contains(update.status())
                || update.notes() == null
                || update.notes().length() > 2000) {
            return ResponseEntity.badRequest()
                    .body(new CustomOrderController.ApiError("Choose a valid status and notes up to 2,000 characters."));
        }
        int updated = jdbcTemplate.update("""
                UPDATE custom_orders
                SET admin_status = ?, admin_notes = ?, status_updated_at = CURRENT_TIMESTAMP
                WHERE order_id = ?
                """, update.status(), update.notes().trim(), requestId);
        if (updated == 0) {
            Integer exists = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM custom_orders WHERE order_id = ?",
                    Integer.class,
                    requestId);
            if (exists == null || exists == 0) {
                return ResponseEntity.notFound().build();
            }
        }
        AuditEvents.append(jdbcTemplate, AuthenticatedActor.from(session),
                "CUSTOM_ORDER_STATUS_CHANGED", "CUSTOM_ORDER", requestId,
                Map.of("status", update.status(), "notes", update.notes().trim()));
        return ResponseEntity.ok(new AdminCustomOrderUpdateResponse(requestId, update.status()));
    }

    @PutMapping("/{requestId}/quote-draft")
    @Transactional
    public ResponseEntity<?> saveQuoteDraft(
            @PathVariable String requestId,
            @RequestBody AdminQuoteRequest quote,
            HttpSession session) {
        String validationError = validateQuote(quote);
        if (validationError != null) {
            return ResponseEntity.badRequest().body(new CustomOrderController.ApiError(validationError));
        }
        BigDecimal total = quote.total();
        int updated = jdbcTemplate.update("""
                UPDATE custom_orders
                SET quote_base_amount = ?, quote_frame_amount = ?, quote_labor_amount = ?,
                    quote_delivery_amount = ?, quote_total = ?, admin_notes = ?
                WHERE order_id = ?
                """,
                quote.baseMirrorAmount(),
                quote.frameAmount(),
                quote.laborAmount(),
                quote.deliveryAmount(),
                total,
                quote.notes().trim(),
                requestId);
        if (updated == 0) {
            Integer exists = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM custom_orders WHERE order_id = ?",
                    Integer.class,
                    requestId);
            if (exists == null || exists == 0) {
                return ResponseEntity.notFound().build();
            }
        }
        AuditEvents.append(jdbcTemplate, AuthenticatedActor.from(session),
                "CUSTOM_ORDER_QUOTE_DRAFT_SAVED", "CUSTOM_ORDER", requestId,
                Map.of("total", total, "notes", quote.notes().trim()));
        return ResponseEntity.ok(new AdminQuoteResponse(requestId, "DRAFT_SAVED", total));
    }

    @PostMapping("/{requestId}/publish-quote")
    @Transactional
    public ResponseEntity<?> publishOfficialQuote(
            @PathVariable String requestId,
            @RequestBody AdminQuoteRequest quote,
            HttpSession session) {
        String validationError = validateQuote(quote);
        if (validationError != null) {
            return ResponseEntity.badRequest().body(new CustomOrderController.ApiError(validationError));
        }
        BigDecimal total = quote.total();
        int updated = jdbcTemplate.update("""
                UPDATE custom_orders
                SET quote_base_amount = ?, quote_frame_amount = ?, quote_labor_amount = ?,
                    quote_delivery_amount = ?, quote_total = ?, admin_notes = ?,
                    admin_status = 'FOR_QUOTATION',
                    status_updated_at = CURRENT_TIMESTAMP,
                    quote_sent_at = CURRENT_TIMESTAMP
                WHERE order_id = ?
                """,
                quote.baseMirrorAmount(),
                quote.frameAmount(),
                quote.laborAmount(),
                quote.deliveryAmount(),
                total,
                quote.notes().trim(),
                requestId);
        if (updated == 0) {
            return ResponseEntity.notFound().build();
        }
        AuditEvents.append(jdbcTemplate, AuthenticatedActor.from(session),
                "CUSTOM_ORDER_QUOTE_PUBLISHED", "CUSTOM_ORDER", requestId,
                Map.of("total", total, "notes", quote.notes().trim()));
        return ResponseEntity.ok(new AdminQuoteResponse(requestId, "FOR_QUOTATION", total));
    }

    @PostMapping("/{requestId}/payment-verification")
    @Transactional
    public ResponseEntity<?> verifyPayment(
            @PathVariable String requestId,
            @RequestBody PaymentVerificationRequest verification,
            HttpSession session) {
        if (verification == null
                || verification.decision() == null
                || !Set.of("VERIFIED", "REJECTED").contains(verification.decision())
                || verification.notes() == null
                || verification.notes().trim().length() > 2000) {
            return ResponseEntity.badRequest().body(
                    new CustomOrderController.ApiError("Choose VERIFIED or REJECTED and provide notes up to 2,000 characters."));
        }
        AuthenticatedActor actor = AuthenticatedActor.from(session);
        List<PaymentReviewState> statuses = jdbcTemplate.query("""
                SELECT payment_status, submission_type
                FROM custom_order_checkouts
                WHERE order_id = ?
                FOR UPDATE
                """, (result, row) -> new PaymentReviewState(
                result.getString("payment_status"), result.getString("submission_type")), requestId);
        if (statuses.isEmpty()) {
            Integer orderExists = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM custom_orders WHERE order_id = ?", Integer.class, requestId);
            if (orderExists == null || orderExists == 0) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new CustomOrderController.ApiError("No payment submission exists for this order."));
        }
        PaymentReviewState payment = statuses.getFirst();
        if (!"QUOTE_BASED".equals(payment.submissionType())) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new CustomOrderController.ApiError(
                            "Demo or legacy-unclassified checkout submissions are not eligible for verification."));
        }
        if (!"SUBMITTED".equalsIgnoreCase(payment.paymentStatus())) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new CustomOrderController.ApiError("This payment submission has already been reviewed."));
        }
        Instant verifiedAt = Instant.now();
        jdbcTemplate.update("""
                UPDATE custom_order_checkouts
                SET payment_status = ?, payment_verified_at = ?, payment_verified_by_email = ?
                WHERE order_id = ? AND payment_status = 'SUBMITTED'
                """, verification.decision(), Timestamp.from(verifiedAt), actor.email(), requestId);
        AuditEvents.append(jdbcTemplate, actor, "CUSTOM_ORDER_PAYMENT_VERIFIED", "CUSTOM_ORDER", requestId,
                Map.of("decision", verification.decision(), "notes", verification.notes().trim()));
        return ResponseEntity.ok(new PaymentVerificationResponse(
                requestId, verification.decision(), Timestamp.from(verifiedAt)));
    }

    private String validateQuote(AdminQuoteRequest quote) {
        if (quote == null || quote.notes() == null || quote.notes().length() > 2000) {
            return "Enter quote notes up to 2,000 characters.";
        }
        if (!isValidQuoteAmount(quote.baseMirrorAmount())
                || !isValidQuoteAmount(quote.frameAmount())
                || !isValidQuoteAmount(quote.laborAmount())
                || !isValidQuoteAmount(quote.deliveryAmount())
                || quote.total().compareTo(MAX_QUOTE_AMOUNT) > 0) {
            return "Enter non-negative quote amounts with no more than two decimal places.";
        }
        return null;
    }

    private boolean isValidQuoteAmount(BigDecimal amount) {
        return amount != null
                && amount.signum() >= 0
                && amount.compareTo(MAX_QUOTE_AMOUNT) <= 0
                && amount.scale() <= 2;
    }

    public record CustomOrderSummary(
            String requestId,
            String customerName,
            String referenceImageName,
            Timestamp submittedAt,
            String status) {
    }

    public record CustomOrderDetail(
            String requestId,
            BigDecimal length,
            BigDecimal width,
            String unit,
            String frame,
            String customFrameRequest,
            String message,
            String referenceImageName,
            Timestamp submittedAt,
            BigDecimal total,
            String status,
            String notes,
            Timestamp statusUpdatedAt,
            String customerName,
            String address,
            String contactNumber,
            String email,
            String paymentMethod,
            String paymentReference,
            BigDecimal quoteBaseMirrorAmount,
            BigDecimal quoteFrameAmount,
            BigDecimal quoteLaborAmount,
            BigDecimal quoteDeliveryAmount,
            BigDecimal quoteTotal,
            Timestamp quoteSentAt,
            String receiptName,
            Timestamp paymentSubmittedAt,
            BigDecimal paymentAmount,
            String paymentStatus) {
    }

    public record AdminCustomOrderUpdate(String status, String notes) {
    }

    public record AdminQuoteRequest(
            BigDecimal baseMirrorAmount,
            BigDecimal frameAmount,
            BigDecimal laborAmount,
            BigDecimal deliveryAmount,
            String notes) {

        BigDecimal total() {
            return baseMirrorAmount.add(frameAmount).add(laborAmount).add(deliveryAmount).setScale(2);
        }
    }

    public record AdminQuoteResponse(String requestId, String status, BigDecimal total) {
    }

    public record AdminCustomOrderUpdateResponse(String requestId, String status) {
    }

    public record PaymentVerificationRequest(String decision, String notes) {
    }

    public record PaymentVerificationResponse(String requestId, String paymentStatus, Timestamp verifiedAt) {
    }

    private record PaymentReviewState(String paymentStatus, String submissionType) {
    }

    private record ReferenceImage(String name, byte[] bytes) {
    }

    private record PaymentProof(String name, byte[] bytes) {
    }
}

// Wastage tracking API
@RestController
@RequestMapping({"/api/wastage", "/api/v1/wastage"})
class WastageController {

    private static final BigDecimal LABOUR_COST = new BigDecimal("45.00");
    private static final Map<String, BigDecimal> MATERIAL_UNIT_COSTS = Map.of(
            "Mirror Glass", new BigDecimal("75.00"),
            "MDF Boards", new BigDecimal("45.00"),
            "Aluminum Frames", new BigDecimal("65.00"),
            "Adhesives", new BigDecimal("25.00"),
            "Wall Mounts", new BigDecimal("30.00"),
            "Protective Films", new BigDecimal("12.00"),
            "Edge Trim", new BigDecimal("18.00"),
            "Cleaning Solution", new BigDecimal("20.00"));

    private final JdbcTemplate jdbcTemplate;

    WastageController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping
    public List<WastageLog> getLogs() {
        return jdbcTemplate.query("""
                SELECT id, order_id, material, description, reason, action_taken, quantity, unit,
                       logged_by, logged_at, order_stage, delivery_date, material_cost, labour_cost,
                       total_impact, recoverable, status
                FROM wastage_logs
                ORDER BY logged_at DESC
                """, (result, rowNumber) -> new WastageLog(
                result.getLong("id"),
                result.getString("order_id"),
                result.getString("material"),
                result.getString("description"),
                result.getString("reason"),
                result.getString("action_taken"),
                result.getBigDecimal("quantity"),
                result.getString("unit"),
                result.getString("logged_by"),
                result.getTimestamp("logged_at").toInstant(),
                result.getString("order_stage"),
                result.getDate("delivery_date").toLocalDate(),
                result.getBigDecimal("material_cost"),
                result.getBigDecimal("labour_cost"),
                result.getBigDecimal("total_impact"),
                result.getBoolean("recoverable"),
                result.getString("status")));
    }

    @PostMapping
    @Transactional
    public ResponseEntity<?> createLog(@RequestBody WastageRequest request, HttpSession session) {
        String validationError = validate(request);
        if (validationError != null) {
            return ResponseEntity.badRequest().body(new ApiError(validationError));
        }

        AuthenticatedActor actor = AuthenticatedActor.from(session);
        InventoryMaterial inventoryMaterial = findInventoryMaterial(request);
        if (!inventoryMaterial.active()) {
            return ResponseEntity.badRequest().body(new ApiError("Select an active inventory material."));
        }
        if (!unitMatches(request.unit(), inventoryMaterial.unitCode(), inventoryMaterial.unitName())) {
            return ResponseEntity.badRequest().body(new ApiError(
                    "Wastage unit must match the selected inventory material unit."));
        }
        BigDecimal balanceAfter = inventoryMaterial.quantityOnHand().subtract(request.quantity());
        if (balanceAfter.signum() < 0) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new ApiError("Insufficient stock to record this wastage."));
        }

        BigDecimal materialCost = MATERIAL_UNIT_COSTS.getOrDefault(inventoryMaterial.name(), BigDecimal.ZERO)
                .multiply(request.quantity())
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalImpact = materialCost.add(LABOUR_COST).setScale(2, RoundingMode.HALF_UP);
        Instant loggedAt = Instant.now();
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO wastage_logs
                    (order_id, material, description, reason, action_taken, quantity, unit, logged_by,
                     logged_at, order_stage, delivery_date, material_cost, labour_cost, total_impact,
                     recoverable, status)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, request.orderId().trim());
            statement.setString(2, inventoryMaterial.name());
            statement.setString(3, request.description() == null ? "" : request.description().trim());
            statement.setString(4, request.reason());
            statement.setString(5, request.actionTaken());
            statement.setBigDecimal(6, request.quantity());
            statement.setString(7, inventoryMaterial.unitCode());
            statement.setString(8, actor.email());
            statement.setTimestamp(9, Timestamp.from(loggedAt));
            statement.setString(10, request.orderStage());
            statement.setDate(11, java.sql.Date.valueOf(request.deliveryDate()));
            statement.setBigDecimal(12, materialCost);
            statement.setBigDecimal(13, LABOUR_COST);
            statement.setBigDecimal(14, totalImpact);
            statement.setBoolean(15, request.recoverable());
            statement.setString(16, request.status());
            return statement;
        }, keyHolder);
        Number generatedId = keyHolder.getKey();
        if (generatedId == null) {
            throw new IllegalStateException("MySQL did not return an ID for the new wastage log.");
        }
        long wastageId = generatedId.longValue();
        Instant occurredAt = Instant.now();
        jdbcTemplate.update("""
                UPDATE inventory_materials
                SET quantity_on_hand = ?, updated_at = ?
                WHERE id = ?
                """, balanceAfter, Timestamp.from(occurredAt), inventoryMaterial.id());
        jdbcTemplate.update("""
                INSERT INTO inventory_movements
                    (material_id, movement_type, quantity_delta, balance_after, actor_email,
                     actor_role, occurred_at, reference_id, wastage_log_id, reason)
                VALUES (?, 'WASTAGE', ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                inventoryMaterial.id(),
                request.quantity().negate(),
                balanceAfter,
                actor.email(),
                actor.role(),
                Timestamp.from(occurredAt),
                request.orderId().trim(),
                wastageId,
                request.reason().trim());
        AuditEvents.append(jdbcTemplate, actor, "WASTAGE_RECORDED", "WASTAGE_LOG",
                Long.toString(wastageId), Map.of(
                        "orderId", request.orderId().trim(),
                        "materialId", inventoryMaterial.id(),
                        "quantity", request.quantity(),
                        "balanceAfter", balanceAfter));

        WastageLog log = new WastageLog(
                wastageId,
                request.orderId().trim(),
                inventoryMaterial.name(),
                request.description() == null ? "" : request.description().trim(),
                request.reason(),
                request.actionTaken(),
                request.quantity(),
                inventoryMaterial.unitCode(),
                actor.email(),
                loggedAt,
                request.orderStage(),
                request.deliveryDate(),
                materialCost,
                LABOUR_COST,
                totalImpact,
                request.recoverable(),
                request.status());
        return ResponseEntity.status(HttpStatus.CREATED).body(log);
    }

    private String validate(WastageRequest request) {
        if (request == null) {
            return "A wastage log is required.";
        }
        if (isBlank(request.orderId())) {
            return "Order ID is required.";
        }
        if (isBlank(request.material())) return "Select a material.";
        if (request.quantity() == null || request.quantity().signum() <= 0) {
            return "Quantity must be greater than zero.";
        }
        if (request.quantity().scale() > 3 || request.quantity().precision() - request.quantity().scale() > 11) {
            return "Quantity must use at most three decimal places and fit the inventory quantity range.";
        }
        if (isBlank(request.unit()) || isBlank(request.reason()) || isBlank(request.actionTaken())) {
            return "Unit, reason, and action taken are required.";
        }
        if (isBlank(request.orderStage()) || request.deliveryDate() == null) {
            return "Order stage and delivery date are required.";
        }
        if (request.recoverable() == null || isBlank(request.status())) {
            return "Recoverable and status values are required.";
        }
        if (request.orderId().trim().length() > 100 || request.material().trim().length() > 100
                || request.description() != null && request.description().length() > 1000
                || request.reason().trim().length() > 500 || request.actionTaken().trim().length() > 500
                || request.unit().trim().length() > 50 || request.orderStage().trim().length() > 100
                || request.status().trim().length() > 50) {
            return "One or more wastage fields exceed their maximum length.";
        }
        return null;
    }

    private InventoryMaterial findInventoryMaterial(WastageRequest request) {
        List<InventoryMaterial> materials;
        if (request.materialId() != null) {
            materials = jdbcTemplate.query("""
                    SELECT m.id, m.name, m.unit_code, u.display_name, m.quantity_on_hand, m.active
                    FROM inventory_materials m
                    JOIN inventory_units u ON u.code = m.unit_code
                    WHERE m.id = ?
                    FOR UPDATE
                    """, (result, row) -> new InventoryMaterial(
                    result.getLong("id"),
                    result.getString("name"),
                    result.getString("unit_code"),
                    result.getString("display_name"),
                    result.getBigDecimal("quantity_on_hand"),
                    result.getBoolean("active")), request.materialId());
        } else {
            materials = jdbcTemplate.query("""
                    SELECT m.id, m.name, m.unit_code, u.display_name, m.quantity_on_hand, m.active
                    FROM inventory_materials m
                    JOIN inventory_units u ON u.code = m.unit_code
                    WHERE LOWER(m.name) = LOWER(?) AND m.active = TRUE
                    FOR UPDATE
                    """, (result, row) -> new InventoryMaterial(
                    result.getLong("id"),
                    result.getString("name"),
                    result.getString("unit_code"),
                    result.getString("display_name"),
                    result.getBigDecimal("quantity_on_hand"),
                    result.getBoolean("active")), request.material().trim());
        }
        if (materials.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "material_unavailable",
                    "The material must exist in the inventory catalog before wastage can be recorded.");
        }
        if (materials.size() > 1) {
            throw new ApiException(HttpStatus.CONFLICT, "ambiguous_material",
                    "More than one catalog material has this name; submit its material ID.");
        }
        return materials.getFirst();
    }

    private boolean unitMatches(String requested, String unitCode, String unitName) {
        String normalized = requested.trim().toUpperCase(Locale.ROOT);
        if (normalized.equals(unitCode) || normalized.equals(unitName.toUpperCase(Locale.ROOT))) {
            return true;
        }
        return "EA".equals(unitCode) && Set.of("PCS", "PC", "PIECE", "PIECES", "EACH").contains(normalized);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public record WastageRequest(
            String orderId,
            String material,
            String description,
            String reason,
            String actionTaken,
            BigDecimal quantity,
            String unit,
            String loggedBy,
            String orderStage,
            LocalDate deliveryDate,
            Boolean recoverable,
            String status,
            Long materialId) {

        public WastageRequest(
                String orderId,
                String material,
                String description,
                String reason,
                String actionTaken,
                BigDecimal quantity,
                String unit,
                String loggedBy,
                String orderStage,
                LocalDate deliveryDate,
                Boolean recoverable,
                String status) {
            this(orderId, material, description, reason, actionTaken, quantity, unit, loggedBy,
                    orderStage, deliveryDate, recoverable, status, null);
        }
    }

    public record WastageLog(
            long id,
            String orderId,
            String material,
            String description,
            String reason,
            String actionTaken,
            BigDecimal quantity,
            String unit,
            String loggedBy,
            Instant loggedAt,
            String orderStage,
            LocalDate deliveryDate,
            BigDecimal materialCost,
            BigDecimal labourCost,
            BigDecimal totalImpact,
            boolean recoverable,
            String status) {
    }

    public record ApiError(String message) {
    }

    private record InventoryMaterial(
            long id,
            String name,
            String unitCode,
            String unitName,
            BigDecimal quantityOnHand,
            boolean active) {
    }
}

// Custom mirror order API
@RestController
@RequestMapping({"/api/custom-orders", "/api/v1/custom-orders"})
class CustomOrderController {

    private static final BigDecimal MIN_DIMENSION = BigDecimal.ONE;
    private static final BigDecimal MAX_DIMENSION_INCHES = new BigDecimal("500");
    private static final BigDecimal MAX_DIMENSION_CENTIMETERS = new BigDecimal("1270");
    private static final long MAX_REFERENCE_IMAGE_BYTES = 10L * 1024 * 1024;
    private static final Set<String> ALLOWED_FRAMES = Set.of(
            "Solid Wood", "Natural Oak", "Matte Black", "White", "Custom (Request)");

    private final JdbcTemplate jdbcTemplate;

    CustomOrderController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/quote")
    public MirrorPriceCalculator.PriceQuote getQuote() {
        return MirrorPriceCalculator.calculateDefaultQuote();
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Transactional
    public ResponseEntity<?> submitRequest(
            @RequestParam("length") BigDecimal length,
            @RequestParam("width") BigDecimal width,
            @RequestParam("unit") String unit,
            @RequestParam("frame") String frame,
            @RequestParam(name = "customFrameRequest", required = false) String customFrameRequest,
            @RequestParam(name = "message", required = false) String message,
            @RequestParam(name = "referenceImage", required = false) MultipartFile referenceImage,
            HttpSession session) throws IOException {
        RequesterInfo requesterInfo = getRequesterInfo(session);
        if (requesterInfo.customerId() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ApiError("Sign in to save this custom request to your customer account."));
        }
        return saveRequest(
                length,
                width,
                unit,
                frame,
                customFrameRequest,
                message,
                referenceImage,
                requesterInfo);
    }

    ResponseEntity<?> submitRequest(
            BigDecimal length,
            BigDecimal width,
            String unit,
            String frame,
            String customFrameRequest,
            String message,
            MultipartFile referenceImage) throws IOException {
        return saveRequest(length, width, unit, frame, customFrameRequest, message, referenceImage, RequesterInfo.empty());
    }

    private ResponseEntity<?> saveRequest(
            BigDecimal length,
            BigDecimal width,
            String unit,
            String frame,
            String customFrameRequest,
            String message,
            MultipartFile referenceImage,
            RequesterInfo requesterInfo) throws IOException {
        String validationError = validate(length, width, unit, frame, customFrameRequest, message, referenceImage);
        if (validationError != null) {
            return ResponseEntity.badRequest().body(new ApiError(validationError));
        }

        String imageName = referenceImage == null || referenceImage.isEmpty()
                ? null
                : referenceImage.getOriginalFilename();
        byte[] imageBytes = referenceImage == null || referenceImage.isEmpty()
                ? null
                : referenceImage.getBytes();
        MirrorPriceCalculator.PriceQuote quote = MirrorPriceCalculator.calculateDefaultQuote();
        String orderId = insertCustomOrder(
                length,
                width,
                unit,
                frame,
                normalize(customFrameRequest),
                normalize(message),
                imageName,
                imageBytes,
                Instant.now(),
                quote,
                requesterInfo);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new CustomOrderResponse(orderId, "REQUEST_RECEIVED", quote));
    }

    @PostMapping(
            path = "/{orderId}/checkout",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Transactional
    public ResponseEntity<?> submitCheckout(
            @PathVariable String orderId,
            @RequestParam("paymentMethod") String paymentMethod,
            @RequestParam(name = "paymentReference", required = false) String paymentReference,
            @RequestParam(name = "termsConfirmed", defaultValue = "false") boolean termsConfirmed,
            @RequestParam(name = "receipt", required = false) MultipartFile receipt,
            HttpSession session) throws IOException {
        RequesterInfo requesterInfo = getRequesterInfo(session);
        if (requesterInfo.customerId() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ApiError("Sign in to submit checkout for your custom order."));
        }
        if (!orderBelongsToCustomer(orderId, requesterInfo.customerId())) {
            return ResponseEntity.notFound().build();
        }
        PublishedQuote quote = getPublishedQuote(orderId);
        if (quote == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ApiError("A published quotation is required before you can submit payment."));
        }
        if (checkoutExists(orderId)) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new ApiError("Checkout has already been submitted for this custom order."));
        }

        String validationError = validateCheckout(
                paymentMethod,
                paymentReference,
                termsConfirmed,
                receipt,
                true,
                "Confirm that this is a payment submission for shop verification.");
        if (validationError != null) {
            return ResponseEntity.badRequest().body(new ApiError(validationError));
        }

        try {
            insertCheckout(
                    orderId,
                    requesterInfo.name(),
                    requesterInfo.address(),
                    requesterInfo.phone(),
                    paymentMethod,
                    paymentReference,
                    receipt,
                    quote.downpayment(),
                    Instant.now(),
                    "QUOTE_BASED");
        } catch (DuplicateKeyException exception) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new ApiError("Checkout has already been submitted for this custom order."));
        }
        AuditEvents.append(jdbcTemplate,
                new AuthenticatedActor(DemoAuthController.USER_ROLE, requesterInfo.email()),
                "CUSTOM_ORDER_PAYMENT_SUBMITTED", "CUSTOM_ORDER", orderId,
                Map.of("paymentAmount", quote.downpayment(), "paymentStatus", "SUBMITTED"));

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new CheckoutResponse(orderId, "PAYMENT_SUBMITTED", quote.total(), quote.downpayment()));
    }

    @PostMapping(
            path = "/checkout",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Transactional
    public ResponseEntity<?> placeDemoOrder(
            @RequestParam("length") BigDecimal length,
            @RequestParam("width") BigDecimal width,
            @RequestParam("unit") String unit,
            @RequestParam("frame") String frame,
            @RequestParam(name = "customFrameRequest", required = false) String customFrameRequest,
            @RequestParam(name = "message", required = false) String message,
            @RequestParam(name = "referenceImage", required = false) MultipartFile referenceImage,
            @RequestParam("customerName") String customerName,
            @RequestParam("address") String address,
            @RequestParam("contactNumber") String contactNumber,
            @RequestParam("paymentMethod") String paymentMethod,
            @RequestParam(name = "paymentReference", required = false) String paymentReference,
            @RequestParam(name = "termsConfirmed", defaultValue = "false") boolean termsConfirmed,
            @RequestParam(name = "receipt", required = false) MultipartFile receipt,
            HttpSession session) throws IOException {
        RequesterInfo requesterInfo = getRequesterInfo(session);
        if (requesterInfo.customerId() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ApiError("Sign in to save this custom order to your customer account."));
        }
        String orderError = validate(length, width, unit, frame, customFrameRequest, message, referenceImage);
        if (orderError != null) {
            return ResponseEntity.badRequest().body(new ApiError(orderError));
        }
        String checkoutError = validateCheckout(
                paymentMethod,
                paymentReference,
                termsConfirmed,
                receipt,
                false,
                "Confirm the demo checkout details before continuing.");
        if (checkoutError != null) {
            return ResponseEntity.badRequest().body(new ApiError(checkoutError));
        }

        Instant submittedAt = Instant.now();
        MirrorPriceCalculator.PriceQuote quote = MirrorPriceCalculator.calculateDefaultQuote();
        String orderId = insertCustomOrder(
                length,
                width,
                unit,
                frame,
                normalize(customFrameRequest),
                normalize(message),
                referenceImage == null || referenceImage.isEmpty() ? null : referenceImage.getOriginalFilename(),
                referenceImage == null || referenceImage.isEmpty() ? null : referenceImage.getBytes(),
                submittedAt,
                quote,
                requesterInfo);
        insertCheckout(
                orderId,
                customerName,
                address,
                contactNumber,
                paymentMethod,
                paymentReference,
                receipt,
                quote.downpayment(),
                submittedAt,
                "DEMO");
        AuditEvents.append(jdbcTemplate,
                new AuthenticatedActor(DemoAuthController.USER_ROLE, requesterInfo.email()),
                "CUSTOM_ORDER_DEMO_CHECKOUT_SUBMITTED", "CUSTOM_ORDER", orderId,
                Map.of("paymentAmount", quote.downpayment(), "status", "DEMO_SUBMITTED"));

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new CheckoutResponse(orderId, "DEMO_SUBMITTED", quote.total(), quote.downpayment()));
    }

    private String insertCustomOrder(
            BigDecimal length,
            BigDecimal width,
            String unit,
            String frame,
            String customFrameRequest,
            String message,
            String imageName,
            byte[] imageBytes,
            Instant submittedAt,
            MirrorPriceCalculator.PriceQuote quote,
            RequesterInfo requesterInfo) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO custom_orders
                    (length, width, unit, frame, custom_frame_request, message, reference_image_name,
                     reference_image, submitted_at, base_price, size_adjustment, frame_adjustment,
                     led_lighting, wall_mounting_hardware, subtotal, delivery_fee, total, downpayment,
                     remaining_balance, requester_name, requester_email, requester_phone, requester_address,
                     requester_customer_id)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            statement.setBigDecimal(1, length);
            statement.setBigDecimal(2, width);
            statement.setString(3, unit);
            statement.setString(4, frame);
            statement.setString(5, normalize(customFrameRequest));
            statement.setString(6, normalize(message));
            statement.setString(7, imageName);
            statement.setBytes(8, imageBytes);
            statement.setTimestamp(9, Timestamp.from(submittedAt));
            statement.setBigDecimal(10, quote.basePrice());
            statement.setBigDecimal(11, quote.sizeAdjustment());
            statement.setBigDecimal(12, quote.frameAdjustment());
            statement.setBigDecimal(13, quote.ledLighting());
            statement.setBigDecimal(14, quote.wallMountingHardware());
            statement.setBigDecimal(15, quote.subtotal());
            statement.setBigDecimal(16, quote.deliveryFee());
            statement.setBigDecimal(17, quote.total());
            statement.setBigDecimal(18, quote.downpayment());
            statement.setBigDecimal(19, quote.remainingBalance());
            statement.setString(20, requesterInfo.name());
            statement.setString(21, requesterInfo.email());
            statement.setString(22, requesterInfo.phone());
            statement.setString(23, requesterInfo.address());
            if (requesterInfo.customerId() == null) {
                statement.setNull(24, java.sql.Types.BIGINT);
            } else {
                statement.setLong(24, requesterInfo.customerId());
            }
            return statement;
        }, keyHolder);
        Number generatedId = keyHolder.getKey();
        if (generatedId == null) {
            throw new IllegalStateException("MySQL did not return an ID for the custom order.");
        }

        String orderId = "CM-" + String.format(Locale.ROOT, "%06d", generatedId.longValue());
        jdbcTemplate.update("UPDATE custom_orders SET order_id = ? WHERE id = ?", orderId, generatedId.longValue());
        if (requesterInfo.customerId() != null && requesterInfo.email() != null) {
            AuditEvents.append(jdbcTemplate,
                    new AuthenticatedActor(DemoAuthController.USER_ROLE, requesterInfo.email()),
                    "CUSTOM_ORDER_CREATED", "CUSTOM_ORDER", orderId,
                    Map.of("frame", frame, "length", length, "width", width, "unit", unit));
        }
        return orderId;
    }

    private RequesterInfo getRequesterInfo(HttpSession session) {
        if (session == null
                || !DemoAuthController.USER_ROLE.equals(
                        session.getAttribute(DemoAuthController.SESSION_ROLE))) {
            return RequesterInfo.empty();
        }
        Object customerId = session.getAttribute(DemoAuthController.SESSION_CUSTOMER_ID);
        if (!(customerId instanceof Number id)) {
            return RequesterInfo.empty();
        }
        List<RequesterInfo> profiles = jdbcTemplate.query("""
                SELECT full_name, email, phone, address
                FROM customer_accounts
                WHERE id = ?
                """, (result, rowNumber) -> new RequesterInfo(
                result.getString("full_name"),
                result.getString("email"),
                result.getString("phone"),
                result.getString("address"),
                id.longValue()), id.longValue());
        return profiles.isEmpty() ? RequesterInfo.empty() : profiles.getFirst();
    }

    private boolean orderBelongsToCustomer(String orderId, long customerId) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM custom_orders
                WHERE order_id = ? AND requester_customer_id = ?
                """, Integer.class, orderId, customerId);
        return count != null && count > 0;
    }

    private PublishedQuote getPublishedQuote(String orderId) {
        List<PublishedQuote> quotes = jdbcTemplate.query("""
                SELECT quote_total, quote_sent_at
                FROM custom_orders
                WHERE order_id = ?
                """, (result, rowNumber) -> {
            BigDecimal total = result.getBigDecimal("quote_total");
            Timestamp quoteSentAt = result.getTimestamp("quote_sent_at");
            return total == null || quoteSentAt == null
                    ? null
                    : new PublishedQuote(total, MirrorPriceCalculator.calculateDownpayment(total));
        }, orderId);
        return quotes.isEmpty() ? null : quotes.getFirst();
    }

    private boolean checkoutExists(String orderId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM custom_order_checkouts WHERE order_id = ?",
                Integer.class,
                orderId);
        return count != null && count > 0;
    }

    private void insertCheckout(
            String orderId,
            String customerName,
            String address,
            String contactNumber,
            String paymentMethod,
            String paymentReference,
            MultipartFile receipt,
            BigDecimal paymentAmount,
            Instant submittedAt,
            String submissionType) throws IOException {
        String receiptName = null;
        byte[] receiptBytes = null;
        if (receipt != null && !receipt.isEmpty()) {
            receiptName = receipt.getOriginalFilename();
            receiptBytes = receipt.getBytes();
        }
        jdbcTemplate.update("""
                INSERT INTO custom_order_checkouts
                (order_id, customer_name, address, contact_number, payment_method,
                 payment_reference, receipt_name, receipt, payment_amount, payment_status, submission_type, submitted_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'SUBMITTED', ?, ?)
                """,
                orderId,
                normalize(customerName),
                normalize(address),
                normalize(contactNumber),
                paymentMethod,
                normalize(paymentReference),
                receiptName,
                receiptBytes,
                paymentAmount,
                submissionType,
                Timestamp.from(submittedAt));
    }

    private String validate(
            BigDecimal length,
            BigDecimal width,
            String unit,
            String frame,
            String customFrameRequest,
            String message,
            MultipartFile referenceImage) {
        if (!"in".equals(unit) && !"cm".equals(unit)) {
            return "Choose inches or centimeters for the dimensions.";
        }
        BigDecimal maxDimension = "in".equals(unit)
                ? MAX_DIMENSION_INCHES
                : MAX_DIMENSION_CENTIMETERS;
        if (!isValidDimension(length, maxDimension) || !isValidDimension(width, maxDimension)) {
            return "Enter a length and width within the supported range.";
        }
        if (!ALLOWED_FRAMES.contains(frame)) {
            return "Select a supported frame material.";
        }
        if ("Custom (Request)".equals(frame)
                && (isBlank(customFrameRequest) || customFrameRequest.trim().length() > 200)) {
            return "Describe your custom frame request in 1 to 200 characters.";
        }
        if (message != null && message.length() > 500) {
            return "Your message must be 500 characters or fewer.";
        }
        if (referenceImage != null && !referenceImage.isEmpty()) {
            if (referenceImage.getSize() > MAX_REFERENCE_IMAGE_BYTES) {
                return "The reference image must be 10MB or smaller.";
            }
            String filename = referenceImage.getOriginalFilename();
            if (filename == null || !filename.toLowerCase(Locale.ROOT).matches(".*\\.(jpe?g|png)$")) {
                return "Choose a JPG or PNG reference image.";
            }
            String contentType = referenceImage.getContentType();
            if (contentType != null && !contentType.isBlank() && !isAcceptedImageType(contentType)) {
                return "Choose a JPG or PNG reference image.";
            }
        }
        return null;
    }

    private boolean isAcceptedImageType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return true;
        }
        String normalized = contentType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
        return MediaType.IMAGE_JPEG_VALUE.equals(normalized)
                || MediaType.IMAGE_PNG_VALUE.equals(normalized)
                || "image/jpg".equals(normalized);
    }

    private boolean isAcceptedReceiptType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return true;
        }
        String normalized = contentType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
        return Set.of(MediaType.IMAGE_JPEG_VALUE, MediaType.IMAGE_PNG_VALUE,
                MediaType.APPLICATION_PDF_VALUE, "image/jpg").contains(normalized);
    }

    private boolean isValidDimension(BigDecimal value, BigDecimal maximum) {
        return value != null
                && value.compareTo(MIN_DIMENSION) >= 0
                && value.compareTo(maximum) <= 0;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private String validateCheckout(
            String paymentMethod,
            String paymentReference,
            boolean termsConfirmed,
            MultipartFile receipt,
            boolean receiptRequired,
            String termsMessage) {
        if (!Set.of("GCash", "Maya", "GoTyme").contains(paymentMethod)) {
            return "Select a supported payment method.";
        }
        if (paymentReference != null && paymentReference.length() > 100) {
            return "The payment reference must be 100 characters or fewer.";
        }
        if (receiptRequired && (receipt == null || receipt.isEmpty())) {
            return "Upload your payment proof or e-receipt before submitting.";
        }
        if (receipt != null && !receipt.isEmpty()) {
            if (receipt.getSize() > MAX_REFERENCE_IMAGE_BYTES) {
                return "The receipt must be 10MB or smaller.";
            }
            String filename = receipt.getOriginalFilename();
            if (filename == null || !filename.toLowerCase(Locale.ROOT).matches(".*\\.(jpe?g|png|pdf)$")) {
                return "Choose a JPG, PNG, or PDF receipt.";
            }
            String contentType = receipt.getContentType();
            if (contentType != null && !contentType.isBlank() && !isAcceptedReceiptType(contentType)) {
                return "Choose a JPG, PNG, or PDF receipt.";
            }
        }
        if (!termsConfirmed) {
            return termsMessage;
        }
        return null;
    }

    public record CustomOrderResponse(
            String orderId,
            String status,
            MirrorPriceCalculator.PriceQuote quote) {
    }

    public record CheckoutResponse(String orderId, String status, BigDecimal total, BigDecimal paymentAmount) {
    }

    public record ApiError(String message) {
    }

    private record RequesterInfo(String name, String email, String phone, String address, Long customerId) {

        static RequesterInfo empty() {
            return new RequesterInfo("", "", "", "", null);
        }
    }

    private record PublishedQuote(BigDecimal total, BigDecimal downpayment) {
    }
}

// Mirror order price calculator
final class MirrorPriceCalculator {

    private static final BigDecimal BASE_PRICE = new BigDecimal("6120.00");
    private static final BigDecimal SIZE_ADJUSTMENT = new BigDecimal("1000.00");
    private static final BigDecimal FRAME_ADJUSTMENT = new BigDecimal("0.00");
    private static final BigDecimal LED_LIGHTING = new BigDecimal("1200.00");
    private static final BigDecimal WALL_MOUNTING_HARDWARE = new BigDecimal("300.00");
    private static final BigDecimal DELIVERY_FEE = new BigDecimal("150.00");
    private static final BigDecimal DOWNPAYMENT_PERCENT = new BigDecimal("0.40");

    private MirrorPriceCalculator() {
    }

    static PriceQuote calculateDefaultQuote() {
        BigDecimal subtotal = BASE_PRICE
                .add(SIZE_ADJUSTMENT)
                .add(FRAME_ADJUSTMENT)
                .add(LED_LIGHTING)
                .add(WALL_MOUNTING_HARDWARE)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal total = subtotal.add(DELIVERY_FEE).setScale(2, RoundingMode.HALF_UP);
        BigDecimal downpayment = total.multiply(DOWNPAYMENT_PERCENT)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal remainingBalance = total.subtract(downpayment).setScale(2, RoundingMode.HALF_UP);

        return new PriceQuote(
                BASE_PRICE,
                SIZE_ADJUSTMENT,
                FRAME_ADJUSTMENT,
                LED_LIGHTING,
                WALL_MOUNTING_HARDWARE,
                subtotal,
                DELIVERY_FEE,
                total,
                downpayment,
                remainingBalance);
    }

    static BigDecimal calculateDownpayment(BigDecimal total) {
        return total.multiply(DOWNPAYMENT_PERCENT).setScale(2, RoundingMode.HALF_UP);
    }

    record PriceQuote(
            BigDecimal basePrice,
            BigDecimal sizeAdjustment,
            BigDecimal frameAdjustment,
            BigDecimal ledLighting,
            BigDecimal wallMountingHardware,
            BigDecimal subtotal,
            BigDecimal deliveryFee,
            BigDecimal total,
            BigDecimal downpayment,
            BigDecimal remainingBalance) {
    }
}
