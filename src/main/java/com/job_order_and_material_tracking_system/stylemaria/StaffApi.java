package com.job_order_and_material_tracking_system.stylemaria;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpSession;

@RestController
class StaffController {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final Logger LOGGER = LoggerFactory.getLogger(StaffController.class);
    private static final long INVITE_LIFETIME_HOURS = 24;

    private final JdbcTemplate jdbcTemplate;
    private final StaffInvitationService invitationService;
    private final JavaMailSender mailSender;
    private final String adminEmail;
    private final String publicUrl;
    private final String mailFrom;

    StaffController(
            JdbcTemplate jdbcTemplate,
            StaffInvitationService invitationService,
            JavaMailSender mailSender,
            @Value("${app.admin.email:}") String adminEmail,
            @Value("${app.public-url:http://localhost:8080}") String publicUrl,
            @Value("${app.mail.from:no-reply@stylemaria.com}") String mailFrom) {
        this.jdbcTemplate = jdbcTemplate;
        this.invitationService = invitationService;
        this.mailSender = mailSender;
        this.adminEmail = adminEmail == null ? "" : adminEmail.trim();
        this.publicUrl = publicUrl == null ? "" : publicUrl.replaceAll("/+$", "");
        this.mailFrom = mailFrom;
    }

    @GetMapping("/api/v1/admin/staff")
    public List<StaffView> listStaff() {
        return jdbcTemplate.query("""
                SELECT id, email, display_name, active, created_at, updated_at
                FROM staff_accounts
                ORDER BY display_name, email
                """, (result, row) -> new StaffView(
                result.getLong("id"),
                result.getString("email"),
                result.getString("display_name"),
                result.getBoolean("active"),
                result.getTimestamp("created_at"),
                result.getTimestamp("updated_at")));
    }

    @PostMapping("/api/v1/admin/staff/invitations")
    public ResponseEntity<?> inviteStaff(@RequestBody StaffInviteRequest input, HttpSession session) {
        AuthenticatedActor actor = AuthenticatedActor.from(session);
        if (input == null || input.name() == null || input.name().isBlank()
                || input.name().trim().length() > 100) {
            throw badRequest("Staff name must contain 1 to 100 characters.");
        }
        String email = normalizeEmail(input.email());
        if (!adminEmail.isBlank() && adminEmail.equalsIgnoreCase(email)) {
            throw new ApiException(HttpStatus.CONFLICT, "account_email_reserved",
                    "That email address is assigned to the Admin account.");
        }
        StaffInvitationService.IssuedInvitation invitation =
                invitationService.issue(email, input.name().trim(), actor);
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(mailFrom);
        message.setTo(invitation.email());
        message.setSubject("Your Style Maria staff invitation");
        message.setText("""
                Hello %s,

                You have been invited to join Style Maria as a staff member.
                Accept this invitation within 24 hours to set your password:

                %s/staff-invitation.html?token=%s

                If you were not expecting this invitation, you can ignore this message.
                """.formatted(invitation.displayName(), publicUrl, invitation.token()));
        try {
            mailSender.send(message);
        } catch (MailException exception) {
            invitationService.revokeAfterDeliveryFailure(invitation.id(), actor);
            LOGGER.warn("Unable to deliver a staff invitation to the requested mailbox.", exception);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(new ApiErrorResponse("mail_unavailable",
                            "The invitation could not be delivered. The pending invitation was revoked."));
        }
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(new StaffInviteResponse(invitation.email(), "INVITATION_SENT", invitation.expiresAt()));
    }

    @PostMapping("/api/v1/staff/invitations/accept")
    public ResponseEntity<?> acceptInvitation(@RequestBody StaffInvitationAcceptance input) {
        if (input == null || input.token() == null
                || !input.token().matches("^[A-Za-z0-9_-]{20,100}$")
                || input.newPassword() == null
                || input.newPassword().length() < 12
                || input.newPassword().length() > 128) {
            throw badRequest("Provide a valid invitation token and a 12 to 128 character password.");
        }
        AcceptedStaff accepted = invitationService.accept(input.token(), input.newPassword());
        return ResponseEntity.status(HttpStatus.CREATED).body(accepted);
    }

    @PatchMapping("/api/v1/admin/staff/{staffId}")
    public StaffView updateStaffStatus(
            @PathVariable long staffId,
            @RequestBody StaffStatusRequest input,
            HttpSession session) {
        AuthenticatedActor actor = AuthenticatedActor.from(session);
        if (input == null || input.active() == null) {
            throw badRequest("An active status is required.");
        }
        return invitationService.setActive(staffId, input.active(), actor);
    }

    private String normalizeEmail(String value) {
        String email = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if (email.length() > 254 || !EMAIL_PATTERN.matcher(email).matches()) {
            throw badRequest("Enter a valid email address up to 254 characters.");
        }
        return email;
    }

