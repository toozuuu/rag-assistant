package com.example.ragassistant.service;

import com.example.ragassistant.dto.LlmConfig;
import com.example.ragassistant.dto.LlmTestResponse;
import com.example.ragassistant.llm.LlmPort;
import com.example.ragassistant.llm.LlmPortFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;

@Service
@Slf4j
public class DynamicLlmService {

    private final LlmPortFactory llmPortFactory;

    public DynamicLlmService(LlmPortFactory llmPortFactory) {
        this.llmPortFactory = llmPortFactory;
    }

    public boolean isConfigBlank(LlmConfig config) {
        return llmPortFactory.isConfigBlank(config);
    }

    public String generateCompletion(LlmConfig config, String systemPrompt, String userPrompt) {
        LlmPort port = llmPortFactory.resolve(config);
        return port.complete(systemPrompt, userPrompt);
    }

    public void generateCompletionStream(LlmConfig config, String systemPrompt, String userPrompt, SseEmitter emitter) {
        try {
            LlmPort port = llmPortFactory.resolve(config);
            String fullResponse = port.complete(systemPrompt, userPrompt);

            // Stream tokens to client
            String[] tokens = fullResponse.split("\\s+");
            for (int i = 0; i < tokens.length; i++) {
                String token = tokens[i] + (i < tokens.length - 1 ? " " : "");
                emitter.send(SseEmitter.event().data(token));
            }
            emitter.complete();
        } catch (IOException e) {
            log.warn("Client disconnected from SSE stream: {}", e.getMessage());
        } catch (Exception e) {
            log.error("Error during streaming completion: {}", e.getMessage(), e);
            try {
                emitter.send(SseEmitter.event().name("error").data("Generation failed: " + e.getMessage()));
            } catch (IOException ignored) {}
            emitter.completeWithError(e);
        }
    }

    public LlmTestResponse testConnection(LlmConfig config, String testPrompt) {
        long startTime = System.currentTimeMillis();
        try {
            LlmPort port = llmPortFactory.resolve(config);
            String prompt = (testPrompt != null && !testPrompt.isBlank())
                    ? testPrompt
                    : "Respond with 'Connection successful!' if you receive this message.";
            String response = port.complete(null, prompt);
            long latency = System.currentTimeMillis() - startTime;

            return LlmTestResponse.builder()
                    .success(true)
                    .message("Successfully connected to " + (config != null ? config.getProvider() : "Default") + "!")
                    .latencyMs(latency)
                    .sampleResponse(response.length() > 200 ? response.substring(0, 200) + "..." : response)
                    .build();
        } catch (Exception e) {
            long latency = System.currentTimeMillis() - startTime;
            log.error("LLM Connection test failed: {}", e.getMessage());
            return LlmTestResponse.builder()
                    .success(false)
                    .message("Connection failed: " + e.getMessage())
                    .latencyMs(latency)
                    .build();
        }
    }

    public LlmTestResponse testConnection(LlmConfig config) {
        return testConnection(config, null);
    }
}