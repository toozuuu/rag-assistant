package com.example.ragassistant.service;

import com.example.ragassistant.dto.LlmConfig;
import com.example.ragassistant.dto.LlmTestResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Service
@Slf4j
public class DynamicLlmService {

    private final ChatClient defaultChatClient;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public DynamicLlmService(ChatClient.Builder chatClientBuilder, ObjectMapper objectMapper) {
        this.defaultChatClient = chatClientBuilder.build();
        this.restTemplate = new RestTemplate();
        this.objectMapper = objectMapper;
    }

    public String generateCompletion(LlmConfig config, String systemPrompt, String userPrompt) {
        if (isConfigBlank(config)) {
            log.info("No custom LLM config provided, using default server ChatClient.");
            return defaultChatClient.prompt()
                    .system(systemPrompt)
                    .user(userPrompt)
                    .call()
                    .content();
        }

        String provider = resolveProvider(config);
        log.info("Executing completion using dynamic provider '{}' with model '{}'", provider, config.getModel());

        try {
            return switch (provider) {
                case "ANTHROPIC" -> callAnthropic(config, systemPrompt, userPrompt);
                case "OLLAMA" -> callOllama(config, systemPrompt, userPrompt);
                default -> callOpenAiCompatible(config, systemPrompt, userPrompt);
            };
        } catch (Exception e) {
            log.error("Failed to execute completion on dynamic LLM provider {}: {}", provider, e.getMessage(), e);
            throw new RuntimeException("LLM Provider (" + provider + ") error: " + e.getMessage(), e);
        }
    }

    public void generateCompletionStream(LlmConfig config, String systemPrompt, String userPrompt, SseEmitter emitter) {
        if (isConfigBlank(config)) {
            defaultChatClient.prompt()
                    .system(systemPrompt)
                    .user(userPrompt)
                    .stream()
                    .content()
                    .subscribe(
                            chunk -> {
                                try {
                                    emitter.send(SseEmitter.event().data(chunk));
                                } catch (Exception e) {
                                    log.warn("Error sending default stream chunk: {}", e.getMessage());
                                }
                            },
                            error -> {
                                log.error("Stream error: {}", error.getMessage());
                                emitter.completeWithError(error);
                            },
                            emitter::complete
                    );
            return;
        }

        // For dynamic configured models, stream chunks directly or send completion
        try {
            String fullResponse = generateCompletion(config, systemPrompt, userPrompt);
            String[] words = fullResponse.split("(?<=\\s+)");
            for (String word : words) {
                emitter.send(SseEmitter.event().data(word));
                Thread.sleep(15);
            }
            emitter.complete();
        } catch (Exception e) {
            log.error("Dynamic stream completion error: {}", e.getMessage());
            try {
                emitter.send(SseEmitter.event().name("error").data("LLM Error: " + e.getMessage()));
            } catch (Exception ignored) {}
            emitter.completeWithError(e);
        }
    }

    public LlmTestResponse testConnection(LlmConfig config) {
        if (isConfigBlank(config)) {
            return LlmTestResponse.builder()
                    .success(false)
                    .errorMessage("Configuration cannot be empty.")
                    .build();
        }

        long start = System.currentTimeMillis();
        String provider = resolveProvider(config);
        String testPrompt = "Respond with 'Connection successful!' if you can read this message.";

        try {
            String response = generateCompletion(config, "You are a helpful test assistant.", testPrompt);
            long latency = System.currentTimeMillis() - start;
            return LlmTestResponse.builder()
                    .success(true)
                    .provider(provider)
                    .model(config.getModel())
                    .response(response.trim())
                    .latencyMs(latency)
                    .build();
        } catch (Exception e) {
            long latency = System.currentTimeMillis() - start;
            return LlmTestResponse.builder()
                    .success(false)
                    .provider(provider)
                    .model(config.getModel())
                    .errorMessage(e.getMessage())
                    .latencyMs(latency)
                    .build();
        }
    }

