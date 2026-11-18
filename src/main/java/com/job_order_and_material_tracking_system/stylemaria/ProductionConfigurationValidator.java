package com.job_order_and_material_tracking_system.stylemaria;

import java.net.URI;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("prod")
class ProductionConfigurationValidator implements ApplicationRunner {

    private final String publicUrl;
    private final String adminEmail;
    private final String adminPassword;
    private final String smtpUsername;
    private final String smtpPassword;
    private final String mailFrom;

    ProductionConfigurationValidator(
            @Value("${app.public-url}") String publicUrl,
            @Value("${app.admin.email}") String adminEmail,
            @Value("${app.admin.password}") String adminPassword,
            @Value("${spring.mail.username}") String smtpUsername,
            @Value("${spring.mail.password}") String smtpPassword,
            @Value("${app.mail.from}") String mailFrom) {
        this.publicUrl = publicUrl;
        this.adminEmail = adminEmail;
        this.adminPassword = adminPassword;
        this.smtpUsername = smtpUsername;
        this.smtpPassword = smtpPassword;
        this.mailFrom = mailFrom;
    }

    @Override
    public void run(ApplicationArguments arguments) {
        URI uri;
        try {
            uri = URI.create(publicUrl);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("APP_PUBLIC_URL must be a valid HTTPS URL.", exception);
        }
        if (!"https".equalsIgnoreCase(uri.getScheme())
                || uri.getHost() == null
                || uri.getUserInfo() != null
                || uri.getQuery() != null
                || uri.getFragment() != null) {
            throw new IllegalStateException("APP_PUBLIC_URL must be the public HTTPS origin or base path.");
        }
        if (adminEmail.isBlank() || adminPassword.length() < 16) {
            throw new IllegalStateException(
                    "Configure ADMIN_EMAIL and an ADMIN_PASSWORD of at least 16 characters.");
        }
        if (smtpUsername.isBlank() || smtpPassword.isBlank() || mailFrom.isBlank()) {
            throw new IllegalStateException(
                    "Configure SMTP_USERNAME, SMTP_PASSWORD, and MAIL_FROM before production startup.");
        }
    }
}
