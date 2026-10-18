package com.stylemaria.controller;

import com.stylemaria.model.*;
import com.stylemaria.repository.*;
import com.stylemaria.util.AuthHelper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final OrderRepository orderRepo;
    private final SettingRepository settingRepo;
    private final UserRepository userRepo;
    private final AuthHelper authHelper;

    private static final Set<String> ALLOWED_STATUS = Set.of(
            "PENDING", "IN_PRODUCTION", "SCHEDULED", "COMPLETED", "REJECTED");

    public AdminController(OrderRepository o, SettingRepository s, UserRepository u, AuthHelper a) {
        this.orderRepo = o; this.settingRepo = s; this.userRepo = u; this.authHelper = a;
    }

    @GetMapping("/orders")
    public ResponseEntity<?> allOrders(@RequestParam(defaultValue = "") String q,
                                       @RequestParam(defaultValue = "") String status,
                                       HttpServletRequest req) {
        authHelper.requireAdmin(req);
        String query = q.trim().toLowerCase();
        String st = status.trim().toUpperCase();
        List<Order> list = orderRepo.findAllByOrderByCreatedAtDesc();
        List<Map<String, Object>> out = new ArrayList<>();
        for (Order o : list) {
            if (st.equals("AWAITING_VERIFICATION")) {
                if (!"PENDING".equals(o.getStatus()) || o.getReceiptImage() == null) continue;
            } else if (!st.isEmpty() && !st.equals(o.getStatus())) continue;

            Optional<User> cust = userRepo.findById(o.getUserId());
            String name = cust.map(User::getFullName).orElse("");
            String email = cust.map(User::getEmail).orElse("");
            if (!query.isEmpty() && !(o.getReference().toLowerCase().contains(query)
                    || name.toLowerCase().contains(query) || email.toLowerCase().contains(query))) continue;

            Map<String, Object> m = new LinkedHashMap<>();
            m.put("order", o);
            m.put("customer", Map.of("fullName", name, "email", email));
            out.add(m);
        }
        return ResponseEntity.ok(out);
    }

    @PutMapping("/orders/{reference}/status")
    public ResponseEntity<?> updateStatus(@PathVariable String reference,
                                          @RequestBody Map<String, String> body,
                                          HttpServletRequest req) {
        authHelper.requireAdmin(req);
        String status = body.getOrDefault("status", "").toUpperCase();
        if (!ALLOWED_STATUS.contains(status))
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid status"));
        Optional<Order> opt = orderRepo.findByReference(reference);
        if (opt.isEmpty()) return ResponseEntity.status(404).body(Map.of("error", "Not found"));
        Order o = opt.get();
        o.setStatus(status);
        o.setUpdatedAt(LocalDateTime.now());
        orderRepo.save(o);
        return ResponseEntity.ok(o);
    }

    @GetMapping("/settings/qr")
    public ResponseEntity<?> getQr() {
        String url = settingRepo.findById("qr_image").map(Setting::getValue).orElse("/images/qr-placeholder.svg");
        return ResponseEntity.ok(Map.of("url", url));
    }

    @PutMapping("/settings/qr")
    public ResponseEntity<?> setQr(@RequestBody Map<String, String> body, HttpServletRequest req) {
        authHelper.requireAdmin(req);
        String url = body.get("url");
        if (url == null || url.isBlank())
            return ResponseEntity.badRequest().body(Map.of("error", "url required"));
        Setting s = settingRepo.findById("qr_image").orElseGet(() -> {
            Setting ns = new Setting(); ns.setKey("qr_image"); return ns;
        });
        s.setValue(url);
        settingRepo.save(s);
        return ResponseEntity.ok(Map.of("url", url));
    }
}
