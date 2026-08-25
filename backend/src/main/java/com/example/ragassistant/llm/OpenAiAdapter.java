package com.example.ragassistant.llm;

import com.example.ragassistant.dto.LlmConfig;
import com.example.ragassistant.dto.LlmProvider;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class OpenAiAdapter implements LlmPort {

    private final LlmConfig config;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public OpenAiAdapter(LlmConfig config, RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.config = config;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public String complete(String systemPrompt, String userPrompt) {
        String baseUrl = config.getBaseUrl();
        LlmProvider provider = LlmProvider.fromString(config.getProvider());

        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = switch (provider) {
                case OPENROUTER -> "https://openrouter.ai/api/v1";
                case GROQ -> "https://api.groq.com/openai/v1";
                default -> "https://api.openai.com/v1";
            };
        }

        baseUrl = baseUrl.replaceAll("/+$", "");
        String url = baseUrl.endsWith("/chat/completions") ? baseUrl : baseUrl + "/chat/completions";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (config.getApiKey() != null && !config.getApiKey().isBlank()) {
            headers.setBearerAuth(config.getApiKey());
        }

        if (provider == LlmProvider.OPENROUTER) {
            headers.set("X-Title", "RAG Assistant");
        }

        List<Map<String, String>> messages = new ArrayList<>();
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            messages.add(Map.of("role", "system", "content", systemPrompt));
        }
        messages.add(Map.of("role", "user", "content", userPrompt != null ? userPrompt : ""));

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", (config.getModel() != null && !config.getModel().isBlank()) ? config.getModel() : "gpt-4o-mini");
        requestBody.put("messages", messages);
        if (config.getTemperature() != null) {
            requestBody.put("temperature", config.getTemperature());
        }

        try {
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);
            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode choices = root.path("choices");
            if (choices.isArray() && !choices.isEmpty()) {
                return choices.get(0).path("message").path("content").asText();
            }
            throw new RuntimeException("Unexpected response structure from OpenAI-compatible provider: " + response.getBody());
        } catch (Exception e) {
            log.error("Failed to complete with OpenAI provider {}: {}", provider, e.getMessage());
            throw new RuntimeException("LLM call failed: " + e.getMessage(), e);
        }
    }
}