package com.example.ragassistant.service;

import com.example.ragassistant.dto.ChatHistoryEntry;
import com.example.ragassistant.dto.ChatResponse;
import com.example.ragassistant.dto.LlmConfig;
import com.example.ragassistant.rag.RagPipeline;
import com.example.ragassistant.rag.RagQuery;
import com.example.ragassistant.rag.RagResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@Service
@Slf4j
public class ChatService {

    private final RagPipeline ragPipeline;

    public ChatService(RagPipeline ragPipeline) {
        this.ragPipeline = ragPipeline;
    }

    public ChatResponse askQuestion(String question, String workspace, List<ChatHistoryEntry> history, LlmConfig llmConfig) {
        RagQuery query = RagQuery.builder()
                .question(question)
                .workspace(workspace)
                .history(history)
                .llmConfig(llmConfig)
                .topK(5)
                .threshold(0.4)
                .mode(RagQuery.OutputMode.BLOCKING)
                .build();

        RagResult result = ragPipeline.execute(query);

        return ChatResponse.builder()
                .answer(result.answer())
                .sources(result.sources())
                .images(result.images())
                .refusal(result.refusal())
                .reasoning(result.reasoning())
                .confidenceScore(result.confidenceScore())
                .build();
    }

    public ChatResponse askQuestion(String question, String workspace, List<ChatHistoryEntry> history) {
        return askQuestion(question, workspace, history, null);
    }

    public void askQuestionStream(String question, String workspace, List<ChatHistoryEntry> history, LlmConfig llmConfig, SseEmitter emitter) {
        RagQuery query = RagQuery.builder()
                .question(question)
                .workspace(workspace)
                .history(history)
                .llmConfig(llmConfig)
                .topK(5)
                .threshold(0.4)
                .mode(RagQuery.OutputMode.STREAMING)
                .emitter(emitter)
                .build();

        ragPipeline.execute(query);
    }

    public void askQuestionStream(String question, String workspace, List<ChatHistoryEntry> history, SseEmitter emitter) {
        askQuestionStream(question, workspace, history, null, emitter);
    }
}