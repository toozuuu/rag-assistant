package com.example.ragassistant.service;

import com.example.ragassistant.dto.ChatHistoryEntry;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
public class ConversationService {

    private static final String CONVERSATIONS_DIR = "conversations";
    private final ObjectMapper objectMapper;

    public ConversationService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String saveConversation(List<ChatHistoryEntry> messages, String conversationId) {
        if (conversationId == null || conversationId.isBlank()) {
            conversationId = UUID.randomUUID().toString();
        }
        try {
            Path dir = Paths.get(CONVERSATIONS_DIR);
            Files.createDirectories(dir);
            Path file = dir.resolve(conversationId + ".json");
            objectMapper.writeValue(file.toFile(), messages);
            log.info("Saved conversation: {}", conversationId);
            return conversationId;
        } catch (IOException e) {
            log.warn("Failed to save conversation: {}", e.getMessage());
            return conversationId;
        }
    }

    public List<ChatHistoryEntry> loadConversation(String conversationId) {
        try {
            Path file = Paths.get(CONVERSATIONS_DIR, conversationId + ".json");
            if (Files.exists(file)) {
                return objectMapper.readValue(file.toFile(), new TypeReference<List<ChatHistoryEntry>>() {});
            }
        } catch (IOException e) {
            log.warn("Failed to load conversation: {}", e.getMessage());
        }
        return new ArrayList<>();
    }

    public List<String> listConversations() {
        List<String> ids = new ArrayList<>();
        try {
            Path dir = Paths.get(CONVERSATIONS_DIR);
            if (Files.exists(dir)) {
                try (var files = Files.list(dir)) {
                    files.filter(f -> f.toString().endsWith(".json"))
                            .map(f -> f.getFileName().toString().replace(".json", ""))
                            .forEach(ids::add);
                }
            }
        } catch (IOException e) {
            log.warn("Failed to list conversations: {}", e.getMessage());
        }
        return ids;
    }
}
