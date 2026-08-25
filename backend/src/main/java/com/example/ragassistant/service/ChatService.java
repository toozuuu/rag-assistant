package com.example.ragassistant.service;

import com.example.ragassistant.dto.ChatHistoryEntry;
import com.example.ragassistant.dto.ChatResponse;
import com.example.ragassistant.dto.LlmConfig;
import com.example.ragassistant.dto.SourceReference;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
@Slf4j
public class ChatService {

    private static final String NOT_FOUND_MESSAGE = "I could not find this information in the indexed documentation or codebase.";

    private final DynamicLlmService dynamicLlmService;
    private final VectorStore vectorStore;
    private final ObjectMapper objectMapper;

    @Value("${app.base-url}")
    private String baseUrl;

    public ChatService(DynamicLlmService dynamicLlmService, VectorStore vectorStore, ObjectMapper objectMapper) {
        this.dynamicLlmService = dynamicLlmService;
        this.vectorStore = vectorStore;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void validateBaseUrl() {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalStateException("app.base-url must be configured");
        }
        try {
            new java.net.URL(baseUrl);
        } catch (java.net.MalformedURLException e) {
            throw new IllegalStateException("Invalid app.base-url: " + baseUrl, e);
        }
    }

    public ChatResponse askQuestion(String question, String workspace, List<ChatHistoryEntry> history) {
        return askQuestion(question, workspace, history, null);
    }

    public ChatResponse askQuestion(String question, String workspace, List<ChatHistoryEntry> history, LlmConfig llmConfig) {
        // 1. Retrieve top-4 most relevant child chunks above threshold
        List<Document> similarDocuments = searchSimilarDocuments(question, workspace);
        if (similarDocuments.isEmpty()) {
            return new ChatResponse(
                NOT_FOUND_MESSAGE,
                List.of(), List.of(), true,
                "Retrieval step returned zero documents or code files exceeding the similarity threshold of 0.35.",
                0.0
            );
        }

        // 2. Extract parent text contexts & build structured citation resources
        List<SourceReference> sources = new ArrayList<>();
        String context = buildContextString(similarDocuments, sources);

        // 3. Corrective RAG: Self-Correction Relevance Grader
        boolean isRelevant = evaluateContextRelevance(question, context, llmConfig);
        if (!isRelevant) {
            return new ChatResponse(
                NOT_FOUND_MESSAGE,
                List.of(), List.of(), true,
                "Retrieval Relevance Grader evaluated context chunks as not containing relevant facts to answer: '" + question + "'",
                0.0
            );
        }

        // 4. Generate structured answer with anti-hallucination prompt
        String rawAiResponse = callLlmWithContext(question, context, history, llmConfig);
        return parseLlmResponse(rawAiResponse, sources);
    }

    private String formatHistory(List<ChatHistoryEntry> history) {
        if (history == null || history.isEmpty()) return "No prior conversation.";
        StringBuilder sb = new StringBuilder();
        for (ChatHistoryEntry entry : history) {
            sb.append(entry.getRole()).append(": ").append(entry.getContent()).append("\n");
        }
        return sb.toString();
    }

    public void askQuestionStream(String question, String workspace, List<ChatHistoryEntry> history, SseEmitter emitter) {
        askQuestionStream(question, workspace, history, null, emitter);
    }

    public void askQuestionStream(String question, String workspace, List<ChatHistoryEntry> history, LlmConfig llmConfig, SseEmitter emitter) {
        try {
            List<Document> similarDocuments = searchSimilarDocuments(question, workspace);
            if (similarDocuments.isEmpty()) {
                emitter.send(SseEmitter.event().name("error").data("No relevant documents or code files found."));
                emitter.complete();
                return;
            }

            List<SourceReference> sources = new ArrayList<>();
            String context = buildContextString(similarDocuments, sources);

            boolean isRelevant = evaluateContextRelevance(question, context, llmConfig);
            if (!isRelevant) {
                emitter.send(SseEmitter.event().name("error").data("No relevant information found."));
                emitter.complete();
                return;
            }

            String historyStr = formatHistory(history);
            String systemPrompt = """
                INSTRUCTIONS (follow exactly):
                - You are an expert Software Architecture, Code & QA Requirement Assistant.
                - You ONLY answer using the CONTEXT LIST provided below.
                - When asked by QA or developers about requirements, business logic, endpoints, validation rules, or test scenarios, analyze the codebase logic and specifications in the CONTEXT.
                - Ground your answer completely.
                - NEVER use ungrounded external knowledge.

                CONVERSATION HISTORY:
                %s

                CONTEXT LIST:
                %s
                """;

            String systemMessage = String.format(systemPrompt, historyStr, context);
            dynamicLlmService.generateCompletionStream(llmConfig, systemMessage, question, emitter);
        } catch (Exception e) {
            log.error("Stream generation failed: {}", e.getMessage(), e);
            try {
                emitter.send(SseEmitter.event().name("error").data("Stream error: " + e.getMessage()));
            } catch (Exception ignored) {}
            emitter.completeWithError(e);
        }
    }

