package com.example.ragassistant.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

@Data
public class ChatRequest {
    @NotBlank(message = "Question must not be blank")
    private String question;
    private String workspace;
    private List<ChatHistoryEntry> history;
}
