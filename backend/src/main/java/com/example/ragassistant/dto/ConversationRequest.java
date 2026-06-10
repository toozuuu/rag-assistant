package com.example.ragassistant.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConversationRequest {
    private String conversationId;
    private List<ChatHistoryEntry> messages;
}
