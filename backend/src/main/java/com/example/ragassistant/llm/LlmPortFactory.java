package com.example.ragassistant.llm;

import com.example.ragassistant.dto.LlmConfig;
import com.example.ragassistant.dto.LlmProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.ChatClient;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class LlmPortFactory {

    private final ChatClient defaultChatClient;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public LlmPortFactory(ChatClient defaultChatClient, RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.defaultChatClient = defaultChatClient;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    public boolean isConfigBlank(LlmConfig config) {
        return config == null
                || (config.getProvider() == null || config.getProvider().isBlank())
                && (config.getModel() == null || config.getModel().isBlank())
                && (config.getApiKey() == null || config.getApiKey().isBlank())
                && (config.getBaseUrl() == null || config.getBaseUrl().isBlank());
    }

    public LlmPort resolve(LlmConfig config) {
        if (isConfigBlank(config)) {
            return new SpringAiChatClientAdapter(defaultChatClient);
        }

        LlmProvider provider = LlmProvider.fromString(config.getProvider());
        return switch (provider) {
            case ANTHROPIC -> new AnthropicAdapter(config, restTemplate, objectMapper);
            case OLLAMA -> new OllamaAdapter(config, restTemplate, objectMapper);
            default -> new OpenAiAdapter(config, restTemplate, objectMapper);
        };
    }
}