package com.abcorg.knowledge.assist.chunking;

import java.util.List;

/** One section of a source page: the heading path (outermost first) and the text under it. */
public record Section(List<String> headingPath, String body) {

    public Section {
        headingPath = List.copyOf(headingPath);
        body = (body == null) ? "" : body;
    }
}
