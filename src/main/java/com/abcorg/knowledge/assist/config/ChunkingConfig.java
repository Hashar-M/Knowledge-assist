package com.abcorg.knowledge.assist.config;

import com.abcorg.knowledge.assist.chunking.ChunkerSettings;
import com.abcorg.knowledge.assist.chunking.HeadingAwareChunker;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ChunkingConfig {

    @Bean
    HeadingAwareChunker headingAwareChunker(
            @Value("${rag.chunking.max-tokens:500}") int maxTokens,
            @Value("${rag.chunking.overlap-tokens:50}") int overlapTokens) {
        return new HeadingAwareChunker(new ChunkerSettings(maxTokens, overlapTokens));
    }
}
