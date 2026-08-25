package com.example.ragassistant.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RepoStatusResponse {
    private String workspace;
    private String repoUrl;
    private String branch;
    private String provider;
    private int filesIndexed;
    private int chunksIndexed;
    private String status; // SUCCESS, FAILED, IN_PROGRESS
    private String message;
    private Map<String, Integer> fileTypes;
}