package com.example.ragassistant.llm;

import org.springframework.ai.chat.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.List;
import java.util.Map;

public class SpringAiChatClientAdapter implements LlmPort {

    private final ChatClient chatClient;

    public SpringAiChatClientAdapter(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    @Override
    public String complete(String systemPrompt, String userPrompt) {
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            var sysMsg = new SystemPromptTemplate(systemPrompt).createMessage(Map.of());
            var userMsg = new UserMessage(userPrompt != null ? userPrompt : "");
            return chatClient.call(new Prompt(List.of(sysMsg, userMsg))).getResult().getOutput().getContent();
        } else {
            return chatClient.call(userPrompt != null ? userPrompt : "");
        }
    }
}