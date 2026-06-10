package com.example.ragassistant.controller;

import com.example.ragassistant.service.DocumentService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/documents")
@Slf4j
public class UploadController {

    private final DocumentService documentService;

    public UploadController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @PostMapping("/upload")
    public ResponseEntity<Map<String, String>> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "workspace", defaultValue = "default") String workspace
    ) {
        try {
            documentService.processAndStoreDocument(file, workspace);
            return ResponseEntity.ok(Map.of("message", "File uploaded and indexed successfully"));
        } catch (Exception e) {
            log.error("Failed to upload and store document: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping
    public ResponseEntity<List<Map<String, String>>> listDocuments(
            @RequestParam(value = "workspace", defaultValue = "default") String workspace
    ) {
        try {
            List<Map<String, String>> documents = documentService.listDocuments(workspace);
            return ResponseEntity.ok(documents);
        } catch (Exception e) {
            log.error("Failed to list documents: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @DeleteMapping("/{docId}")
    public ResponseEntity<Map<String, String>> deleteDocument(
            @PathVariable String docId,
            @RequestParam(value = "workspace", defaultValue = "default") String workspace
    ) {
        try {
            documentService.deleteDocument(docId, workspace);
            return ResponseEntity.ok(Map.of("message", "Document deleted successfully"));
        } catch (Exception e) {
            log.error("Failed to delete document: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }
}
