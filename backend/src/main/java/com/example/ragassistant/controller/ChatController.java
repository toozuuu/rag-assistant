package com.example.ragassistant.controller;

import com.example.ragassistant.dto.ChatRequest;
import com.example.ragassistant.dto.ChatResponse;
import com.example.ragassistant.service.ChatService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping("/ask")
    public ResponseEntity<ChatResponse> ask(@Valid @RequestBody ChatRequest request) {
        String workspace = request.getWorkspace() != null ? request.getWorkspace() : "default";
        ChatResponse response = chatService.askQuestion(request.getQuestion(), workspace, request.getHistory());
        return ResponseEntity.ok(response);
    }

    @PostMapping(value = "/ask/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter askStream(@Valid @RequestBody ChatRequest request) {
        String workspace = request.getWorkspace() != null ? request.getWorkspace() : "default";
        SseEmitter emitter = new SseEmitter(60000L);

        chatService.askQuestionStream(request.getQuestion(), workspace, request.getHistory(), emitter);

        return emitter;
    }
}
