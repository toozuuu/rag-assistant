package com.example.ragassistant.rag;

import com.example.ragassistant.dto.ChatHistoryEntry;
import com.example.ragassistant.dto.LlmConfig;
import lombok.Builder;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@Builder
public record RagQuery(
        String question,
        String workspace,
        List<ChatHistoryEntry> history,
        LlmConfig llmConfig,
        int topK,
        double threshold,
        String customSystemPrompt,
        OutputMode mode,
        SseEmitter emitter
) {
    public enum OutputMode {
        BLOCKING,
        STREAMING
    }
}