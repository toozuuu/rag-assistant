package com.example.ragassistant.controller;

import com.example.ragassistant.dto.LlmTestRequest;
import com.example.ragassistant.dto.LlmTestResponse;
import com.example.ragassistant.service.DynamicLlmService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/llm")
public class LlmConfigController {

    private final DynamicLlmService dynamicLlmService;

    public LlmConfigController(DynamicLlmService dynamicLlmService) {
        this.dynamicLlmService = dynamicLlmService;
    }

    @PostMapping("/test")
    public ResponseEntity<LlmTestResponse> testConnection(@RequestBody LlmTestRequest request) {
        LlmTestResponse response = dynamicLlmService.testConnection(request.getConfig(), request.getTestPrompt());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/presets")
    public ResponseEntity<Map<String, Object>> getPresets() {
        Map<String, Object> presets = Map.of(
            "providers", List.of(
                Map.of(
                    "id", "OPENAI",
                    "name", "OpenAI",
                    "defaultBaseUrl", "https://api.openai.com/v1",
                    "defaultModel", "gpt-4o-mini",
                    "requiresApiKey", true,
                    "models", List.of("gpt-4o", "gpt-4o-mini", "gpt-4-turbo", "o1-preview", "o1-mini")
                ),
                Map.of(
                    "id", "ANTHROPIC",
                    "name", "Anthropic Claude",
                    "defaultBaseUrl", "https://api.anthropic.com/v1",
                    "defaultModel", "claude-3-5-sonnet-20241022",
                    "requiresApiKey", true,
                    "models", List.of("claude-3-5-sonnet-20241022", "claude-3-5-haiku-20241022", "claude-3-opus-20240229")
                ),
                Map.of(
                    "id", "OLLAMA",
                    "name", "Ollama (Local)",
                    "defaultBaseUrl", "http://localhost:11434",
                    "defaultModel", "phi3:mini",
                    "requiresApiKey", false,
                    "models", List.of("phi3:mini", "llama3.1", "llama3.2", "qwen2.5-coder", "mistral", "deepseek-r1", "nomic-embed-text")
                ),
                Map.of(
                    "id", "OPENROUTER",
                    "name", "OpenRouter",
                    "defaultBaseUrl", "https://openrouter.ai/api/v1",
                    "defaultModel", "anthropic/claude-3.5-sonnet",
                    "requiresApiKey", true,
                    "models", List.of("anthropic/claude-3.5-sonnet", "openai/gpt-4o", "meta-llama/llama-3.1-70b-instruct", "deepseek/deepseek-r1", "google/gemini-2.0-flash-exp:free")
                ),
                Map.of(
                    "id", "GROQ",
                    "name", "Groq Cloud",
                    "defaultBaseUrl", "https://api.groq.com/openai/v1",
                    "defaultModel", "llama-3.3-70b-versatile",
                    "requiresApiKey", true,
                    "models", List.of("llama-3.3-70b-versatile", "llama-3.1-8b-instant", "mixtral-8x7b-32768", "gemma2-9b-it")
                ),
                Map.of(
                    "id", "CUSTOM",
                    "name", "Custom OpenAI-Compatible",
                    "defaultBaseUrl", "http://localhost:8000/v1",
                    "defaultModel", "custom-model",
                    "requiresApiKey", false,
                    "models", List.of()
                )
            )
        );
        return ResponseEntity.ok(presets);
    }
}