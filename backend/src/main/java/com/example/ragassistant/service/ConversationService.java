package com.example.ragassistant.service;

import com.example.ragassistant.dto.ChatHistoryEntry;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
@Slf4j
public class ConversationService {

    /** Pattern that a valid conversation ID must match to prevent path traversal. */
    private static final Pattern SAFE_ID = Pattern.compile("^[a-zA-Z0-9_\\-]{1,128}$");

    private final String conversationsDir;
    private final ObjectMapper objectMapper;

    public ConversationService(
            @Value("${app.conversations-dir:conversations}") String conversationsDir,
            ObjectMapper objectMapper) {
        this.conversationsDir = conversationsDir;
        this.objectMapper = objectMapper;
    }

    /**
     * Validates that the given conversation ID is safe to use as a filename.
     * Throws {@link IllegalArgumentException} on invalid input.
     */
    private void validateId(String conversationId) {
        if (conversationId == null || !SAFE_ID.matcher(conversationId).matches()) {
            throw new IllegalArgumentException("Invalid conversation ID: must contain only letters, digits, hyphens, or underscores");
        }
    }

    public String saveConversation(List<ChatHistoryEntry> messages, String conversationId) {
        if (conversationId == null || conversationId.isBlank()) {
            conversationId = UUID.randomUUID().toString();
        }
        validateId(conversationId);
        try {
            Path dir = Paths.get(conversationsDir);
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
        validateId(conversationId);
        try {
            Path file = Paths.get(conversationsDir, conversationId + ".json");
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
            Path dir = Paths.get(conversationsDir);
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