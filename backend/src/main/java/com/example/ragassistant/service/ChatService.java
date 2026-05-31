package com.example.ragassistant.service;

import com.example.ragassistant.dto.ChatResponse;
import com.example.ragassistant.dto.SourceReference;
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
public class ChatService {

    private final ChatClient chatClient;
    private final VectorStore vectorStore;

    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    public ChatService(ChatClient.Builder chatClientBuilder, VectorStore vectorStore) {
        this.chatClient = chatClientBuilder.build();
        this.vectorStore = vectorStore;
    }

    public ChatResponse askQuestion(String question) {
        // 1. Retrieve top-4 most relevant chunks, only above similarity threshold
        List<Document> similarDocuments = vectorStore.similaritySearch(
                SearchRequest.query(question)
                        .withTopK(4)
                        .withSimilarityThreshold(0.4)   // reject chunks that aren't relevant enough
        );

        if (similarDocuments.isEmpty()) {
            return new ChatResponse(
                "I could not find this information in the documentation.",
                List.of(), List.of(), false
            );
        }

        // 2. Build context string for the LLM, keeping the inline image tags
        String context = similarDocuments.stream()
                .map(Document::getContent)
                .collect(Collectors.joining("\n\n---\n\n"));

        // 3. Deduplicate sources by filename
        List<SourceReference> sources = similarDocuments.stream()
                .map(doc -> String.valueOf(doc.getMetadata().getOrDefault("filename", "Unknown Document")))
                .distinct()
                .map(filename -> new SourceReference(filename, null))
                .toList();

        // 4. Strict anti-hallucination system prompt, instructing LLM to only include relevant images
        String systemPrompt = """
            INSTRUCTIONS (follow exactly):
            - You are a documentation assistant.
            - You ONLY answer using the CONTEXT provided below.
            - If the CONTEXT does not contain the answer, respond with EXACTLY this sentence and nothing else: "I could not find this information in the documentation."
            - NEVER use your own knowledge. NEVER guess. NEVER suggest external websites or contacts.
            - Format answers clearly: use numbered steps, bullet points, and **bold** for key terms.
            - Be concise and accurate.
            - If the CONTEXT contains an inline [image: ...] tag that is DIRECTLY relevant and illustrates the specific step or feature you are explaining, you MUST include that exact [image: ...] tag in your response at the end of the relevant sentence.
            - DO NOT include any [image: ...] tags that are not directly relevant to the user's question.

            CONTEXT:
            {context}

            IMPORTANT: If the answer is not in the CONTEXT above, say exactly: "I could not find this information in the documentation."
            """;

        PromptTemplate promptTemplate = new PromptTemplate(systemPrompt);
        String systemMessage = promptTemplate.render(Map.of("context", context));

        String rawAiResponse = chatClient.prompt()
                .system(systemMessage)
                .user(question)
                .call()
                .content();

        if (rawAiResponse == null) {
            rawAiResponse = "I could not find this information in the documentation.";
        }

        // 5. Extract image URLs only from the LLM's generated response
        List<String> imageUrls = new ArrayList<>();
        java.util.regex.Pattern imagePattern = java.util.regex.Pattern.compile("\\[image:\\s*([^\\]]+)\\]");
        java.util.regex.Matcher matcher = imagePattern.matcher(rawAiResponse);
        while (matcher.find()) {
            String ref = matcher.group(1).trim();
            imageUrls.add(baseUrl + "/api/images/" + ref);
        }
        imageUrls = imageUrls.stream().distinct().collect(Collectors.toList());

        // 6. Strip out the image tags from the final text response so the user gets clean text
        String aiResponse = rawAiResponse.replaceAll("\\[image:\\s*([^\\]]+)\\]", "").trim();

        boolean isRefusal = checkRefusal(aiResponse);

        return new ChatResponse(
                aiResponse,
                isRefusal ? List.of() : sources,
                isRefusal ? List.of() : imageUrls,
                isRefusal
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
