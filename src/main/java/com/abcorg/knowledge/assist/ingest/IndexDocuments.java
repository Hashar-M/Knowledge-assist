package com.abcorg.knowledge.assist.ingest;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class IndexDocuments {

    /** Must match the vector field name in chunks-mapping.json (Chapter 5). // adapt */
    static final String VECTOR_FIELD = "embedding";

    private IndexDocuments() {
    }

    public static Map<String, Object> toSource(ChunkJob job, float[] vector) {
        SourceDocument d = job.doc();
        List<Float> vec = new ArrayList<>(vector.length);
        for (float f : vector) {
            vec.add(f);
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("product", d.product());
        m.put("doc_id", d.docId());
        m.put("source_url", d.sourceUrl());
        m.put("source_commit", d.sourceCommit());
        m.put("license", d.license());
        m.put("content", job.text());
        m.put(VECTOR_FIELD, vec);
        return m;
    }
}
