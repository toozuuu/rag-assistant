package com.example.ragassistant.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "app.chunking")
public class ChunkingProperties {
    private int parentMaxTokens = 1000;
    private int parentOverlap = 200;
    private int childMaxTokens = 150;
    private int childOverlap = 30;
    private int minChunkSize = 5;
    private int maxChunks = 10000;
}
