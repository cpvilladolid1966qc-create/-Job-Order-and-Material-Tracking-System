package com.stylemaria.model;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "cart_items")
public class CartItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Double length;

    @Column(nullable = false)
    private Double width;

    @Column(nullable = false)
    private String frameMaterial;

    @Column(length = 500)
    private String referenceImage; // path like /uploads/xxx.png

    @Column(nullable = false)
    private Integer quantity = 1;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPrice;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
