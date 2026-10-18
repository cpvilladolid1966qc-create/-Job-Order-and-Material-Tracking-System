package com.stylemaria.controller;

import com.stylemaria.model.User;
import com.stylemaria.repository.UserRepository;
import com.stylemaria.util.AuthHelper;
import jakarta.servlet.http.HttpServletRequest;
import org.mindrot.jbcrypt.BCrypt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired private UserRepository userRepository;
    @Autowired private AuthHelper authHelper;

    @PostMapping("/signup")
    public ResponseEntity<?> signup(@RequestBody Map<String, String> body) {
        String email = body.getOrDefault("email", "").trim().toLowerCase();
        String password = body.getOrDefault("password", "");
        String fullName = body.getOrDefault("fullName", "").trim();

        if (email.isEmpty() || password.length() < 6 || fullName.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid input (password min 6 chars)"));
        }
        if (userRepository.findByEmail(email).isPresent()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Email already registered"));
        }

        User u = new User();
        u.setEmail(email);
        u.setPasswordHash(BCrypt.hashpw(password, BCrypt.gensalt()));
        u.setFullName(fullName);
        u.setRole("CUSTOMER");
        u.setToken(UUID.randomUUID().toString().replace("-", ""));
        userRepository.save(u);

        return ResponseEntity.ok(publicUser(u));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> body) {
        String email = body.getOrDefault("email", "").trim().toLowerCase();
        String password = body.getOrDefault("password", "");

        Optional<User> opt = userRepository.findByEmail(email);
        if (opt.isEmpty() || !BCrypt.checkpw(password, opt.get().getPasswordHash())) {
            return ResponseEntity.status(401).body(Map.of("error", "Invalid credentials"));
        }
        User u = opt.get();
        u.setToken(UUID.randomUUID().toString().replace("-", ""));
        userRepository.save(u);
        return ResponseEntity.ok(publicUser(u));
    }

    @GetMapping("/me")
    public ResponseEntity<?> me(HttpServletRequest request) {
        User u = authHelper.requireUser(request);
        return ResponseEntity.ok(publicUser(u));
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpServletRequest request) {
        try {
            User u = authHelper.requireUser(request);
            u.setToken(null);
            userRepository.save(u);
        } catch (Exception ignored) {}
        return ResponseEntity.ok(Map.of("ok", true));
    }

    private Map<String, Object> publicUser(User u) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", u.getId());
        m.put("email", u.getEmail());
        m.put("fullName", u.getFullName());
        m.put("role", u.getRole());
        m.put("token", u.getToken());
        m.put("initials", initials(u.getFullName()));
        return m;
    }

    private String initials(String name) {
        if (name == null || name.isBlank()) return "SM";
        String[] parts = name.trim().split("\\s+");
        String s = parts[0].substring(0, 1);
        if (parts.length > 1) s += parts[parts.length - 1].substring(0, 1);
        return s.toUpperCase();
    }
}
