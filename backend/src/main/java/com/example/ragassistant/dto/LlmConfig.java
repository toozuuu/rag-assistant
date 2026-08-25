package com.example.ragassistant.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LlmConfig {
    private String provider; // OPENAI, ANTHROPIC, OLLAMA, OPENROUTER, GROQ, CUSTOM
    private String model;
    private String apiKey;
    private String baseUrl;
    private Double temperature;
}