package com.abcorg.knowledge.assist.ingest;

/** Per-product settings read from data/manifest.yaml. */
public record ProductMeta(
        String product,
        String license,
        String urlTemplate,
        String commit,
        boolean localOnly) {}