    private ApiException badRequest(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, "invalid_request", message);
    }

    public record StaffView(
            long id,
            String email,
            String name,
            boolean active,
            Timestamp createdAt,
            Timestamp updatedAt) {
    }

    public record StaffInviteRequest(String name, String email) {
    }

    public record StaffInviteResponse(String email, String status, Instant expiresAt) {
    }

    public record StaffInvitationAcceptance(String token, String newPassword) {
    }

    public record StaffStatusRequest(Boolean active) {
    }

    public record AcceptedStaff(long id, String email, String name, String role) {
    }
}

@Service
class StaffInvitationService {

    private static final long INVITE_LIFETIME_HOURS = 24;
    private final JdbcTemplate jdbcTemplate;

    StaffInvitationService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public IssuedInvitation issue(String email, String displayName, AuthenticatedActor actor) {
        Integer accountCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM staff_accounts WHERE email = ?", Integer.class, email);
        if (accountCount != null && accountCount > 0) {
            throw new ApiException(HttpStatus.CONFLICT, "staff_exists",
                    "A staff account already exists for this email address.");
        }
        Integer customerCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM customer_accounts WHERE email = ?", Integer.class, email);
        if (customerCount != null && customerCount > 0) {
            throw new ApiException(HttpStatus.CONFLICT, "account_email_reserved",
                    "That email address is already assigned to a customer account.");
        }
        Instant now = Instant.now();
        jdbcTemplate.update("""
                UPDATE staff_invites
                SET revoked_at = ?
                WHERE email = ? AND accepted_at IS NULL AND revoked_at IS NULL
                """, Timestamp.from(now), email);
        String token = PasswordSecurity.newInviteToken();
        Instant expiresAt = now.plusSeconds(INVITE_LIFETIME_HOURS * 60 * 60);
        long id = insertInvitation(email, displayName, PasswordSecurity.hashToken(token), expiresAt, now, actor.email());
        AuditEvents.append(jdbcTemplate, actor, "STAFF_INVITATION_ISSUED", "STAFF_INVITATION",
                Long.toString(id), Map.of("email", email, "expiresAt", expiresAt.toString()));
        return new IssuedInvitation(id, email, displayName, token, expiresAt);
    }

    @Transactional
    public void revokeAfterDeliveryFailure(long inviteId, AuthenticatedActor actor) {
        Instant now = Instant.now();
        int updated = jdbcTemplate.update("""
                UPDATE staff_invites
                SET revoked_at = ?
                WHERE id = ? AND accepted_at IS NULL AND revoked_at IS NULL
                """, Timestamp.from(now), inviteId);
        if (updated == 1) {
            AuditEvents.append(jdbcTemplate, actor, "STAFF_INVITATION_REVOKED", "STAFF_INVITATION",
                    Long.toString(inviteId), Map.of("reason", "email_delivery_failed"));
        }
    }

    @Transactional
    public StaffController.AcceptedStaff accept(String token, String password) {
        String tokenHash = PasswordSecurity.hashToken(token);
        List<InviteRecord> invitations = jdbcTemplate.query("""
                SELECT id, email, display_name, expires_at, created_by_email, accepted_at, revoked_at
                FROM staff_invites
                WHERE token_hash = ?
                FOR UPDATE
                """, (result, row) -> new InviteRecord(
                result.getLong("id"),
                result.getString("email"),
                result.getString("display_name"),
                result.getTimestamp("expires_at").toInstant(),
                result.getString("created_by_email"),
                result.getTimestamp("accepted_at"),
                result.getTimestamp("revoked_at")), tokenHash);
        if (invitations.isEmpty()) {
            throw new ApiException(HttpStatus.GONE, "invite_invalid", "The invitation is invalid, expired, or already used.");
        }
        InviteRecord invitation = invitations.getFirst();
        if (invitation.acceptedAt() != null || invitation.revokedAt() != null
                || !invitation.expiresAt().isAfter(Instant.now())) {
            throw new ApiException(HttpStatus.GONE, "invite_invalid", "The invitation is invalid, expired, or already used.");
        }
        Integer existing = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM staff_accounts WHERE email = ?", Integer.class, invitation.email());
        if (existing != null && existing > 0) {
            throw new ApiException(HttpStatus.CONFLICT, "staff_exists",
                    "A staff account already exists for this email address.");
        }
        byte[] salt = PasswordSecurity.newSalt();
        String saltValue = PasswordSecurity.encodeSalt(salt);
        String passwordHash = PasswordSecurity.hashPassword(password, salt);
        Instant now = Instant.now();
        long staffId = insertStaff(
                invitation.email(), invitation.displayName(), saltValue, passwordHash,
                Timestamp.from(now), invitation.createdByEmail());
        int consumed = jdbcTemplate.update("""
                UPDATE staff_invites SET accepted_at = ?
                WHERE id = ? AND accepted_at IS NULL AND revoked_at IS NULL AND expires_at > ?
                """, Timestamp.from(now), invitation.id(), Timestamp.from(now));
        if (consumed != 1) {
            throw new ApiException(HttpStatus.GONE, "invite_invalid",
                    "The invitation is invalid, expired, or already used.");
        }
        AuthenticatedActor invitee = new AuthenticatedActor(
                DemoAuthController.STAFF_ROLE, invitation.email());
        AuditEvents.append(jdbcTemplate, invitee, "STAFF_INVITATION_ACCEPTED", "STAFF_ACCOUNT",
                Long.toString(staffId), Map.of("email", invitation.email()));
        return new StaffController.AcceptedStaff(
                staffId, invitation.email(), invitation.displayName(), DemoAuthController.STAFF_ROLE);
    }

