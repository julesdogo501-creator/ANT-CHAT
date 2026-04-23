package com.antchat.controller;

import com.antchat.service.FileStorageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/files")
@CrossOrigin(origins = "*")
public class FileController {

    @Autowired
    private FileStorageService fileStorageService;

    @PostMapping("/upload")
    public ResponseEntity<?> uploadFile(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body("File is empty");
        }

        try {
            String fileName = fileStorageService.storeFile(file);
            String fileUrl = "/uploads/" + fileName;

            String originalName = file.getOriginalFilename();
            if (originalName == null || originalName.isBlank()) {
                originalName = fileName;
            }

            String fileType = file.getContentType();
            if (fileType == null || fileType.isBlank()) {
                fileType = sniffContentType(originalName);
            } else if ("application/octet-stream".equalsIgnoreCase(fileType.trim())) {
                String sniffed = sniffContentType(originalName);
                if (sniffed != null) {
                    fileType = sniffed;
                }
            }

            Map<String, String> response = new HashMap<>();
            response.put("url", fileUrl);
            response.put("type", fileType);
            response.put("name", originalName);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Upload failed: " + e.getMessage());
        }
    }

    /** Quand le navigateur / client envoie un MIME vide ou générique, déduire depuis l'extension. */
    private static String sniffContentType(String originalName) {
        if (originalName == null) {
            return "application/octet-stream";
        }
        String n = originalName.toLowerCase();
        if (n.endsWith(".png")) return "image/png";
        if (n.endsWith(".jpg") || n.endsWith(".jpeg")) return "image/jpeg";
        if (n.endsWith(".gif")) return "image/gif";
        if (n.endsWith(".webp")) return "image/webp";
        if (n.endsWith(".bmp")) return "image/bmp";
        if (n.endsWith(".svg")) return "image/svg+xml";
        if (n.endsWith(".mp4")) return "video/mp4";
        if (n.endsWith(".webm")) return "video/webm";
        if (n.endsWith(".mp3")) return "audio/mpeg";
        if (n.endsWith(".wav")) return "audio/wav";
        if (n.endsWith(".pdf")) return "application/pdf";
        return "application/octet-stream";
    }
}
