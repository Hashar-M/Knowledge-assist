package com.abcorg.knowledge.assist.ingest;

import com.abcorg.knowledge.assist.chunking.Section;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Adapter between Chapter 6 and Chapter 7.
 * A SourceDocument holds flat text plus a flat list of headings (level + title).
 * The chunker wants sections: a heading path and the body text under it.
 * We find each heading inside the text, in order, and cut the text between them.
 */
public final class SectionSplitter {

    /** sections: ready for HeadingAwareChunker. unmatchedHeadings: headings we could not find in the text. */
    public record Result(List<Section> sections, int unmatchedHeadings) {
    }

    private record Hit(int level, String title, int start, int end) {
    }

    private SectionSplitter() {
    }

    public static Result split(SourceDocument doc) {
        String text = doc.text();

        List<Hit> hits = new ArrayList<>();
        int unmatched = 0;
        int cursor = 0;                       // headings appear in order, so never search backwards
        for (Heading h : doc.headings()) {
            int at = find(text, h.title(), cursor);
            if (at < 0) {
                unmatched++;
                continue;
            }
            int end = at + h.title().length();
            hits.add(new Hit(h.level(), h.title(), at, end));
            cursor = end;
        }

        List<Section> sections = new ArrayList<>();
        int firstStart = hits.isEmpty() ? text.length() : hits.get(0).start();
        addIfNotBlank(sections, preamblePath(doc), text.substring(0, firstStart));

        Deque<Hit> stack = new ArrayDeque<>();    // the current heading path, outermost at the bottom
        for (int i = 0; i < hits.size(); i++) {
            Hit h = hits.get(i);
            while (!stack.isEmpty() && stack.peek().level() >= h.level()) {
                stack.pop();
            }
            stack.push(h);

            int bodyEnd = (i + 1 < hits.size()) ? hits.get(i + 1).start() : text.length();
            List<String> path = new ArrayList<>();
            stack.descendingIterator().forEachRemaining(x -> path.add(x.title()));
            addIfNotBlank(sections, path, text.substring(h.end(), bodyEnd));
        }
        return new Result(sections, unmatched);
    }

    private static List<String> preamblePath(SourceDocument doc) {
        String t = doc.title();
        return (t == null || t.isBlank()) ? List.of() : List.of(t);
    }

    private static void addIfNotBlank(List<Section> out, List<String> path, String body) {
        if (!body.isBlank()) {
            out.add(new Section(path, body));
        }
    }

    /**
     * Finds a heading title at or after {@code from}.
     * Best match: the title alone on its own line (AsciiDoc and Markdown output).
     * Fallback: the first whitespace-bounded match (HTML text has no line breaks).
     */
    static int find(String text, String title, int from) {
        if (title == null || title.isBlank()) {
            return -1;
        }
        int fallback = -1;
        int idx = text.indexOf(title, from);
        while (idx >= 0) {
            int end = idx + title.length();
            boolean lineStart = idx == 0 || text.charAt(idx - 1) == '\n';
            boolean lineEnd = end == text.length() || text.charAt(end) == '\n';
            if (lineStart && lineEnd) {
                return idx;
            }
            boolean boundedBefore = idx == 0 || Character.isWhitespace(text.charAt(idx - 1));
            boolean boundedAfter = end == text.length() || Character.isWhitespace(text.charAt(end));
            if (fallback < 0 && boundedBefore && boundedAfter) {
                fallback = idx;
            }
            idx = text.indexOf(title, idx + 1);
        }
        return fallback;
    }
}

