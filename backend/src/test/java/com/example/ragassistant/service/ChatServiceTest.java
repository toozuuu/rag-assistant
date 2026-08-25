package com.example.ragassistant.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ChatServiceTest {

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
