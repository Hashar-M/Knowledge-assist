package com.abcorg.knowledge.assist.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * All project-specific settings live under the "rag" prefix in application.yml.
 */
@ConfigurationProperties(prefix = "rag")
public class RagProperties {
    public record Elasticsearch(String indexAlias) {
    }
}
