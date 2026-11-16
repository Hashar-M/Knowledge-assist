package com.abcorg.knowledge.assist.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "rag.ingest")
public record IngestProperties(
        @DefaultValue("64") int batchSize,
        @DefaultValue("4") int maxAttempts,
        @DefaultValue("500ms") Duration initialBackoff,
        @DefaultValue("1536") int expectedDimensions,
        @DefaultValue("text-embedding-3-small") String embeddingModelName,
        @DefaultValue("0.02") double pricePerMillionTokens) {
}
