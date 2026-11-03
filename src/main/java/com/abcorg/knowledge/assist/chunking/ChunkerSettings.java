package com.abcorg.knowledge.assist.chunking;

/**
 * Chunk size settings, in tokens. The chunker works internally in characters
 * (1 token is treated as 4 characters), so these numbers are exact budgets.
 */
public record ChunkerSettings(int maxTokens, int overlapTokens) {

    public ChunkerSettings {
        if (maxTokens < 50) {
            throw new IllegalArgumentException("maxTokens must be at least 50, was " + maxTokens);
        }
        if (overlapTokens < 0 || overlapTokens > maxTokens / 2) {
            throw new IllegalArgumentException("overlapTokens must be between 0 and maxTokens/2");
        }
    }

    public static ChunkerSettings defaults() {
        return new ChunkerSettings(500, 50);
    }

    int maxChars() {
        return maxTokens * 4;
    }

    int overlapChars() {
        return overlapTokens * 4;
    }
}