    private List<Document> searchSimilarDocuments(String question, String workspace) {
        String safeWorkspace = workspace != null ? workspace.replace("'", "\\'") : "default";
        return vectorStore.similaritySearch(
            SearchRequest.query(question)
                .withTopK(4)
                .withSimilarityThreshold(0.35)
                .withFilterExpression("workspace == '" + safeWorkspace + "'")
        );
    }

    private String buildContextString(List<Document> similarDocuments, List<SourceReference> sources) {
        StringBuilder contextBuilder = new StringBuilder();
        int citIndex = 0;

        for (Document doc : similarDocuments) {
            Map<String, Object> metadata = doc.getMetadata();
            String docId = (String) metadata.getOrDefault("doc_id", "unknown");
            String filename = (String) metadata.getOrDefault("filename", "unknown");
            String parentText = (String) metadata.getOrDefault("parent_text", doc.getContent());
            String chunkType = (String) metadata.getOrDefault("chunk_type", "doc");
            String filePath = (String) metadata.getOrDefault("filePath", "");
            String language = (String) metadata.getOrDefault("language", "");
            String repository = (String) metadata.getOrDefault("repository", "");
            String branch = (String) metadata.getOrDefault("branch", "");
            Integer pageNumber = extractPageNumber(metadata.get("page_number"));

            // Build human-readable snippet (truncated to 160 chars)
            String snippet = parentText.length() > 160
                    ? parentText.substring(0, 160).replace("\n", " ").trim() + "..."
                    : parentText.replace("\n", " ").trim();

            sources.add(new SourceReference(
                    filename,
                    chunkType,
                    snippet,
                    pageNumber,
                    filePath,
                    language,
                    repository,
                    branch
            ));

            String refHeader = String.format("[cit:%d] (File: %s%s%s)",
                    citIndex,
                    filename,
                    filePath.isBlank() ? "" : " | Path: " + filePath,
                    pageNumber != null ? " | Page: " + pageNumber : "");

            contextBuilder.append(refHeader).append("\n")
                          .append(parentText).append("\n\n");
            citIndex++;
        }
        return contextBuilder.toString();
    }

    private Integer extractPageNumber(Object pageNumObj) {
        if (pageNumObj instanceof Number number) {
            return number.intValue();
        } else if (pageNumObj != null) {
            try {
                return Integer.parseInt(pageNumObj.toString());
            } catch (NumberFormatException e) {
                // Ignore
            }
        }
        return null;
    }

    private boolean evaluateContextRelevance(String question, String context, LlmConfig llmConfig) {
        String gradingSystemPrompt = """
            You are a strict data grader. Evaluate if the CONTEXT provided below is relevant and contains any useful details to help answer the user's question: "{question}".
            Respond with EXACTLY one word: "YES" or "NO". Do not write any other words, details, or markdown formatting.
            
            CONTEXT:
            {context}
            """;
        
        PromptTemplate gradingTemplate = new PromptTemplate(gradingSystemPrompt);
        String gradingMessage = gradingTemplate.render(Map.of("question", question, "context", context));
        
        String gradingResult = dynamicLlmService.generateCompletion(llmConfig, gradingMessage, "Is the context relevant?");
        
        boolean isRelevant = gradingResult != null && gradingResult.trim().toUpperCase().contains("YES");
        log.info("Retrieval Grader evaluated document context relevance as: {} (Raw LLM output: {})", isRelevant, gradingResult);
        return isRelevant;
    }

