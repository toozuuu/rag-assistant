package com.example.ragassistant.ingestion;

import com.example.ragassistant.dto.RepoConnectRequest;
import org.springframework.web.multipart.MultipartFile;

public sealed interface IngestionSource permits IngestionSource.FileSource, IngestionSource.RepoSource {

    record FileSource(MultipartFile file, String workspace) implements IngestionSource {}

    record RepoSource(RepoConnectRequest request) implements IngestionSource {}
}