package com.example.ragassistant.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatResponse {
    private String answer;
    private List<SourceReference> sources;
    private List<String> imageUrls;
    private boolean refusal;
    private String reasoning;
    private double confidenceScore;
}
