package com.abcorg.knowledge.assist.ingest;

import org.commonmark.node.AbstractVisitor;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.text.TextContentRenderer;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Markdown reader (commonmark-java). Strips Hugo front matter and shortcodes first.
 */
public final class MarkdownParser implements DocumentParser {

    private static final Pattern FRONT_MATTER = Pattern.compile("\\A---\\R(.*?)\\R---\\R", Pattern.DOTALL);
    private static final Pattern FM_TITLE = Pattern.compile("(?m)^title:\\s*\"?([^\"\\r\\n]+?)\"?\\s*$");
    private static final Pattern HUGO_SHORTCODE = Pattern.compile("\\{\\{[<%].*?[>%]\\}\\}");

    @Override
    public boolean supports(String fileName) {
        return fileName.endsWith(".md");
    }

    @Override
    public ParsedDocument parse(String raw) {
        String frontMatterTitle = null;
        String body = raw;

        Matcher fm = FRONT_MATTER.matcher(raw);
        if (fm.find()) {
            Matcher t = FM_TITLE.matcher(fm.group(1));
            if (t.find()) {
                frontMatterTitle = t
                        .group(1)
                        .strip();
            }
            body = raw.substring(fm.end());
        }
        body = HUGO_SHORTCODE
                .matcher(body)
                .replaceAll("");

        Node doc = Parser
                .builder()
                .build()
                .parse(body);
        TextContentRenderer renderer = TextContentRenderer
                .builder()
                .build();

        List<Heading> headings = new ArrayList<>();
        doc.accept(new AbstractVisitor() {
            @Override
            public void visit(org.commonmark.node.Heading h) {
                headings.add(new Heading(h.getLevel(), renderer
                        .render(h)
                        .strip()));
            }
        });

        String title = frontMatterTitle;
        if (title == null) {
            title = headings
                    .stream()
                    .filter(h -> h.level() == 1)
                    .map(Heading::title)
                    .findFirst()
                    .orElse(null);
        }
        return new ParsedDocument(title, TextUtil.normalize(renderer.render(doc)), headings, List.of());
    }
}
