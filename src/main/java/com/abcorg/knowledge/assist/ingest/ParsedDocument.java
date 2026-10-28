package com.abcorg.knowledge.assist.ingest;

import java.util.List;

/** Output of any parser: clean text plus structure, before we add metadata. */
public record ParsedDocument(
        String title,
        String text,
        List<Heading> headings,
        List<String> warnings) {}
