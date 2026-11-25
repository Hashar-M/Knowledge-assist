package com.abcorg.knowledge.assist.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Settings for the query side. Prefix rag.ask. */
@ConfigurationProperties(prefix = "rag.ask")
public record AskProperties(
        @DefaultValue("5") int topK,
        @DefaultValue("50") int numCandidates,
        @DefaultValue("3000") int maxContextTokens,
        @DefaultValue("300") int snippetChars,
        @DefaultValue("5s") Duration searchTimeout) {

    /** Fail at startup, not at the first question. */
    public AskProperties {
        if (topK < 1) {
            throw new IllegalArgumentException("rag.ask.top-k must be at least 1");
        }
        if (numCandidates < topK) {
            throw new IllegalArgumentException("rag.ask.num-candidates (" + numCandidates
                    + ") must be >= rag.ask.top-k (" + topK + ")");
        }
        if (searchTimeout == null || searchTimeout.isNegative() || searchTimeout.isZero()) {
            throw new IllegalArgumentException("rag.ask.search-timeout must be positive");
        }
    }
}

