package com.abcorg.knowledge.assist.ingest;

/** One unit of ingestion work before embedding. */
public record ChunkJob(String id, String text, SourceDocument doc) {
}
