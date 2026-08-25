package com.example.ragassistant.rag;

import com.example.ragassistant.dto.SourceReference;
import lombok.Builder;

import java.util.List;

@Builder
public record RagResult(
        String answer,
        List<SourceReference> sources,
        List<String> images,
        boolean refusal,
        String reasoning,
        double confidenceScore
) {}