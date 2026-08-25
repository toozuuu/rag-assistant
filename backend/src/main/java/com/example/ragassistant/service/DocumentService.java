package com.example.ragassistant.service;

import com.example.ragassistant.ingestion.IngestionModule;
import com.example.ragassistant.ingestion.IngestionSource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@Service
public class DocumentService {

    private final IngestionModule ingestionModule;

    public DocumentService(IngestionModule ingestionModule) {
        this.ingestionModule = ingestionModule;
    }

    public void processAndStoreDocument(MultipartFile file, String workspace) {
        ingestionModule.ingest(new IngestionSource.FileSource(file, workspace));
    }

    public List<Map<String, String>> listDocuments(String workspace) {
        return ingestionModule.listDocuments(workspace);
    }

    public void deleteDocument(String docId, String workspace) {
        ingestionModule.deleteDocument(docId, workspace);
    }
}