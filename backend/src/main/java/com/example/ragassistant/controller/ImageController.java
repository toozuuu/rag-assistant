package com.example.ragassistant.controller;

import com.example.ragassistant.service.ImageExtractorService;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Path;
import java.nio.file.Paths;

@RestController
@RequestMapping("/api/images")
@CrossOrigin(origins = "*")
public class ImageController {

    @GetMapping("/{docId}/{filename}")
    public ResponseEntity<Resource> getImage(
            @PathVariable String docId,
            @PathVariable String filename
    ) {
        // Security: prevent path traversal
        if (docId.contains("..") || filename.contains("..") ||
            docId.contains("/")  || filename.contains("/")) {
            return ResponseEntity.badRequest().build();
        }

        Path imagePath = Paths.get(ImageExtractorService.UPLOAD_DIR, docId, filename);
        Resource resource = new FileSystemResource(imagePath);

        if (!resource.exists() || !resource.isReadable()) {
            return ResponseEntity.notFound().build();
        }

        String contentType = determineContentType(filename);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .body(resource);
    }

    private String determineContentType(String filename) {
        String lower = filename.toLowerCase();
        if (lower.endsWith(".png"))  return "image/png";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".gif"))  return "image/gif";
        if (lower.endsWith(".webp")) return "image/webp";
        if (lower.endsWith(".bmp"))  return "image/bmp";
        return "image/png";
    }
}
