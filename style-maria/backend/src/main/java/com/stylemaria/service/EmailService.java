package com.stylemaria.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stylemaria.model.Order;
import com.stylemaria.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.text.NumberFormat;
import java.util.*;

@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);
    private static final String EMAIL_BASE_URL = "https://integrations.emergentagent.com";

    @Value("${app.email.key:}") private String emailKey;
    @Value("${app.email.from-name}") private String fromName;
    @Value("${app.email.reply-to:}") private String replyTo;

    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();

    // Recipient, subject and body all come from server-side data; never from request input.
    public void sendOrderConfirmation(User user, Order order) {
        if (emailKey == null || emailKey.isBlank()) {
            log.info("Email disabled (app.email.key empty) - skipping confirmation for {}", order.getReference());
            return;
        }
        String subject = "Your " + fromName + " order " + order.getReference() + " is received";
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("to", List.of(user.getEmail()));
        payload.put("subject", subject);
        payload.put("html", buildHtml(user, order));
        payload.put("from_name", fromName);
        if (replyTo != null && !replyTo.isBlank()) payload.put("contact_email", replyTo);

        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(EMAIL_BASE_URL + "/api/v1/email/send"))
                    .header("Content-Type", "application/json")
                    .header("X-Email-Key", emailKey)
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload)))
                    .build();
            http.sendAsync(req, HttpResponse.BodyHandlers.ofString()).thenAccept(res -> {
                if (res.statusCode() >= 300) log.warn("Email send failed {}: {}", res.statusCode(), res.body());
                else log.info("Confirmation email sent for {}", order.getReference());
            });
        } catch (Exception e) {
            log.error("Email send error: {}", e.getMessage());
        }
    }

    private String buildHtml(User user, Order order) {
        NumberFormat php = NumberFormat.getCurrencyInstance(new Locale("en", "PH"));
        String esc = "";
        String name = HtmlUtils.htmlEscape(user.getFullName());
        String ref = HtmlUtils.htmlEscape(order.getReference());
        String address = HtmlUtils.htmlEscape(String.join(", ",
                Objects.toString(order.getStreet(), esc), Objects.toString(order.getCity(), esc),
                Objects.toString(order.getProvince(), esc)));
        String brand = HtmlUtils.htmlEscape(fromName);
        return "<table role=\"presentation\" width=\"100%\" style=\"background:#f8f4ec\"><tr><td style=\"padding:28px;font-family:Georgia,serif;color:#2b2b2b\">"
                + "<h2 style=\"color:#4b5a3a;margin:0 0 12px\">Thank you, " + name + "</h2>"
                + "<p style=\"font-family:Arial,sans-serif\">We received your order and payment receipt. Production starts once your payment is verified.</p>"
                + "<table style=\"font-family:Arial,sans-serif;font-size:14px\">"
                + "<tr><td style=\"padding:4px 16px 4px 0\">Order reference</td><td><strong>" + ref + "</strong></td></tr>"
                + "<tr><td style=\"padding:4px 16px 4px 0\">Subtotal</td><td>" + php.format(order.getSubtotal()) + "</td></tr>"
                + "<tr><td style=\"padding:4px 16px 4px 0\">Delivery fee</td><td>" + php.format(order.getDeliveryFee()) + "</td></tr>"
                + "<tr><td style=\"padding:4px 16px 4px 0\">Total</td><td><strong>" + php.format(order.getTotal()) + "</strong></td></tr>"
                + "<tr><td style=\"padding:4px 16px 4px 0\">Deliver to</td><td>" + address + "</td></tr>"
                + "</table>"
                + "<p style=\"font-family:Arial,sans-serif\">Use your order reference on the Track Order page to follow its progress.</p>"
                + "<p style=\"font-family:Arial,sans-serif;font-size:12px;color:#888\">Sent by " + brand
                + ". We never ask for your password or card details by email.</p>"
                + "</td></tr></table>";
    }
}
