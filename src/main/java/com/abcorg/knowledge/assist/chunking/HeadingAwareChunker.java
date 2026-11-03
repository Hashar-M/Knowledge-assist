package com.abcorg.knowledge.assist.chunking;

import java.util.ArrayList;
import java.util.List;

/**
 * Heading-aware chunker.
 * - Chunks never cross section boundaries.
 * - Blocks are packed greedily up to the size budget.
 * - The last blocks of a chunk are repeated at the start of the next one (overlap).
 * - Each chunk text starts with a breadcrumb of its headings; the breadcrumb counts toward the budget.
 */
public final class HeadingAwareChunker {

    private static final String SEP = "\n\n";

    private final ChunkerSettings settings;

    public HeadingAwareChunker(ChunkerSettings settings) {
        this.settings = settings;
    }

    public List<Chunk> chunk(String docId, List<Section> sections) {
        List<Chunk> out = new ArrayList<>();
        int ordinal = 0;

        for (Section section : sections) {
            String crumb = String.join(" > ", section.headingPath());
            String prefix = crumb.isEmpty() ? "" : crumb + SEP;
            int budget = settings.maxChars() - prefix.length();
            if (budget <= 0) {
                throw new IllegalArgumentException("Heading path too long for chunk size: " + crumb);
            }

            for (String body : pack(Blocks.split(section.body()), budget)) {
                String text = prefix + body;
                out.add(new Chunk(
                        docId + "#" + ordinal,
                        docId,
                        ordinal,
                        section.headingPath(),
                        text,
                        TokenEstimator.estimate(text)));
                ordinal++;
            }
        }
        return out;
    }

    /** Packs pieces into bodies of at most {@code budget} characters, with overlap between them. */
    private List<String> pack(List<Blocks.Block> blocks, int budget) {
        List<String> pieces = new ArrayList<>();
        for (Blocks.Block block : blocks) {
            pieces.addAll(fit(block, budget));
        }

        List<String> out = new ArrayList<>();
        List<String> current = new ArrayList<>();
        for (String piece : pieces) {
            boolean wouldOverflow = !current.isEmpty()
                    && String.join(SEP, current).length() + SEP.length() + piece.length() > budget;
            if (wouldOverflow) {
                out.add(String.join(SEP, current));
                current = tail(current, budget - SEP.length() - piece.length());
            }
            current.add(piece);
        }
        if (!current.isEmpty()) {
            out.add(String.join(SEP, current));
        }
        return out;
    }

    /** The trailing pieces to repeat at the start of the next chunk (the overlap). */
    private List<String> tail(List<String> pieces, int limit) {
        int max = Math.min(limit, settings.overlapChars());
        List<String> tail = new ArrayList<>();
        int len = 0;
        for (int i = pieces.size() - 1; i >= 0; i--) {
            String p = pieces.get(i);
            int added = tail.isEmpty() ? p.length() : SEP.length() + p.length();
            if (len + added > max) {
                break;
            }
            tail.add(0, p);
            len += added;
        }
        return tail;
    }

    /** A block that fits stays whole. A bigger block is split at sentence or line boundaries. */
    private List<String> fit(Blocks.Block block, int budget) {
        if (block.text().length() <= budget) {
            return List.of(block.text());
        }

        String[] units = block.code()
                ? block.text().split("\n", -1)
                : block.text().split("(?<=[.!?])\\s+");
        String joiner = block.code() ? "\n" : " ";

        List<String> pieces = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (String unit : units) {
            for (String part : hardSplit(unit, budget)) {
                if (cur.length() > 0 && cur.length() + joiner.length() + part.length() > budget) {
                    pieces.add(cur.toString());
                    cur.setLength(0);
                }
                if (cur.length() > 0) {
                    cur.append(joiner);
                }
                cur.append(part);
            }
        }
        if (cur.length() > 0) {
            pieces.add(cur.toString());
        }
        return pieces;
    }

    /** Last resort for a single unit (one long sentence or line) that is still too big. */
    private static List<String> hardSplit(String unit, int budget) {
        List<String> parts = new ArrayList<>();
        for (int i = 0; i < unit.length(); i += budget) {
            parts.add(unit.substring(i, Math.min(unit.length(), i + budget)));
        }
        return parts;
    }
}
