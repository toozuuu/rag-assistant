package com.example.ragassistant.service;

import com.example.ragassistant.dto.RepoConnectRequest;
import com.example.ragassistant.dto.RepoStatusResponse;
import com.example.ragassistant.ingestion.IngestionModule;
import org.springframework.stereotype.Service;

@Service
public class CodeIngestionService {

    private final IngestionModule ingestionModule;

    public CodeIngestionService(IngestionModule ingestionModule) {
        this.ingestionModule = ingestionModule;
    }

    public RepoStatusResponse ingestRepository(RepoConnectRequest request) {
        return ingestionModule.ingestRepository(request);
    }
}