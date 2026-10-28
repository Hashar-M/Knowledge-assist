package com.abcorg.knowledge.assist.ingest;

/** Turns the raw text of one file into a ParsedDocument. One implementation per format. */
public interface DocumentParser {
    boolean supports(String fileName);
    ParsedDocument parse(String rawText);
}
