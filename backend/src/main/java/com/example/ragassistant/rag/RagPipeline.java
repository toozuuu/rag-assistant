package com.example.ragassistant.rag;

import com.example.ragassistant.config.MetadataKeys;
import com.example.ragassistant.dto.ChatHistoryEntry;
import com.example.ragassistant.dto.ChatResponse;
import com.example.ragassistant.dto.SourceReference;
import com.example.ragassistant.retrieval.WorkspaceRetriever;
import com.example.ragassistant.service.DynamicLlmService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@Slf4j
public class RagPipeline {

    private static final String DEFAULT_SYSTEM_PROMPT = """
        You are a helpful and precise AI assistant. You answer questions based ONLY on the provided context documents.
        
        Guidelines:
        1. Base your answer strictly on the provided Context.
        2. If the context does not contain sufficient information to answer the question, state that you do not have enough information and provide a refusal.
        3. Use inline citation references like [Source 1], [Source 2] corresponding to the sources in the context.
        4. If you mention images from the context, preserve image references like `[image: <docId>/<filename>]`.
        
        Respond with a valid JSON object matching this schema:
        {
          "answer": "Your detailed answer here with citations...",
          "refusal": false,
          "reasoning": "Brief explanation of how the context supports this answer",
          "confidenceScore": 0.95
        }
        """;

    private static final Pattern CODE_BLOCK_PATTERN = Pattern.compile("(?s)```(?:json)?\\s*(.+?)\\s*```");
    private static final Pattern IMAGE_PATTERN = Pattern.compile("\\[image:\\s*([^\\]]+)\\]");

    private final WorkspaceRetriever workspaceRetriever;
    private final DynamicLlmService dynamicLlmService;
    private final ObjectMapper objectMapper;

    public RagPipeline(WorkspaceRetriever workspaceRetriever, DynamicLlmService dynamicLlmService, ObjectMapper objectMapper) {
        this.workspaceRetriever = workspaceRetriever;
        this.dynamicLlmService = dynamicLlmService;
        this.objectMapper = objectMapper;
    }

    public RagResult execute(RagQuery query) {
        String safeWorkspace = (query.workspace() == null || query.workspace().isBlank()) ? "default" : query.workspace().trim();

        // 1. Retrieve
        int topK = query.topK() > 0 ? query.topK() : 5;
        double threshold = query.threshold() >= 0 ? query.threshold() : 0.4;
        List<Document> similarDocs = workspaceRetriever.search(query.question(), safeWorkspace, topK, threshold);

        // 2. Assemble context & extract sources
        List<SourceReference> sources = new ArrayList<>();
        String contextText = buildContextString(similarDocs, sources);

        // 3. Relevance grading (Corrective-RAG)
        boolean isRelevant = evaluateContextRelevance(query.question(), contextText, query);
        if (!isRelevant && !similarDocs.isEmpty()) {
            log.info("Corrective RAG: Retrieved context deemed not relevant to query: '{}'", query.question());
        }

        // 4. Generate answer
        if (query.mode() == RagQuery.OutputMode.STREAMING && query.emitter() != null) {
            streamAnswer(query, contextText, sources);
            return RagResult.builder().sources(sources).build();
        } else {
            return generateBlockingAnswer(query, contextText, sources);
        }
    }

    private String buildContextString(List<Document> documents, List<SourceReference> sources) {
        if (documents == null || documents.isEmpty()) {
            return "No relevant context documents found in this workspace.";
        }

        StringBuilder sb = new StringBuilder();
        int citationIndex = 1;

        for (Document doc : documents) {
            Map<String, Object> meta = doc.getMetadata() != null ? doc.getMetadata() : Map.of();
            String filename = (String) meta.getOrDefault(MetadataKeys.FILENAME, "Document");
            String parentText = (String) meta.get(MetadataKeys.PARENT_TEXT);
            String contentToUse = (parentText != null && !parentText.isBlank()) ? parentText : doc.getContent();

            sb.append(String.format("--- [Source %d: %s] ---\n", citationIndex, filename));
            if (meta.containsKey(MetadataKeys.PAGE_NUMBER)) {
                sb.append("Page: ").append(meta.get(MetadataKeys.PAGE_NUMBER)).append("\n");
            }
            if (meta.containsKey(MetadataKeys.FILE_PATH)) {
                sb.append("Path: ").append(meta.get(MetadataKeys.FILE_PATH)).append("\n");
            }
            sb.append(contentToUse).append("\n\n");

            String snippet = contentToUse.length() > 250 ? contentToUse.substring(0, 250) + "..." : contentToUse;
            sources.add(SourceReference.builder()
                    .citationIndex(citationIndex)
                    .documentName(filename)
                    .pageNumber(meta.get(MetadataKeys.PAGE_NUMBER) instanceof Number n ? n.intValue() : null)
                    .snippet(snippet)
                    .docId((String) meta.get(MetadataKeys.DOC_ID))
                    .metadata(meta)
                    .build());

            citationIndex++;
        }
        return sb.toString();
    }

