package com.example.ragassistant.service;

import com.example.ragassistant.dto.WriterResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
public class WriterService {

    private final ChatClient chatClient;
    private final VectorStore vectorStore;
    private final ObjectMapper objectMapper;

    public WriterService(ChatClient.Builder chatClientBuilder, VectorStore vectorStore, ObjectMapper objectMapper) {
        this.chatClient = chatClientBuilder.build();
        this.vectorStore = vectorStore;
        this.objectMapper = objectMapper;
    }

    public WriterResponse generateDocument(String prompt, String workspace) {
        log.info("Generating grounded document draft in workspace '{}' with prompt: '{}'", workspace, prompt);

        String safeWorkspace = sanitizeFilterValue(workspace);
        List<Document> similarDocuments = vectorStore.similaritySearch(
                SearchRequest.query(prompt)
                        .withTopK(8)
                        .withSimilarityThreshold(0.2)
                        .withFilterExpression("workspace == '" + safeWorkspace + "'")
        );

        String context = similarDocuments.isEmpty()
                ? "No context documents found in the current workspace."
                : similarDocuments.stream()
                        .map(doc -> "- Source File: "
                                + doc.getMetadata().getOrDefault("filename", "Unknown Document")
                                + "\n  Text Content:\n"
                                + doc.getMetadata().getOrDefault("parent_text", doc.getContent()))
                        .distinct()
                        .collect(Collectors.joining("\n\n---\n\n"));

        String systemPrompt = """
            INSTRUCTIONS (follow exactly):
            - You are an expert professional document author and technical writer.
            - Your task is to draft an extensive, high-quality, beautifully formatted document based on the user's request and the CONTEXT provided below.
            - Leverage the files in the workspace (provided via CONTEXT) to ensure the document is deeply specific, accurate, and aligned with standard policies, templates, or instructions in those documents.
            - Present the generated document in a beautiful Markdown format with clear headings, subheadings, lists, tables, bold styling, and code blocks as appropriate.
            - Avoid generic placeholders (e.g. "[Insert Date Here]"). Formulate a complete, ready-to-use professional document layout.
            - You MUST respond in a strict JSON format matching the schema below. Do not include any other markdown packaging, code block ticks (```json), or text outside the JSON.

            JSON SCHEMA:
            {{
              "reasoning": "Step-by-step reasoning (2-3 sentences) explaining which sections of the workspace documentation were utilized to compose the draft.",
              "draft": "The complete, detailed document drafted in full Markdown format."
            }}

            WORKSPACE CONTEXT:
            {context}
            """;

        PromptTemplate promptTemplate = new PromptTemplate(systemPrompt);
        String systemMessage = promptTemplate.render(Map.of("context", context));

        String rawResponse = chatClient.prompt()
                .system(systemMessage)
                .user(prompt)
                .call()
                .content();

        if (rawResponse == null) {
            return new WriterResponse(
                "# Document Generation Failed\nWe could not retrieve a valid draft from the LLM model.",
                "The LLM response was null."
            );
        }

        String jsonText = rawResponse.trim();
        if (jsonText.startsWith("```")) {
            jsonText = jsonText.replaceAll("^```[a-zA-Z]*\\s*", "");
            jsonText = jsonText.replaceAll("\\s*```$", "");
            jsonText = jsonText.trim();
        }

        try {
            JsonNode rootNode = objectMapper.readTree(jsonText);

            String reasoning = rootNode.path("reasoning").asText("Composed using workspace documentation.");
            String draft = rootNode.path("draft").asText("");

            if (draft.isEmpty()) {
                draft = jsonText;
            }

            return new WriterResponse(draft, reasoning);
        } catch (Exception e) {
            log.warn("Failed to parse structured JSON from document writer LLM, returning full output as draft: {}", e.getMessage());
            return new WriterResponse(rawResponse, "Composed draft with fallback parsing.");
        }
    }

    private String sanitizeFilterValue(String value) {
        if (value == null) return "default";
        return value.replace("'", "\\'");
    }
}
