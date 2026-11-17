package com.abcorg.knowledge.assist.ingest;

import com.abcorg.knowledge.assist.chunking.Chunk;

import java.util.LinkedHashMap;
import java.util.Map;

public final class IndexDocuments {

    private IndexDocuments() {
    }

    public static Map<String, Object> toSource(ChunkJob job, float[] vector) {
        SourceDocument d = job.doc();
        Chunk c = job.chunk();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("doc_id", d.docId());
        m.put("product", d.product());
        m.put("source_url", d.sourceUrl());
        m.put("source_commit", d.sourceCommit());
        m.put("license", d.license());
        m.put("title", d.title());
        m.put("heading_path", String.join(" > ", c.headingPath()));
        m.put("chunk_index", c.ordinal());
        m.put("token_count", c.approxTokens());
        m.put("content", c.text());          // includes the breadcrumb (Chapter 7)
        m.put("embedding", vector);
        return m;
    }
}
