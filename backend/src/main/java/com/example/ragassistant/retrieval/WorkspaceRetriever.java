package com.example.ragassistant.retrieval;

import org.springframework.ai.document.Document;

import java.util.List;

public interface WorkspaceRetriever {
    List<Document> search(String query, String workspace, int topK, double threshold);
    void store(List<Document> documents);
    void deleteByDocId(String docId, String workspace);
    List<Document> listByWorkspace(String workspace);
}