    @Transactional
    public StaffController.StaffView setActive(long staffId, boolean active, AuthenticatedActor actor) {
        StaffController.StaffView before = staff(staffId);
        if (before.active() != active) {
            jdbcTemplate.update("""
                    UPDATE staff_accounts SET active = ?, updated_at = ?
                    WHERE id = ?
                    """, active, Timestamp.from(Instant.now()), staffId);
            AuditEvents.append(jdbcTemplate, actor, "STAFF_ACCOUNT_STATUS_CHANGED", "STAFF_ACCOUNT",
                    Long.toString(staffId), Map.of("active", active));
        }
        return staff(staffId);
    }

    private long insertInvitation(
            String email,
            String displayName,
            String tokenHash,
            Instant expiresAt,
            Instant createdAt,
            String createdByEmail) {
        KeyHolderWithId key = new KeyHolderWithId();
        jdbcTemplate.update(connection -> {
            var statement = connection.prepareStatement("""
                    INSERT INTO staff_invites
                        (email, display_name, token_hash, expires_at, created_at, created_by_email)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """, java.sql.Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, email);
            statement.setString(2, displayName);
            statement.setString(3, tokenHash);
            statement.setTimestamp(4, Timestamp.from(expiresAt));
            statement.setTimestamp(5, Timestamp.from(createdAt));
            statement.setString(6, createdByEmail);
            return statement;
        }, key.holder());
        return key.id();
    }

    private long insertStaff(
            String email,
            String displayName,
            String salt,
            String passwordHash,
            Timestamp now,
            String createdByEmail) {
        KeyHolderWithId key = new KeyHolderWithId();
        jdbcTemplate.update(connection -> {
            var statement = connection.prepareStatement("""
                    INSERT INTO staff_accounts
                        (email, display_name, password_salt, password_hash, active, created_at, updated_at, created_by_email)
                    VALUES (?, ?, ?, ?, TRUE, ?, ?, ?)
                    """, java.sql.Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, email);
            statement.setString(2, displayName);
            statement.setString(3, salt);
            statement.setString(4, passwordHash);
            statement.setTimestamp(5, now);
            statement.setTimestamp(6, now);
            statement.setString(7, createdByEmail);
            return statement;
        }, key.holder());
        return key.id();
    }

    private StaffController.StaffView staff(long staffId) {
        try {
            return jdbcTemplate.queryForObject("""
                    SELECT id, email, display_name, active, created_at, updated_at
                    FROM staff_accounts WHERE id = ?
                    """, (result, row) -> new StaffController.StaffView(
                    result.getLong("id"),
                    result.getString("email"),
                    result.getString("display_name"),
                    result.getBoolean("active"),
                    result.getTimestamp("created_at"),
                    result.getTimestamp("updated_at")), staffId);
        } catch (EmptyResultDataAccessException exception) {
            throw new ApiException(HttpStatus.NOT_FOUND, "not_found", "Staff account was not found.");
        }
    }

    public record IssuedInvitation(long id, String email, String displayName, String token, Instant expiresAt) {
    }

    private record InviteRecord(
            long id,
            String email,
            String displayName,
            Instant expiresAt,
            String createdByEmail,
            Timestamp acceptedAt,
            Timestamp revokedAt) {
    }

    private static final class KeyHolderWithId {
        private final org.springframework.jdbc.support.GeneratedKeyHolder holder =
                new org.springframework.jdbc.support.GeneratedKeyHolder();

        org.springframework.jdbc.support.KeyHolder holder() {
            return holder;
        }

        long id() {
            Number key = holder.getKey();
            if (key == null) {
                throw new IllegalStateException("The database did not return an ID for the new record.");
            }
            return key.longValue();
        }
    }
}

@Component
class StaffInviteSchemaMigration implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    StaffInviteSchemaMigration(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments arguments) {
        Boolean exists = jdbcTemplate.execute((ConnectionCallback<Boolean>) connection -> {
            var metadata = connection.getMetaData();
            for (String table : List.of("staff_invites", "STAFF_INVITES")) {
                for (String column : List.of("display_name", "DISPLAY_NAME")) {
                    try (var result = metadata.getColumns(connection.getCatalog(), null, table, column)) {
                        if (result.next()) {
                            return true;
                        }
                    }
                }
            }
            return false;
        });
        if (!Boolean.TRUE.equals(exists)) {
            jdbcTemplate.execute("ALTER TABLE staff_invites ADD COLUMN display_name VARCHAR(100) NOT NULL DEFAULT ''");
        }
    }
}
