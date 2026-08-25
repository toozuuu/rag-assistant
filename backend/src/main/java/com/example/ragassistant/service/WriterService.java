package com.example.ragassistant.service;

import com.example.ragassistant.dto.LlmConfig;
import com.example.ragassistant.dto.WriterRequest;
import com.example.ragassistant.dto.WriterResponse;
import com.example.ragassistant.rag.RagPipeline;
import com.example.ragassistant.rag.RagQuery;
import com.example.ragassistant.rag.RagResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
public class WriterService {

    private static final String WRITER_SYSTEM_PROMPT = """
        You are an expert technical writer and document drafting assistant.
        Your goal is to generate high quality, clear, structured documents based on the provided context.
        
        Respond with a valid JSON object matching this schema:
        {
          "title": "Document Title",
          "content": "The full generated markdown document content...",
          "outline": ["Section 1", "Section 2", "Section 3"]
        }
        """;

    private final RagPipeline ragPipeline;
    private final ObjectMapper objectMapper;

    public WriterService(RagPipeline ragPipeline, ObjectMapper objectMapper) {
        this.ragPipeline = ragPipeline;
        this.objectMapper = objectMapper;
    }

    public WriterResponse generateDocument(WriterRequest request) {
        String queryText = (request.getPrompt() != null && !request.getPrompt().isBlank())
                ? request.getPrompt()
                : request.getDocType() != null ? request.getDocType() : "Technical Document";

        RagQuery query = RagQuery.builder()
                .question(queryText)
                .workspace(request.getWorkspace() != null ? request.getWorkspace() : "default")
                .llmConfig(request.getLlmConfig())
                .topK(10)
                .threshold(0.2)
                .customSystemPrompt(WRITER_SYSTEM_PROMPT)
                .mode(RagQuery.OutputMode.BLOCKING)
                .build();

        RagResult result = ragPipeline.execute(query);

        // Try to parse as Writer structured response
        String cleaned = RagPipeline.stripCodeBlocks(result.answer());
        try {
            JsonNode node = objectMapper.readTree(cleaned);
            String title = node.path("title").asText("Generated Document");
            String content = node.path("content").asText(result.answer());
            List<String> outline = new java.util.ArrayList<>();
            JsonNode outlineNode = node.path("outline");
            if (outlineNode.isArray()) {
                outlineNode.forEach(o -> outline.add(o.asText()));
            }

            return WriterResponse.builder()
                    .title(title)
                    .content(content)
                    .outline(outline)
                    .sources(result.sources())
                    .build();
        } catch (Exception e) {
            return WriterResponse.builder()
                    .title("Generated " + (request.getDocType() != null ? request.getDocType() : "Document"))
                    .content(result.answer())
                    .outline(List.of())
                    .sources(result.sources())
                    .build();
        }
    }
}