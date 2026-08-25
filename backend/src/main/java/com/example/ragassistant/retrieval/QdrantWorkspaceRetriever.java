package com.example.ragassistant.retrieval;

import com.example.ragassistant.config.MetadataKeys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Component
@Primary
@Slf4j
public class QdrantWorkspaceRetriever implements WorkspaceRetriever {

    private final VectorStore vectorStore;
    private final RestTemplate restTemplate;

    @Value("${spring.ai.vectorstore.qdrant.host:localhost}")
    private String qdrantHost;

    @Value("${spring.ai.vectorstore.qdrant.port:6333}")
    private int qdrantRestPort;

    @Value("${spring.ai.vectorstore.qdrant.collection-name:document_embeddings}")
    private String collectionName;

    public QdrantWorkspaceRetriever(VectorStore vectorStore, RestTemplate restTemplate) {
        this.vectorStore = vectorStore;
        this.restTemplate = restTemplate;
    }

    public static String sanitize(String workspace) {
        if (workspace == null || workspace.isBlank()) {
            return "default";
        }
        return workspace.replace("'", "\\'");
    }

    @Override
    public List<Document> search(String query, String workspace, int topK, double threshold) {
        String safeWorkspace = sanitize(workspace);
        String filterExpression = MetadataKeys.WORKSPACE + " == '" + safeWorkspace + "'";
        try {
            return vectorStore.similaritySearch(
                    SearchRequest.query(query)
                            .withTopK(topK > 0 ? topK : 5)
                            .withSimilarityThreshold(threshold >= 0 ? threshold : 0.0)
                            .withFilterExpression(filterExpression)
            );
        } catch (Exception e) {
            log.error("Error executing vector similarity search for workspace {}: {}", safeWorkspace, e.getMessage());
            return Collections.emptyList();
        }
    }

    @Override
    public void store(List<Document> documents) {
        if (documents == null || documents.isEmpty()) {
            return;
        }
        vectorStore.accept(documents);
    }

    @Override
    public void deleteByDocId(String docId, String workspace) {
        String safeWorkspace = sanitize(workspace);
        String url = String.format("http://%s:%d/collections/%s/points/delete?wait=true",
                qdrantHost, qdrantRestPort, collectionName);

        String jsonPayload = String.format("""
            {
                "filter": {
                    "must": [
                        { "key": "%s", "match": { "value": "%s" } },
                        { "key": "%s", "match": { "value": "%s" } }
                    ]
                }
            }
            """, MetadataKeys.DOC_ID, docId, MetadataKeys.WORKSPACE, safeWorkspace);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> entity = new HttpEntity<>(jsonPayload, headers);

        try {
            restTemplate.postForEntity(url, entity, String.class);
            log.info("Deleted document points for docId: {} in workspace: {}", docId, safeWorkspace);
        } catch (Exception e) {
            log.error("Failed to delete points from Qdrant for docId {}: {}", docId, e.getMessage());
            throw new RuntimeException("Failed to delete document from vector database: " + e.getMessage(), e);
        }
    }

    @Override
    public List<Document> listByWorkspace(String workspace) {
        String safeWorkspace = sanitize(workspace);
        String filterExpression = MetadataKeys.WORKSPACE + " == '" + safeWorkspace + "'";
        try {
            // Retrieve sample/indexed docs for workspace
            return vectorStore.similaritySearch(
                    SearchRequest.query("document")
                            .withTopK(200)
                            .withSimilarityThreshold(0.0)
                            .withFilterExpression(filterExpression)
            );
        } catch (Exception e) {
            log.error("Error listing documents for workspace {}: {}", safeWorkspace, e.getMessage());
            return Collections.emptyList();
        }
    }
}