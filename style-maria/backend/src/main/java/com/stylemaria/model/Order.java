package com.stylemaria.model;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Entity
@Table(name = "orders")
public class Order {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 32)
    private String reference;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal subtotal;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal deliveryFee = new BigDecimal("250.00");

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal total;

    // PENDING_PAYMENT | PENDING | IN_PRODUCTION | SCHEDULED | COMPLETED | REJECTED
    @Column(nullable = false, length = 32)
    private String status = "PENDING_PAYMENT";

    @Column(length = 500)
    private String receiptImage;

    private String recipientName;
    private String phone;
    @Column(length = 500)
    private String street;
    private String city;
    private String province;
    private String deliveryArea;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column
    private LocalDateTime updatedAt = LocalDateTime.now();

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @JoinColumn(name = "order_id")
    private List<OrderItem> items = new ArrayList<>();
}