    private boolean evaluateContextRelevance(String question, String context, RagQuery query) {
        if (context.startsWith("No relevant context documents")) {
            return false;
        }
        String prompt = String.format("""
            Given the following question and context, evaluate if the context contains relevant information to help answer the question.
            Respond with ONLY 'YES' or 'NO'.
            
            Question: %s
            Context: %s
            """, question, context.length() > 1000 ? context.substring(0, 1000) : context);

        try {
            String evaluation = dynamicLlmService.generateCompletion(query.llmConfig(), null, prompt);
            return evaluation != null && evaluation.trim().toUpperCase().contains("YES");
        } catch (Exception e) {
            log.warn("Relevance evaluation failed, falling back to treating context as relevant: {}", e.getMessage());
            return true;
        }
    }

    private RagResult generateBlockingAnswer(RagQuery query, String context, List<SourceReference> sources) {
        String systemPrompt = (query.customSystemPrompt() != null && !query.customSystemPrompt().isBlank())
                ? query.customSystemPrompt()
                : DEFAULT_SYSTEM_PROMPT;

        String userPrompt = buildUserPrompt(query.question(), context, query.history());
        String rawResponse = dynamicLlmService.generateCompletion(query.llmConfig(), systemPrompt, userPrompt);

        return parseLlmResponse(rawResponse, sources);
    }

    private void streamAnswer(RagQuery query, String context, List<SourceReference> sources) {
        SseEmitter emitter = query.emitter();
        try {
            // First emit source references as SSE event
            emitter.send(SseEmitter.event().name("sources").data(sources));

            String systemPrompt = (query.customSystemPrompt() != null && !query.customSystemPrompt().isBlank())
                    ? query.customSystemPrompt()
                    : "You are a helpful assistant. Answer the question based on the provided context. Include citations.";

            String userPrompt = buildUserPrompt(query.question(), context, query.history());
            dynamicLlmService.generateCompletionStream(query.llmConfig(), systemPrompt, userPrompt, emitter);
        } catch (Exception e) {
            log.error("Streaming failed: {}", e.getMessage(), e);
            emitter.completeWithError(e);
        }
    }

    private String buildUserPrompt(String question, String context, List<ChatHistoryEntry> history) {
        StringBuilder sb = new StringBuilder();
        if (history != null && !history.isEmpty()) {
            sb.append("Conversation History:\n");
            for (ChatHistoryEntry entry : history) {
                sb.append(entry.getRole()).append(": ").append(entry.getContent()).append("\n");
            }
            sb.append("\n");
        }
        sb.append("Context:\n").append(context).append("\n\n");
        sb.append("Question: ").append(question);
        return sb.toString();
    }

    public RagResult parseLlmResponse(String rawResponse, List<SourceReference> sources) {
        if (rawResponse == null || rawResponse.isBlank()) {
            return RagResult.builder()
                    .answer("I was unable to generate a response.")
                    .sources(sources)
                    .images(List.of())
                    .refusal(true)
                    .confidenceScore(0.0)
                    .build();
        }

        String cleanedJson = stripCodeBlocks(rawResponse);
        try {
            JsonNode node = objectMapper.readTree(cleanedJson);
            String answer = node.path("answer").asText(rawResponse);
            boolean refusal = node.path("refusal").asBoolean(false);
            String reasoning = node.path("reasoning").asText("");
            double confidence = node.path("confidenceScore").asDouble(1.0);

            List<String> images = extractImagesFromText(answer);

            return RagResult.builder()
                    .answer(answer)
                    .sources(sources)
                    .images(images)
                    .refusal(refusal)
                    .reasoning(reasoning)
                    .confidenceScore(confidence)
                    .build();
        } catch (Exception e) {
            log.debug("JSON parse failed on LLM response, using raw text: {}", e.getMessage());
            List<String> images = extractImagesFromText(rawResponse);
            return RagResult.builder()
                    .answer(rawResponse)
                    .sources(sources)
                    .images(images)
                    .refusal(false)
                    .confidenceScore(0.8)
                    .build();
        }
    }

    public static String stripCodeBlocks(String text) {
        if (text == null) return "";
        Matcher matcher = CODE_BLOCK_PATTERN.matcher(text.trim());
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return text.trim();
    }

    public static List<String> extractImagesFromText(String text) {
        if (text == null) return List.of();
        List<String> images = new ArrayList<>();
        Matcher matcher = IMAGE_PATTERN.matcher(text);
        while (matcher.find()) {
            images.add(matcher.group(1).trim());
        }
        return images;
    }
}