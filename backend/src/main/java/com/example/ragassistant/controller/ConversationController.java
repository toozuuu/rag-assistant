package com.example.ragassistant.controller;

import com.example.ragassistant.dto.ChatHistoryEntry;
import com.example.ragassistant.dto.ConversationRequest;
import com.example.ragassistant.service.ConversationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/conversations")
public class ConversationController {

    private final ConversationService conversationService;

    public ConversationController(ConversationService conversationService) {
        this.conversationService = conversationService;
    }

    @PostMapping
    public ResponseEntity<Map<String, String>> save(@RequestBody ConversationRequest request) {
        String id = conversationService.saveConversation(request.getMessages(), request.getConversationId());
        return ResponseEntity.ok(Map.of("conversationId", id));
    }

    @GetMapping("/{id}")
    public ResponseEntity<List<ChatHistoryEntry>> load(@PathVariable String id) {
        List<ChatHistoryEntry> messages = conversationService.loadConversation(id);
        return ResponseEntity.ok(messages);
    }

    @GetMapping
    public ResponseEntity<List<String>> list() {
        return ResponseEntity.ok(conversationService.listConversations());
    }
}
