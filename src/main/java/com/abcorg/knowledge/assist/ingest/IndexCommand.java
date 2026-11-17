package com.abcorg.knowledge.assist.ingest;

import java.util.Map;

/** One bulk action: the target _id and the document body. */
public record IndexCommand(String id, Map<String, Object> source) {
}
