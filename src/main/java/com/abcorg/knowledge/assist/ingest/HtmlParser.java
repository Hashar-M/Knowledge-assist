package com.abcorg.knowledge.assist.ingest;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * HTML reader (jsoup). Removes navigation and scripts, then reads the main content only.
 */
public final class HtmlParser implements DocumentParser {

    private static final Pattern SSI_INCLUDE = Pattern.compile("<!--#include[^>]*-->");
    private static final String NOISE = "script, style, noscript, nav, header, footer";

    @Override
    public boolean supports(String fileName) {
        return fileName.endsWith(".html");
    }

    @Override
    public ParsedDocument parse(String raw) {
        List<String> warnings = new ArrayList<>();
        Matcher ssi = SSI_INCLUDE.matcher(raw);
        while (ssi.find()) {
            warnings.add("skipped " + ssi.group());
        }
        String cleaned = SSI_INCLUDE
                .matcher(raw)
                .replaceAll("");

        Document doc = Jsoup.parse(cleaned);
        doc
                .select(NOISE)
                .remove();
        Element main = findMain(doc);

        List<Heading> headings = new ArrayList<>();
        for (Element h : main.select("h1, h2, h3, h4, h5, h6")) {
            headings.add(new Heading(h
                    .tagName()
                    .charAt(1) - '0', h
                    .text()
                    .strip()));
        }

        String title = doc
                .title()
                .isBlank()
                ? null
                : doc
                        .title()
                        .strip();
        return new ParsedDocument(title, TextUtil.normalize(main.text()), headings, warnings);
    }

    /**
     * Pick the first main-content container that exists. Order matters: first match wins.
     */
    private static Element findMain(Document doc) {
        for (String selector : List.of("main", "article", "#content", "div.content")) {
            Element e = doc.selectFirst(selector);
            if (e != null) {
                return e;
            }
        }
        return doc.body();
    }
}
