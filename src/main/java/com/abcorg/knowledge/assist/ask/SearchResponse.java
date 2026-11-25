package com.abcorg.knowledge.assist.ask;

import java.util.List;

/** What POST /search returns: retrieval only, no model call, no generation cost. */
public record SearchResponse(List<AskResponse.Source> sources, long embedMs, long searchMs) {
}
