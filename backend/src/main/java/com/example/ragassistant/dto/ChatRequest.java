package com.example.ragassistant.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatRequest {
    @NotBlank(message = "Question must not be blank")
    private String question;
    private String workspace;
    private List<ChatHistoryEntry> history;
    private LlmConfig llmConfig;
}