    private String callLlmWithContext(String question, String context, List<ChatHistoryEntry> history, LlmConfig llmConfig) {
        String historyStr = formatHistory(history);
        String systemPrompt = """
            INSTRUCTIONS (follow exactly):
            - You are an expert Documentation, Codebase & QA Requirement Assistant.
            - You answer questions regarding requirements, API contracts, validations, business logic, test cases, and implementation details using the CONTEXT LIST provided below.
            - NEVER hallucinate or guess. Ground your answers strictly on the provided context.
            - You MUST respond in a strict JSON format matching the schema below. Do not include any other markdown packaging, code block ticks (```json), or text outside the JSON.
            - If you formulate a sentence based on Context [cit:X], append the citation marker `[cit:X]` at the end of that sentence or statement. You can use multiple citations like `[cit:0][cit:1]`.
            
            JSON SCHEMA:
            {{
              "reasoning": "Step-by-step logic explaining which code files or documentation sections contain the facts.",
              "answer": "The formatted markdown answer (supporting code blocks, bullet points, Gherkin/BDD tables, test scenarios) containing inline citation markers like [cit:0].",
              "confidenceScore": 0.0 to 1.0 representing how fully the context supports the question
            }}

            - If the CONTEXT contains an inline [image: ...] tag that is DIRECTLY relevant and illustrates the specific step or feature you are explaining, you MUST include that exact [image: ...] tag in the "answer" field at the end of the relevant sentence.
            - DO NOT include any [image: ...] tags that are not directly relevant.
            
            CONVERSATION HISTORY:
            {history}
            
            CONTEXT LIST:
            {context}
            """;

        PromptTemplate promptTemplate = new PromptTemplate(systemPrompt);
        String systemMessage = promptTemplate.render(Map.of("context", context, "history", historyStr));

        String rawAiResponse = dynamicLlmService.generateCompletion(llmConfig, systemMessage, question);

        if (rawAiResponse == null) {
            rawAiResponse = "{\"reasoning\":\"No response from model\",\"answer\":\"" + NOT_FOUND_MESSAGE + "\",\"confidenceScore\":0.0}";
        }
        return rawAiResponse;
    }

    private ChatResponse parseLlmResponse(String rawAiResponse, List<SourceReference> sources) {
        String jsonText = rawAiResponse.trim();
        if (jsonText.startsWith("```")) {
            int firstNewline = jsonText.indexOf('\n');
            int lastBackticks = jsonText.lastIndexOf("```");
            if (firstNewline != -1 && lastBackticks > firstNewline) {
                jsonText = jsonText.substring(firstNewline + 1, lastBackticks).trim();
            }
        }

        String answer = NOT_FOUND_MESSAGE;
        String reasoning = "Direct context match.";
        double confidenceScore = 1.0;
        boolean isRefusal = false;

        try {
            var node = objectMapper.readTree(jsonText);
            if (node.has("answer")) {
                answer = node.get("answer").asText();
            }
            if (node.has("reasoning")) {
                reasoning = node.get("reasoning").asText();
            }
            if (node.has("confidenceScore")) {
                confidenceScore = node.get("confidenceScore").asDouble();
            }

            if (answer.contains("could not find") || confidenceScore < 0.2) {
                isRefusal = true;
            }
        } catch (Exception e) {
            log.warn("Failed to parse structured JSON from LLM, falling back to plain text parsing: {}", e.getMessage());
            answer = rawAiResponse;
            reasoning = "Fallback context evaluation.";
            confidenceScore = 0.5;
            if (rawAiResponse.contains("could not find")) {
                isRefusal = true;
            }
        }

        List<String> images = extractImagesFromAnswer(answer);
        return new ChatResponse(answer, sources, images, isRefusal, reasoning, confidenceScore);
    }

    private List<String> extractImagesFromAnswer(String answer) {
        List<String> images = new ArrayList<>();
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("\\[image:\\s*([^\\]]+)\\]").matcher(answer);
        while (matcher.find()) {
            String imgPath = matcher.group(1).trim();
            images.add(imgPath);
        }
        return images;
    }
}