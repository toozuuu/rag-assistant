package com.example.ragassistant.ingestion;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IndexResult {
    private int filesIndexed;
    private int chunksIndexed;
    private String status;
    private String message;
    private Map<String, Integer> fileTypes;
}