    private String callOpenAiCompatible(LlmConfig config, String systemPrompt, String userPrompt) throws Exception {
        String baseUrl = config.getBaseUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = switch (resolveProvider(config)) {
                case "OPENROUTER" -> "https://openrouter.ai/api/v1";
                case "GROQ" -> "https://api.groq.com/openai/v1";
                case "DEEPSEEK" -> "https://api.deepseek.com/v1";
                default -> "https://api.openai.com/v1";
            };
        }
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        if (!baseUrl.endsWith("/chat/completions")) {
            baseUrl = baseUrl + "/chat/completions";
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (config.getApiKey() != null && !config.getApiKey().isBlank()) {
            headers.setBearerAuth(config.getApiKey().trim());
        }
        if ("OPENROUTER".equalsIgnoreCase(config.getProvider())) {
            headers.set("HTTP-Referer", "http://localhost:5173");
            headers.set("X-Title", "Knowledge QA Assistant");
        }

        Map<String, Object> body = new HashMap<>();
        body.put("model", config.getModel() != null && !config.getModel().isBlank() ? config.getModel() : "gpt-4o-mini");
        body.put("temperature", config.getTemperature() != null ? config.getTemperature() : 0.2);

        List<Map<String, String>> messages = new ArrayList<>();
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            messages.add(Map.of("role", "system", "content", systemPrompt));
        }
        messages.add(Map.of("role", "user", "content", userPrompt));
        body.put("messages", messages);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
        ResponseEntity<String> response = restTemplate.postForEntity(baseUrl, request, String.class);

        if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode choices = root.path("choices");
            if (choices.isArray() && choices.size() > 0) {
                return choices.get(0).path("message").path("content").asText("");
            }
        }
        throw new RuntimeException("Unexpected response from OpenAI compatible provider: " + response.getBody());
    }

    private String callAnthropic(LlmConfig config, String systemPrompt, String userPrompt) throws Exception {
        String baseUrl = config.getBaseUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "https://api.anthropic.com/v1/messages";
        } else if (!baseUrl.endsWith("/messages")) {
            baseUrl = baseUrl.endsWith("/") ? baseUrl + "messages" : baseUrl + "/messages";
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-api-key", config.getApiKey() != null ? config.getApiKey().trim() : "");
        headers.set("anthropic-version", "2023-06-01");

        Map<String, Object> body = new HashMap<>();
        body.put("model", config.getModel() != null && !config.getModel().isBlank() ? config.getModel() : "claude-3-5-sonnet-20241022");
        body.put("max_tokens", 4096);
        body.put("temperature", config.getTemperature() != null ? config.getTemperature() : 0.2);
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            body.put("system", systemPrompt);
        }

        body.put("messages", List.of(Map.of("role", "user", "content", userPrompt)));

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
        ResponseEntity<String> response = restTemplate.postForEntity(baseUrl, request, String.class);

        if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode content = root.path("content");
            if (content.isArray() && content.size() > 0) {
                return content.get(0).path("text").asText("");
            }
        }
        throw new RuntimeException("Unexpected response from Anthropic: " + response.getBody());
    }

    private String callOllama(LlmConfig config, String systemPrompt, String userPrompt) throws Exception {
        String baseUrl = config.getBaseUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "http://localhost:11434/api/chat";
        } else {
            if (baseUrl.endsWith("/")) {
                baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
            }
            if (!baseUrl.endsWith("/api/chat") && !baseUrl.endsWith("/api/generate")) {
                baseUrl = baseUrl + "/api/chat";
            }
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = new HashMap<>();
        body.put("model", config.getModel() != null && !config.getModel().isBlank() ? config.getModel() : "phi3:mini");
        body.put("stream", false);

        Map<String, Object> options = new HashMap<>();
        options.put("temperature", config.getTemperature() != null ? config.getTemperature() : 0.2);
        body.put("options", options);

        List<Map<String, String>> messages = new ArrayList<>();
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            messages.add(Map.of("role", "system", "content", systemPrompt));
        }
        messages.add(Map.of("role", "user", "content", userPrompt));
        body.put("messages", messages);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
        ResponseEntity<String> response = restTemplate.postForEntity(baseUrl, request, String.class);

        if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
            JsonNode root = objectMapper.readTree(response.getBody());
            return root.path("message").path("content").asText("");
        }
        throw new RuntimeException("Unexpected response from Ollama: " + response.getBody());
    }

    private boolean isConfigBlank(LlmConfig config) {
        return config == null ||
                (config.getProvider() == null && config.getModel() == null && config.getApiKey() == null && config.getBaseUrl() == null);
    }

    private String resolveProvider(LlmConfig config) {
        if (config == null || config.getProvider() == null || config.getProvider().isBlank()) {
            return "OPENAI";
        }
        return config.getProvider().trim().toUpperCase();
    }
}