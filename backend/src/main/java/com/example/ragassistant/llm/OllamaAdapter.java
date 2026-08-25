package com.example.ragassistant.llm;

import com.example.ragassistant.dto.LlmConfig;
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
public class OllamaAdapter implements LlmPort {

    private final LlmConfig config;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public OllamaAdapter(LlmConfig config, RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.config = config;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public String complete(String systemPrompt, String userPrompt) {
        String baseUrl = config.getBaseUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "http://localhost:11434";
        }
        baseUrl = baseUrl.replaceAll("/+$", "");
        String url = baseUrl.endsWith("/api/chat") ? baseUrl : baseUrl + "/api/chat";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        List<Map<String, String>> messages = new ArrayList<>();
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            messages.add(Map.of("role", "system", "content", systemPrompt));
        }
        messages.add(Map.of("role", "user", "content", userPrompt != null ? userPrompt : ""));

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", (config.getModel() != null && !config.getModel().isBlank()) ? config.getModel() : "phi3:mini");
        requestBody.put("messages", messages);
        requestBody.put("stream", false);

        try {
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);
            JsonNode root = objectMapper.readTree(response.getBody());
            return root.path("message").path("content").asText();
        } catch (Exception e) {
            log.error("Failed to complete with Ollama: {}", e.getMessage());
            throw new RuntimeException("Ollama LLM call failed: " + e.getMessage(), e);
        }
    }
}