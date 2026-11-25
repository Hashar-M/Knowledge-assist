package com.abcorg.knowledge.assist.retrieval;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Builds the JSON body for POST /chunks/_search. Plain Java: no Spring, no HTTP, easy to unit test. */
public final class KnnRequest {

    /** Fields we read back. The embedding is left out on purpose: 1536 floats per hit is wasted bandwidth. */
    static final List<String> SOURCE_FIELDS = List.of(
            "doc_id", "product", "title", "heading_path", "source_url", "license", "content");

    private KnnRequest() {
    }

    public static Map<String, Object> build(float[] queryVector, int k, int numCandidates) {
        if (queryVector == null || queryVector.length == 0) {
            throw new IllegalArgumentException("query vector must not be empty");
        }
        if (k < 1) {
            throw new IllegalArgumentException("k must be at least 1 but was " + k);
        }
        if (numCandidates < k) {
            throw new IllegalArgumentException("num_candidates (" + numCandidates + ") must be >= k (" + k + ")");
        }

        Map<String, Object> knn = new LinkedHashMap<>();
        knn.put("field", "embedding");
        knn.put("query_vector", queryVector);
        knn.put("k", k);
        knn.put("num_candidates", numCandidates);
        // Chapter 12 adds "filter" here, inside knn, so filtering happens during the graph search.

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("knn", knn);
        body.put("size", k);
        body.put("_source", Map.of("includes", SOURCE_FIELDS));
        return body;
    }
}
