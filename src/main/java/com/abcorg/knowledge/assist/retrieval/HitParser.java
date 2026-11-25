package com.abcorg.knowledge.assist.retrieval;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Turns the parsed JSON of an Elasticsearch search response into chunks. Order is kept: best score first. */
public final class HitParser {

    private HitParser() {
    }

    public static List<RetrievedChunk> parse(Map<String, Object> response) {
        if (response == null || !(response.get("hits") instanceof Map<?, ?> outer)
                || !(outer.get("hits") instanceof List<?> hits)) {
            return List.of();
        }
        List<RetrievedChunk> chunks = new ArrayList<>(hits.size());
        for (Object o : hits) {
            if (!(o instanceof Map<?, ?> hit) || !(hit.get("_source") instanceof Map<?, ?> src)) {
                continue;
            }
            chunks.add(new RetrievedChunk(
                    text(hit.get("_id")),
                    text(src.get("doc_id")),
                    text(src.get("product")),
                    text(src.get("title")),
                    text(src.get("heading_path")),
                    text(src.get("source_url")),
                    text(src.get("license")),
                    text(src.get("content")),
                    hit.get("_score") instanceof Number n ? n.doubleValue() : 0.0));
        }
        return List.copyOf(chunks);
    }

    private static String text(Object o) {
        return o == null ? "" : o.toString();
    }
}
