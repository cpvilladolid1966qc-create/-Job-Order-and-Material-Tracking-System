package com.stylemaria.service;

import com.stylemaria.model.User;
import com.stylemaria.model.Setting;
import com.stylemaria.repository.UserRepository;
import com.stylemaria.repository.SettingRepository;
import jakarta.annotation.PostConstruct;
import org.mindrot.jbcrypt.BCrypt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class SeedService {

    @Autowired private UserRepository userRepository;
    @Autowired private SettingRepository settingRepository;

    @Value("${app.admin.email}") private String adminEmail;
    @Value("${app.admin.password}") private String adminPassword;
    @Value("${app.admin.name}") private String adminName;

    @PostConstruct
    public void seed() {
        if (userRepository.findByEmail(adminEmail).isEmpty()) {
            User u = new User();
            u.setEmail(adminEmail);
            u.setFullName(adminName);
            u.setPasswordHash(BCrypt.hashpw(adminPassword, BCrypt.gensalt()));
            u.setRole("ADMIN");
            userRepository.save(u);
        }
        if (settingRepository.findById("qr_image").isEmpty()) {
            Setting s = new Setting();
            s.setKey("qr_image");
            s.setValue("/images/qr-placeholder.svg");
            settingRepository.save(s);
        }
    }
}
