package com.stylemaria.controller;

import com.stylemaria.model.*;
import com.stylemaria.repository.*;
import com.stylemaria.service.EmailService;
import com.stylemaria.util.AuthHelper;
import com.stylemaria.util.DeliveryFees;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderRepository orderRepo;
    private final CartItemRepository cartRepo;
    private final AuthHelper authHelper;
    private final EmailService emailService;

    public OrderController(OrderRepository o, CartItemRepository c, AuthHelper a, EmailService e) {
        this.orderRepo = o; this.cartRepo = c; this.authHelper = a; this.emailService = e;
    }

    @PostMapping("/checkout")
    @Transactional
    public ResponseEntity<?> checkout(@RequestBody(required = false) Map<String, Object> body, HttpServletRequest req) {
        User u = authHelper.requireUser(req);
        List<CartItem> cart = cartRepo.findByUserIdOrderByCreatedAtDesc(u.getId());
        if (cart.isEmpty()) return ResponseEntity.badRequest().body(Map.of("error", "Cart is empty"));

        Map<String, Object> b = body == null ? Map.of() : body;
        String recipientName = str(b, "recipientName"), phone = str(b, "phone"), street = str(b, "street"),
               city = str(b, "city"), province = str(b, "province"), area = str(b, "deliveryArea");
        BigDecimal fee = DeliveryFees.feeFor(area);
        if (recipientName.isEmpty() || phone.isEmpty() || street.isEmpty() || city.isEmpty() || province.isEmpty())
            return ResponseEntity.badRequest().body(Map.of("error", "Please complete the delivery address"));
        if (fee == null) return ResponseEntity.badRequest().body(Map.of("error", "Please select a delivery area"));

        Order order = new Order();
        order.setRecipientName(recipientName);
        order.setPhone(phone);
        order.setStreet(street);
        order.setCity(city);
        order.setProvince(province);
        order.setDeliveryArea(area);
        order.setUserId(u.getId());
        order.setReference("SM-" + System.currentTimeMillis());
        BigDecimal subtotal = BigDecimal.ZERO;
        for (CartItem c : cart) {
            OrderItem oi = new OrderItem();
            oi.setLength(c.getLength());
            oi.setWidth(c.getWidth());
            oi.setFrameMaterial(c.getFrameMaterial());
            oi.setReferenceImage(c.getReferenceImage());
            oi.setQuantity(c.getQuantity());
            oi.setUnitPrice(c.getUnitPrice());
            order.getItems().add(oi);
            subtotal = subtotal.add(c.getUnitPrice().multiply(BigDecimal.valueOf(c.getQuantity())));
        }
        order.setSubtotal(subtotal);
        order.setDeliveryFee(fee);
        order.setTotal(subtotal.add(order.getDeliveryFee()));
        order.setStatus("PENDING_PAYMENT");
        orderRepo.save(order);

        return ResponseEntity.ok(order);
    }

    @PostMapping("/{reference}/payment")
    public ResponseEntity<?> submitPayment(@PathVariable String reference,
                                           @RequestBody Map<String, String> body,
                                           HttpServletRequest req) {
        User u = authHelper.requireUser(req);
        Optional<Order> opt = orderRepo.findByReference(reference);
        if (opt.isEmpty() || !opt.get().getUserId().equals(u.getId()))
            return ResponseEntity.status(404).body(Map.of("error", "Order not found"));
        Order o = opt.get();
        String receipt = body.get("receiptImage");
        if (receipt == null || receipt.isBlank())
            return ResponseEntity.badRequest().body(Map.of("error", "Receipt required"));
        o.setReceiptImage(receipt);
        o.setStatus("PENDING");
        o.setUpdatedAt(LocalDateTime.now());
        orderRepo.save(o);

        // clear cart
        List<CartItem> items = cartRepo.findByUserIdOrderByCreatedAtDesc(u.getId());
        cartRepo.deleteAll(items);

        emailService.sendOrderConfirmation(u, o);
        return ResponseEntity.ok(o);
    }

    private static String str(Map<String, Object> m, String k) {
        Object v = m.get(k);
        return v == null ? "" : v.toString().trim();
    }

    @GetMapping("/mine")
    public ResponseEntity<?> myOrders(HttpServletRequest req) {
        User u = authHelper.requireUser(req);
        return ResponseEntity.ok(orderRepo.findByUserIdOrderByCreatedAtDesc(u.getId()));
    }

    @GetMapping("/track/{reference}")
    public ResponseEntity<?> track(@PathVariable String reference) {
        return orderRepo.findByReference(reference)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElse(ResponseEntity.status(404).body(Map.of("error", "Order not found")));
    }
}
