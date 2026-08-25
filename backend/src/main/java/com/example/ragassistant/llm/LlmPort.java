package com.example.ragassistant.llm;

public interface LlmPort {
    String complete(String systemPrompt, String userPrompt);
}