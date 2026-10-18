package com.stylemaria.controller;

import com.stylemaria.model.Setting;
import com.stylemaria.repository.SettingRepository;
import com.stylemaria.util.Pricing;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class PublicController {

    private final SettingRepository settingRepo;

    public PublicController(SettingRepository s) { this.settingRepo = s; }

    @GetMapping("/qr")
    public ResponseEntity<?> qr() {
        String url = settingRepo.findById("qr_image").map(Setting::getValue).orElse("/images/qr-placeholder.svg");
        return ResponseEntity.ok(Map.of("url", url));
    }

    @GetMapping("/delivery-areas")
    public ResponseEntity<?> deliveryAreas() {
        return ResponseEntity.ok(com.stylemaria.util.DeliveryFees.AREAS);
    }

    @GetMapping("/quote")
    public ResponseEntity<?> quote(@RequestParam double length,
                                   @RequestParam double width,
                                   @RequestParam(defaultValue = "Solid Wood") String material) {
        BigDecimal p = Pricing.unitPrice(material, length, width);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("unitPrice", p);
        m.put("length", length);
        m.put("width", width);
        m.put("material", material);
        return ResponseEntity.ok(m);
    }
}
