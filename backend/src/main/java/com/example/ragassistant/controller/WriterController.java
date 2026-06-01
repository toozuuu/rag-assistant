package com.example.ragassistant.controller;

import com.example.ragassistant.dto.WriterRequest;
import com.example.ragassistant.dto.WriterResponse;
import com.example.ragassistant.service.WriterService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/writer")
public class WriterController {

    private final WriterService writerService;

    public WriterController(WriterService writerService) {
        this.writerService = writerService;
    }

    @PostMapping("/generate")
    public ResponseEntity<WriterResponse> generate(@RequestBody WriterRequest request) {
        String workspace = request.getWorkspace() != null ? request.getWorkspace() : "default";
        WriterResponse response = writerService.generateDocument(request.getPrompt(), workspace);
        return ResponseEntity.ok(response);
    }
}
