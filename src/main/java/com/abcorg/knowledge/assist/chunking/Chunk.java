package com.abcorg.knowledge.assist.chunking;

import java.util.List;

/** One searchable unit. The text starts with a breadcrumb of its headings. */
public record Chunk(
        String chunkId,
        String docId,
        int ordinal,
        List<String> headingPath,
        String text,
        int approxTokens) {
}