package com.abcorg.knowledge.assist.ingest;

import java.util.List;

/** Everything the rest of the pipeline needs to know about one source page. */
public record SourceDocument(
        String docId,          // deterministic, e.g. "spring-boot:actuator/endpoints.adoc"
        String product,        // e.g. "spring-boot"
        String title,
        String text,
        List<Heading> headings,
        String sourceUrl,      // public URL for citations (Chapter 13)
        String license,        // e.g. "Apache-2.0"
        String sourceCommit,   // pinned SHA from the manifest
        String contentSha256,  // hash of the raw file bytes (Chapter 17)
        List<String> warnings) {}
