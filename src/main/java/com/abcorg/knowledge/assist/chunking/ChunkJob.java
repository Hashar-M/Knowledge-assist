package com.abcorg.knowledge.assist.chunking;

import com.abcorg.knowledge.assist.ingest.SourceDocument;

/** One chunk plus the document it came from. The chunk ID is the Elasticsearch _id. */
public record ChunkJob(SourceDocument doc, Chunk chunk) {
    public String id() { return chunk.chunkId(); }
    public String text() { return chunk.text(); }
}
