package com.abcorg.knowledge.assist.ingest;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Minimal AsciiDoc reader for Antora pages.
 * Keeps titles, headings and text. Drops macros we cannot resolve and records them as warnings.
 * It does NOT follow include:: (that would need the whole Antora module), so it never reads
 * other files. Safety first.
 */
public final class AsciiDocParser implements DocumentParser {

    private static final Pattern HEADING = Pattern.compile("^(={1,6})\\s+(.+?)\\s*$");
    private static final Pattern IMAGE = Pattern.compile("^image::.*$");
    private static final Pattern DOC_ATTRIBUTE = Pattern.compile("^:[\\w-]+:.*$");
    private static final Pattern BLOCK_ATTRIBUTE = Pattern.compile("^\\[[^\\]]*]\\s*$");
    private static final Pattern XREF = Pattern.compile("xref:[^\\[\\s]+\\[([^\\]]*)]");
    private static final Pattern LINK = Pattern.compile("(?:link:)?(https?://[^\\[\\s]+)\\[([^\\]]*)]");

    @Override
    public boolean supports(String fileName) {
        return fileName.endsWith(".adoc");
    }

    @Override
    public ParsedDocument parse(String raw) {
        List<Heading> headings = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        StringBuilder out = new StringBuilder();
        String openDelimiter = null;   // "----" or "...." while inside a listing block

        for (String line : raw.split("\\R", -1)) {
            if (openDelimiter != null) {
                if (line.equals(openDelimiter)) {
                    openDelimiter = null;
                } else {
                    out.append(line).append('\n');   // keep code text, never read it as a heading
                }
                continue;
            }
            if (line.equals("----") || line.equals("....")) {
                openDelimiter = line;
                continue;
            }
            if (line.startsWith("include::") || line.startsWith("include-code::")) {
                warnings.add("skipped " + line.strip());
                continue;
            }
            if (IMAGE.matcher(line).matches()
                    || DOC_ATTRIBUTE.matcher(line).matches()
                    || BLOCK_ATTRIBUTE.matcher(line).matches()) {
                continue;
            }
            Matcher h = HEADING.matcher(line);
            if (h.matches()) {
                String title = cleanInline(h.group(2));
                headings.add(new Heading(h.group(1).length(), title));   // "=" count = level
                out.append(title).append("\n\n");
                continue;
            }
            out.append(cleanInline(line)).append('\n');
        }

        String title = headings.stream()
                .filter(x -> x.level() == 1)
                .map(Heading::title)
                .findFirst()
                .orElse(null);
        return new ParsedDocument(title, TextUtil.normalize(out.toString()), headings, warnings);
    }

    static String cleanInline(String s) {
        String r = XREF.matcher(s).replaceAll("$1");   // xref:path[text] -> text
        return LINK.matcher(r).replaceAll("$2");       // https://url[text] -> text
    }
}
