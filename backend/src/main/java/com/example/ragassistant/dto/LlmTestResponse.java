package com.example.ragassistant.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LlmTestResponse {
    private boolean success;
    private String provider;
    private String model;
    private String response;
    private long latencyMs;
    private String errorMessage;
}