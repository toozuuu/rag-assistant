package com.example.ragassistant.service;

import com.example.ragassistant.dto.ChatHistoryEntry;
import com.example.ragassistant.dto.ChatResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

    @Mock
    private ChatClient.Builder chatClientBuilder;

    @Mock
    private ChatClient chatClient;

    @Mock
    private VectorStore vectorStore;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void sanitizeFilterValue_escapesSingleQuotes() {
        String result = sanitizeFilterValue("test'workspace");
        assertEquals("test\\'workspace", result);
    }

    @Test
    void sanitizeFilterValue_nullReturnsDefault() {
        String result = sanitizeFilterValue(null);
        assertEquals("default", result);
    }

    @Test
    void sanitizeFilterValue_normalValueUnchanged() {
        String result = sanitizeFilterValue("my-workspace_1");
        assertEquals("my-workspace_1", result);
    }

    private String sanitizeFilterValue(String value) {
        if (value == null) return "default";
        return value.replace("'", "\\'");
    }
}
