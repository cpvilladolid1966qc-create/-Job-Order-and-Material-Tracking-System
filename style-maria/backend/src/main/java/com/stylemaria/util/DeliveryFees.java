package com.stylemaria.util;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

public class DeliveryFees {
    public static final Map<String, BigDecimal> AREAS = new LinkedHashMap<>();
    static {
        AREAS.put("Metro Manila", new BigDecimal("250.00"));
        AREAS.put("Luzon (outside Metro Manila)", new BigDecimal("450.00"));
        AREAS.put("Visayas", new BigDecimal("650.00"));
        AREAS.put("Mindanao", new BigDecimal("750.00"));
    }

    public static BigDecimal feeFor(String area) {
        return AREAS.get(area);
    }
}
