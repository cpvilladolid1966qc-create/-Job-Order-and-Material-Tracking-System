package com.stylemaria.controller;

import com.stylemaria.util.AuthHelper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.*;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/files")
public class FileController {

    @Value("${app.upload-dir}") private String uploadDir;
    private final AuthHelper authHelper;

    public FileController(AuthHelper authHelper) { this.authHelper = authHelper; }

    @PostMapping("/upload")
    public ResponseEntity<?> upload(@RequestParam("file") MultipartFile file,
                                    HttpServletRequest request) throws IOException {
        authHelper.requireUser(request);
        if (file.isEmpty()) return ResponseEntity.badRequest().body(Map.of("error", "Empty file"));

        Files.createDirectories(Paths.get(uploadDir));
        String original = file.getOriginalFilename() == null ? "file" : file.getOriginalFilename();
        String ext = original.contains(".") ? original.substring(original.lastIndexOf('.')) : "";
        String name = UUID.randomUUID().toString().replace("-", "") + ext;
        Path target = Paths.get(uploadDir, name);
        Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);

        return ResponseEntity.ok(Map.of("url", "/uploads/" + name));
    }
}
