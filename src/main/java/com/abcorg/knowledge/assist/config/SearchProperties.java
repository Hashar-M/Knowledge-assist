package com.abcorg.knowledge.assist.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.search")
public record SearchProperties(
        String baseUrl,
        String alias,
        int mappingVersion,
        int embeddingDims,
        String embeddingModel) {
}
