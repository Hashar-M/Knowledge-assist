package com.abcorg.knowledge.assist.ingest;

/** One heading in a document. level 1 = page title, 2 = section, 3 = subsection... */
public record Heading(int level, String title) {}
