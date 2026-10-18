package com.stylemaria.controller;

import com.stylemaria.model.CartItem;
import com.stylemaria.model.User;
import com.stylemaria.repository.CartItemRepository;
import com.stylemaria.util.AuthHelper;
import com.stylemaria.util.Pricing;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.*;

@RestController
@RequestMapping("/api/cart")
public class CartController {

    private final CartItemRepository cartRepo;
    private final AuthHelper authHelper;

    public CartController(CartItemRepository c, AuthHelper a) { this.cartRepo = c; this.authHelper = a; }

    @GetMapping
    public ResponseEntity<?> list(HttpServletRequest req) {
        User u = authHelper.requireUser(req);
        List<CartItem> items = cartRepo.findByUserIdOrderByCreatedAtDesc(u.getId());
        return ResponseEntity.ok(summary(items));
    }

    @PostMapping
    public ResponseEntity<?> add(@RequestBody Map<String, Object> body, HttpServletRequest req) {
        User u = authHelper.requireUser(req);
        try {
            double length = Double.parseDouble(body.get("length").toString());
            double width = Double.parseDouble(body.get("width").toString());
            String material = (String) body.getOrDefault("frameMaterial", "Solid Wood");
            String refImg = (String) body.getOrDefault("referenceImage", null);
            int qty = body.get("quantity") == null ? 1 : Integer.parseInt(body.get("quantity").toString());
            if (length <= 0 || width <= 0 || qty <= 0)
                return ResponseEntity.badRequest().body(Map.of("error", "Invalid dimensions"));

            CartItem c = new CartItem();
            c.setUserId(u.getId());
            c.setLength(length);
            c.setWidth(width);
            c.setFrameMaterial(material);
            c.setReferenceImage(refImg);
            c.setQuantity(qty);
            c.setUnitPrice(Pricing.unitPrice(material, length, width));
            cartRepo.save(c);
            return ResponseEntity.ok(summary(cartRepo.findByUserIdOrderByCreatedAtDesc(u.getId())));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid input"));
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateQty(@PathVariable Long id, @RequestBody Map<String, Object> body, HttpServletRequest req) {
        User u = authHelper.requireUser(req);
        Optional<CartItem> opt = cartRepo.findById(id);
        if (opt.isEmpty() || !opt.get().getUserId().equals(u.getId()))
            return ResponseEntity.status(404).body(Map.of("error", "Not found"));
        int qty = Integer.parseInt(body.get("quantity").toString());
        if (qty <= 0) return ResponseEntity.badRequest().body(Map.of("error", "Qty must be >= 1"));
        CartItem c = opt.get();
        c.setQuantity(qty);
        cartRepo.save(c);
        return ResponseEntity.ok(summary(cartRepo.findByUserIdOrderByCreatedAtDesc(u.getId())));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> remove(@PathVariable Long id, HttpServletRequest req) {
        User u = authHelper.requireUser(req);
        Optional<CartItem> opt = cartRepo.findById(id);
        if (opt.isEmpty() || !opt.get().getUserId().equals(u.getId()))
            return ResponseEntity.status(404).body(Map.of("error", "Not found"));
        cartRepo.deleteById(id);
        return ResponseEntity.ok(summary(cartRepo.findByUserIdOrderByCreatedAtDesc(u.getId())));
    }

    private Map<String, Object> summary(List<CartItem> items) {
        BigDecimal subtotal = BigDecimal.ZERO;
        for (CartItem c : items) subtotal = subtotal.add(c.getUnitPrice().multiply(BigDecimal.valueOf(c.getQuantity())));
        BigDecimal delivery = items.isEmpty() ? BigDecimal.ZERO : new BigDecimal("250.00");
        BigDecimal total = subtotal.add(delivery);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("items", items);
        m.put("subtotal", subtotal);
        m.put("deliveryFee", delivery);
        m.put("total", total);
        return m;
    }
}
