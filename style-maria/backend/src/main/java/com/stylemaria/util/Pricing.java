package com.stylemaria.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class Pricing {
    // Price formula: base price depends on material, area in cm^2 priced per 100 cm^2
    // Solid Wood: 0.80 per cm^2 + 500 base
    // MDF: 0.50 per cm^2 + 300 base
    // Aluminum: 0.70 per cm^2 + 400 base
    public static BigDecimal unitPrice(String material, double length, double width) {
        double area = length * width;
        double base, rate;
        switch (material == null ? "" : material.toLowerCase()) {
            case "mdf" -> { base = 300; rate = 0.50; }
            case "aluminum", "aluminium" -> { base = 400; rate = 0.70; }
            default -> { base = 500; rate = 0.80; } // Solid Wood default
        }
        double price = base + rate * area;
        return BigDecimal.valueOf(price).setScale(2, RoundingMode.HALF_UP);
    }
}
