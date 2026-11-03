package com.abcorg.knowledge.assist.chunking;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Demo command-line tool: chunks one Markdown file and prints a statistics report.
 * Usage: java -cp target/classes com.example.rag.chunking.ChunkReport docs/sample-chunking.md [docId]
 */
public final class ChunkReport {

    private static final Pattern HEADING = Pattern.compile("^(#{1,6})\\s+(.*)$");

    private ChunkReport() {
    }

    public static void main(String[] args) throws IOException {
        args = new String[] {"docs/_index.md", "kubernetes:workloads:pods"}; // for IDE run
        System.out.println("ChunkReport: " + String.join(" ", args) + "\n");
        Path file = Path.of(args[0]);
        String docId = args.length > 1
                ? args[1]
                : file
                        .getFileName()
                        .toString();

        List<Section> sections = parseMarkdown(Files.readString(file));
        System.out.println("sections:\n");
        sections
                .stream()
                .map(Section::headingPath)
                .forEach(System.out::println);
        System.out.println("\n");
        List<Chunk> chunks = new HeadingAwareChunker(ChunkerSettings.defaults()).chunk(docId, sections);
        System.out.println("chunks:");
        chunks
                .stream()
                .map(c -> String.format("Chunk(id=%s, docId=%s, ordinal=%d, path=%s, tokens=%d, text=%s)", c.chunkId(),
                        c.docId(), c.ordinal(), c.headingPath(), c.approxTokens(), c.text()))
                .forEach(System.out::println);

        System.out.println(ChunkStats
                .of(chunks)
                .toMarkdown());
    }

    static List<Section> parseMarkdown(String markdown) {
        record Heading(int level, String title) {
        }

        List<Section> sections = new ArrayList<>();
        Deque<Heading> stack = new ArrayDeque<>();
        StringBuilder body = new StringBuilder();
        List<String> path = List.of();

        for (String line : markdown.split("\n", -1)) {
            Matcher m = HEADING.matcher(line);
            if (m.matches()) {
                addSection(sections, path, body);
                int level = m
                        .group(1)
                        .length();
                while (!stack.isEmpty() && stack
                        .peek()
                        .level() >= level) {
                    stack.pop();
                }
                stack.push(new Heading(level, m
                        .group(2)
                        .strip()));
                List<String> newPath = new ArrayList<>();
                stack
                        .descendingIterator()
                        .forEachRemaining(h -> newPath.add(h.title())); // outermost first
                path = newPath;
            } else {
                body
                        .append(line)
                        .append('\n');
            }
        }
        addSection(sections, path, body);
        return sections;
    }

    private static void addSection(List<Section> sections, List<String> path, StringBuilder body) {
        if (!body
                .toString()
                .isBlank()) {
            sections.add(new Section(path, body.toString()));
        }
        body.setLength(0);
    }
}

