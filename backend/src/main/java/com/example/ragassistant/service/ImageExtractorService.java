package com.example.ragassistant.service;

import com.example.ragassistant.ingestion.IngestionModule;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ImageExtractorService {

    public static final String UPLOAD_DIR = IngestionModule.UPLOAD_DIR;

    private final IngestionModule ingestionModule;

    public ImageExtractorService(IngestionModule ingestionModule) {
        this.ingestionModule = ingestionModule;
    }

    public List<String> extractImages(byte[] fileBytes, String originalFilename, String docId) {
        return ingestionModule.extractImages(fileBytes, originalFilename, docId);
    }
}