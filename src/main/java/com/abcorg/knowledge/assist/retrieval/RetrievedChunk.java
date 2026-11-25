package com.abcorg.knowledge.assist.retrieval;

/**
 * One chunk returned by the kNN search.
 * score is Elasticsearch's _score. For a cosine field it is (1 + cosine) / 2, so it lies between 0 and 1.
 */
public record RetrievedChunk(
        String chunkId,
        String docId,
        String product,
        String title,
        String headingPath,
        String sourceUrl,
        String license,
        String content,
        double score) {
}
