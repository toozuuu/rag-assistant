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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class AnthropicAdapter implements LlmPort {

    private final LlmConfig config;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public AnthropicAdapter(LlmConfig config, RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.config = config;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public String complete(String systemPrompt, String userPrompt) {
        String baseUrl = config.getBaseUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "https://api.anthropic.com/v1";
        }
        baseUrl = baseUrl.replaceAll("/+$", "");
        String url = baseUrl.endsWith("/messages") ? baseUrl : baseUrl + "/messages";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (config.getApiKey() != null && !config.getApiKey().isBlank()) {
            headers.set("x-api-key", config.getApiKey());
        }
        headers.set("anthropic-version", "2023-06-01");

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", (config.getModel() != null && !config.getModel().isBlank()) ? config.getModel() : "claude-3-5-sonnet-20241022");
        requestBody.put("max_tokens", 4096);
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            requestBody.put("system", systemPrompt);
        }
        requestBody.put("messages", List.of(Map.of("role", "user", "content", userPrompt != null ? userPrompt : "")));
        if (config.getTemperature() != null) {
            requestBody.put("temperature", config.getTemperature());
        }

        try {
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);
            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode content = root.path("content");
            if (content.isArray() && !content.isEmpty()) {
                return content.get(0).path("text").asText();
            }
            throw new RuntimeException("Unexpected response from Anthropic: " + response.getBody());
        } catch (Exception e) {
            log.error("Failed to complete with Anthropic: {}", e.getMessage());
            throw new RuntimeException("Anthropic LLM call failed: " + e.getMessage(), e);
        }
    }
}