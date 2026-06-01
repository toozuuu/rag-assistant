package com.example.ragassistant.service;

import com.example.ragassistant.dto.ChatResponse;
import com.example.ragassistant.dto.SourceReference;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ChatService {

    private final ChatClient chatClient;
    private final VectorStore vectorStore;

    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    public ChatService(ChatClient.Builder chatClientBuilder, VectorStore vectorStore) {
        this.chatClient = chatClientBuilder.build();
        this.vectorStore = vectorStore;
    }

    public ChatResponse askQuestion(String question, String workspace) {
        // 1. Retrieve top-4 most relevant child chunks, only above similarity threshold & matched workspace
        List<Document> similarDocuments = vectorStore.similaritySearch(
                SearchRequest.query(question)
                        .withTopK(4)
                        .withSimilarityThreshold(0.4)   // reject chunks that aren't relevant enough
                        .withFilterExpression("workspace == '" + workspace + "'")
        );

        if (similarDocuments.isEmpty()) {
            return new ChatResponse(
                "I could not find this information in the documentation.",
                List.of(), List.of(), true,
                "Retrieval step returned zero documents exceeding the similarity threshold of 0.4.",
                0.0
            );
        }

        // 2. Extract parent text contexts and build structured citation resources
        StringBuilder contextBuilder = new StringBuilder();
        List<SourceReference> sources = new ArrayList<>();
        for (int i = 0; i < similarDocuments.size(); i++) {
            Document doc = similarDocuments.get(i);
            String filename = String.valueOf(doc.getMetadata().getOrDefault("filename", "Unknown Document"));
            String parentText = String.valueOf(doc.getMetadata().getOrDefault("parent_text", doc.getContent()));
            
            Object pageNumObj = doc.getMetadata().get("page_number");
            Integer pageNumber = null;
            if (pageNumObj instanceof Number number) {
                pageNumber = number.intValue();
            } else if (pageNumObj != null) {
                try {
                    pageNumber = Integer.parseInt(pageNumObj.toString());
                } catch (NumberFormatException e) {
                    // Ignore
                }
            }

            // Append to context list
            contextBuilder.append("- Context [cit:").append(i).append("]:\n");
            contextBuilder.append("  File: ").append(filename);
            if (pageNumber != null) {
                contextBuilder.append(" (Page ").append(pageNumber).append(")");
            }
            contextBuilder.append("\n");
            contextBuilder.append("  Text: ").append(parentText).append("\n\n");

            // Build source reference
            sources.add(new SourceReference(filename, null, doc.getContent(), pageNumber));
        }
        String context = contextBuilder.toString();

        // 4. Corrective RAG: Self-Correction Relevance Grader
        String gradingSystemPrompt = """
            You are a strict data grader. Evaluate if the CONTEXT provided below is relevant and contains any useful details to help answer the user's question: "{question}".
            Respond with EXACTLY one word: "YES" or "NO". Do not write any other words, details, or markdown formatting.
            
            CONTEXT:
            {context}
            """;
        
        PromptTemplate gradingTemplate = new PromptTemplate(gradingSystemPrompt);
        String gradingMessage = gradingTemplate.render(Map.of("question", question, "context", context));
        
        String gradingResult = chatClient.prompt()
                .system(gradingMessage)
                .user("Is the context relevant?")
                .call()
                .content();
        
        boolean isRelevant = gradingResult != null && gradingResult.trim().toUpperCase().contains("YES");
        log.info("Retrieval Grader evaluated document context relevance as: {} (Raw LLM output: {})", isRelevant, gradingResult);

        if (!isRelevant) {
            return new ChatResponse(
                "I could not find this information in the documentation.",
                List.of(), List.of(), true,
                "Retrieval Relevance Grader evaluated the matched context chunks as not containing relevant facts to answer the question: '" + question + "'",
                0.0
            );
        }

        // 5. Strict anti-hallucination system prompt, instructing LLM to respond in structured JSON format
        String systemPrompt = """
            INSTRUCTIONS (follow exactly):
            - You are a documentation assistant.
            - You ONLY answer using the CONTEXT LIST provided below.
            - NEVER use your own knowledge. NEVER guess. NEVER suggest external websites or contacts.
            - You MUST respond in a strict JSON format matching the schema below. Do not include any other markdown packaging, code block ticks (```json), or text outside the JSON.
            - Ground your answer completely. If you formulate a sentence based on Context [cit:X], you MUST append the citation marker `[cit:X]` at the end of that sentence or phrase. You can use multiple citations like `[cit:0][cit:1]` if needed.
            
            JSON SCHEMA:
            {{
              "reasoning": "Step-by-step logic in 2-3 sentences explaining exactly which sections of the context contain the facts used to formulate the answer.",
              "answer": "The clean, formatted markdown answer containing inline citation markers like [cit:0] at the end of relevant sentences (using bold, numbered lists, etc. for formatting).",
              "confidenceScore": 0.0 to 1.0 representing how fully the context supports the question
            }}

            - If the CONTEXT contains an inline [image: ...] tag that is DIRECTLY relevant and illustrates the specific step or feature you are explaining, you MUST include that exact [image: ...] tag in the "answer" field at the end of the relevant sentence.
            - DO NOT include any [image: ...] tags that are not directly relevant.
            
            CONTEXT LIST:
            {context}
            """;

        PromptTemplate promptTemplate = new PromptTemplate(systemPrompt);
        String systemMessage = promptTemplate.render(Map.of("context", context));

        String rawAiResponse = chatClient.prompt()
                .system(systemMessage)
                .user(question)
                .call()
                .content();

        if (rawAiResponse == null) {
            rawAiResponse = "{\"reasoning\":\"No response from model\",\"answer\":\"I could not find this information in the documentation.\",\"confidenceScore\":0.0}";
        }

        // Clean up markdown wrapping if present
        String jsonText = rawAiResponse.trim();
        if (jsonText.startsWith("```")) {
            jsonText = jsonText.replaceAll("^```[a-zA-Z]*\\s*", "");
            jsonText = jsonText.replaceAll("\\s*```$", "");
            jsonText = jsonText.trim();
        }

        String reasoning = "Evaluation of grounding files.";
        String answer = "I could not find this information in the documentation.";
        double confidenceScore = 0.0;
        boolean isRefusal = false;

        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.JsonNode rootNode = mapper.readTree(jsonText);
            
            reasoning = rootNode.path("reasoning").asText("Evaluated query context.");
            answer = rootNode.path("answer").asText("I could not find this information in the documentation.");
            confidenceScore = rootNode.path("confidenceScore").asDouble(0.0);
            
            if (answer.toLowerCase().contains("could not find") || confidenceScore < 0.3) {
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

        // 6. Extract image URLs only from the clean answer
        List<String> imageUrls = new ArrayList<>();
        java.util.regex.Pattern imagePattern = java.util.regex.Pattern.compile("\\[image:\\s*([^\\]]+)\\]");
        java.util.regex.Matcher matcher = imagePattern.matcher(answer);
        String cleanedBaseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        while (matcher.find()) {
            String ref = matcher.group(1).trim();
            imageUrls.add(cleanedBaseUrl + "/api/images/" + ref);
        }
        imageUrls = imageUrls.stream().distinct().collect(Collectors.toList());

        // 7. Strip out the image tags from the final text response so the user gets clean text
        String finalAnswer = answer.replaceAll("\\[image:\\s*([^\\]]+)\\]", "").trim();

        if (checkRefusal(finalAnswer)) {
            isRefusal = true;
        }

        return new ChatResponse(
                finalAnswer,
                isRefusal ? List.of() : sources,
                isRefusal ? List.of() : imageUrls,
                isRefusal,
                reasoning,
                confidenceScore
        );
    }

    private boolean checkRefusal(String response) {
        if (response == null) return false;
        String lower = response.toLowerCase();
        return lower.contains("i cannot")
                || lower.contains("i am sorry")
                || lower.contains("i'm sorry")
                || lower.contains("harmful actions")
                || lower.contains("cannot assist")
                || lower.contains("cannot provide")
                || lower.contains("against my guidelines")
                || lower.contains("against my programming")
                || lower.contains("ethical guidelines")
                || lower.contains("safety guidelines")
                || lower.contains("how to kill");
    }
}
