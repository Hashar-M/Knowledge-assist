package com.abcorg.knowledge.assist.ingest;

import java.nio.charset.StandardCharsets;

public final class ChunkIds {
    private static final int ES_MAX_ID_BYTES = 512;   // Elasticsearch limit for _id

    private ChunkIds() {
    }

    public static String requireValid(String id) {
        if (id.getBytes(StandardCharsets.UTF_8).length > ES_MAX_ID_BYTES) {
            throw new IllegalArgumentException("Chunk id longer than " + ES_MAX_ID_BYTES + " bytes: " + id);
        }
        return id;
    }
}
