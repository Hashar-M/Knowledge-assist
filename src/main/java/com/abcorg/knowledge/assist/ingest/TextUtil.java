package com.abcorg.knowledge.assist.ingest;

/** Shared clean-up applied to every parser's output. */
final class TextUtil {

    private TextUtil() {}

    static String normalize(String s) {
        return s.replace("\r", "")
                .replaceAll("(?<=\\S)[ \\t\\u00A0]{2,}", " ")  // collapse inner runs of spaces
                .replaceAll("[ \\t]+\\n", "\n")                // drop trailing spaces
                .replaceAll("\\n{3,}", "\n\n")                 // at most one blank line
                .strip();
    }
}
