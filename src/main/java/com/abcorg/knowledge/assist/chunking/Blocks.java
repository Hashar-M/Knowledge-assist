package com.abcorg.knowledge.assist.chunking;

import java.util.ArrayList;
import java.util.List;

/** Splits a section body into blocks: prose paragraphs and fenced code blocks. */
final class Blocks {

    record Block(boolean code, String text) {
    }

    private Blocks() {
    }

    static List<Block> split(String body) {
        List<Block> blocks = new ArrayList<>();
        StringBuilder buf = new StringBuilder();
        boolean inCode = false;
        String fence = null;

        for (String line : body.split("\n", -1)) {
            String trimmed = line.strip();
            if (inCode) {
                buf.append(line).append('\n');
                if (trimmed.equals(fence)) {
                    flush(blocks, buf, true);
                    inCode = false;
                }
            } else if (trimmed.startsWith("```") || trimmed.equals("----")) {
                flush(blocks, buf, false);
                fence = trimmed.startsWith("```") ? "```" : "----";
                inCode = true;
                buf.append(line).append('\n');
            } else if (trimmed.isEmpty()) {
                flush(blocks, buf, false);
            } else {
                buf.append(line).append('\n');
            }
        }
        flush(blocks, buf, inCode); // an unclosed fence is kept as code
        return blocks;
    }

    private static void flush(List<Block> blocks, StringBuilder buf, boolean code) {
        // Code keeps its leading indentation (Python and YAML depend on it).
        String text = code ? buf.toString().stripTrailing() : buf.toString().strip();
        if (!text.isEmpty()) {
            blocks.add(new Block(code, text));
        }
        buf.setLength(0);
    }
}