package com.job_order_and_material_tracking_system.stylemaria;

import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

record AuthenticatedActor(String role, String email) {

    static AuthenticatedActor from(HttpSession session) {
        if (session == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "unauthenticated", "Sign in to continue.");
        }
        Object role = session.getAttribute(DemoAuthController.SESSION_ROLE);
        Object email = session.getAttribute(DemoAuthController.SESSION_EMAIL);
        if (!(role instanceof String roleValue)
                || !(email instanceof String emailValue)
                || emailValue.isBlank()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "unauthenticated", "Sign in to continue.");
        }
        return new AuthenticatedActor(roleValue, emailValue);
    }
}

final class PasswordSecurity {

    private static final int PASSWORD_ITERATIONS = 210_000;
    private static final int PASSWORD_KEY_LENGTH = 256;
    private static final int PASSWORD_SALT_BYTES = 16;
    private static final int INVITE_TOKEN_BYTES = 32;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private PasswordSecurity() {
    }

    static byte[] newSalt() {
        byte[] salt = new byte[PASSWORD_SALT_BYTES];
        SECURE_RANDOM.nextBytes(salt);
        return salt;
    }

    static String encodeSalt(byte[] salt) {
        return Base64.getEncoder().encodeToString(salt);
    }

    static String hashPassword(String password, byte[] salt) {
        PBEKeySpec spec = new PBEKeySpec(
                password.toCharArray(), salt, PASSWORD_ITERATIONS, PASSWORD_KEY_LENGTH);
        try {
            byte[] hash = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                    .generateSecret(spec)
                    .getEncoded();
            return Base64.getEncoder().encodeToString(hash);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Password hashing is unavailable.", exception);
        } finally {
            spec.clearPassword();
        }
    }

    static boolean passwordMatches(String password, String saltValue, String hashValue) {
        if (password == null || saltValue == null || hashValue == null) {
            return false;
        }
        try {
            byte[] salt = Base64.getDecoder().decode(saltValue);
            byte[] expectedHash = Base64.getDecoder().decode(hashValue);
            byte[] actualHash = Base64.getDecoder().decode(hashPassword(password, salt));
            return MessageDigest.isEqual(expectedHash, actualHash);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    static String newInviteToken() {
        byte[] token = new byte[INVITE_TOKEN_BYTES];
        SECURE_RANDOM.nextBytes(token);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(token);
    }

    static String hashToken(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Invitation token hashing is unavailable.", exception);
        }
    }
}

final class AuditEvents {

    private AuditEvents() {
    }

    static void append(
            JdbcTemplate jdbcTemplate,
            AuthenticatedActor actor,
            String eventType,
            String entityType,
            String entityId,
            Map<String, ?> details) {
        String detailsJson = jsonObject(details);
        jdbcTemplate.update("""
                INSERT INTO audit_events
                    (event_type, entity_type, entity_id, actor_email, actor_role, occurred_at, details_json)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                eventType,
                entityType,
                entityId,
                actor.email(),
                actor.role(),
                Timestamp.from(Instant.now()),
                detailsJson);
    }

    private static String jsonObject(Map<String, ?> details) {
        StringBuilder json = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, ?> entry : details.entrySet()) {
            if (!first) {
                json.append(',');
            }
            first = false;
            json.append(quote(entry.getKey())).append(':').append(jsonValue(entry.getValue()));
        }
        return json.append('}').toString();
    }

    private static String jsonValue(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof Number || value instanceof Boolean) {
            return value.toString();
        }
        if (value instanceof Iterable<?> values) {
            StringBuilder json = new StringBuilder("[");
            boolean first = true;
            for (Object item : values) {
                if (!first) {
                    json.append(',');
                }
                first = false;
                json.append(jsonValue(item));
            }
            return json.append(']').toString();
        }
        return quote(value.toString());
    }

    private static String quote(String value) {
        StringBuilder json = new StringBuilder("\"");
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '"' -> json.append("\\\"");
                case '\\' -> json.append("\\\\");
                case '\b' -> json.append("\\b");
                case '\f' -> json.append("\\f");
                case '\n' -> json.append("\\n");
                case '\r' -> json.append("\\r");
                case '\t' -> json.append("\\t");
                default -> {
                    if (character < 0x20) {
                        json.append(String.format("\\u%04x", (int) character));
                    } else {
                        json.append(character);
                    }
                }
            }
        }
        return json.append('"').toString();
    }
}

class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    ApiException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    HttpStatus status() {
        return status;
    }

    String code() {
        return code;
    }
}

@RestControllerAdvice
class ApiExceptionHandler {

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ApiErrorResponse> handleApiException(ApiException exception) {
        return ResponseEntity.status(exception.status())
                .body(new ApiErrorResponse(exception.code(), exception.getMessage()));
    }

    @ExceptionHandler(org.springframework.dao.DuplicateKeyException.class)
    ResponseEntity<ApiErrorResponse> handleDuplicateKey() {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiErrorResponse("conflict", "A record with the supplied unique value already exists."));
    }
}

record ApiErrorResponse(String error, String message) {
}
