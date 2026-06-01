package com.example.ragassistant.dto;

import lombok.Data;

@Data
public class ChatRequest {
    private String question;
    private String workspace;
}
