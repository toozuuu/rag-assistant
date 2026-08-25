package com.example.ragassistant.dto;

public enum LlmProvider {
    OPENAI("OpenAI"),
    ANTHROPIC("Anthropic Claude"),
    OLLAMA("Ollama (Local)"),
    OPENROUTER("OpenRouter"),
    GROQ("Groq Cloud"),
    DEEPSEEK("DeepSeek"),
    CUSTOM("Custom OpenAI-Compatible");

    private final String displayName;

    LlmProvider(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static LlmProvider fromString(String providerStr) {
        if (providerStr == null || providerStr.isBlank()) {
            return OPENAI;
        }
        try {
            return LlmProvider.valueOf(providerStr.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return CUSTOM;
        }
    }
}