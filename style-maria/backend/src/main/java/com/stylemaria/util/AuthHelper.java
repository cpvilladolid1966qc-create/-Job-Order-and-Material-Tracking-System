package com.stylemaria.util;

import com.stylemaria.model.User;
import com.stylemaria.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import jakarta.servlet.http.HttpServletRequest;

@Component
public class AuthHelper {

    @Autowired
    private UserRepository userRepository;

    public User requireUser(HttpServletRequest request) {
        String token = extractToken(request);
        if (token == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing token");
        return userRepository.findByToken(token)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid token"));
    }

    public User requireAdmin(HttpServletRequest request) {
        User u = requireUser(request);
        if (!"ADMIN".equals(u.getRole())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin only");
        }
        return u;
    }

    private String extractToken(HttpServletRequest req) {
        String h = req.getHeader("Authorization");
        if (h != null && h.startsWith("Bearer ")) return h.substring(7);
        return null;
    }
}
