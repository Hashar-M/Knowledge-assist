package com.abcorg.knowledge.assist.ask;

import com.abcorg.knowledge.assist.retrieval.RetrievedChunk;
import java.util.ArrayList;
import java.util.List;

/**
 * Turns ranked chunks into the numbered excerpts that go into the prompt, inside a token budget.
 * Plain Java. Tokens are estimated as characters / 4, the same rule as Chapter 7.
 */
public final class ContextBuilder {

    /** text: the excerpts. used: the chunks that fit, in rank order (their position + 1 is their number). dropped: chunks that did not fit. */
    public record Built(String text, List<RetrievedChunk> used, int dropped) {
    }

    private ContextBuilder() {
    }

    public static Built build(List<RetrievedChunk> ranked, int maxTokens) {
        StringBuilder out = new StringBuilder();
        List<RetrievedChunk> used = new ArrayList<>();
        int spent = 0;
        for (RetrievedChunk chunk : ranked) {
            String block = block(used.size() + 1, chunk);
            int cost = estimateTokens(block);
            if (spent + cost > maxTokens) {
                break;                      // keep the best-ranked prefix; no gaps in the numbering
            }
            if (!used.isEmpty()) {
                out.append("\n\n");
            }
            out.append(block);
            used.add(chunk);
            spent += cost;
        }
        return new Built(out.toString(), List.copyOf(used), ranked.size() - used.size());
    }

    static String block(int number, RetrievedChunk chunk) {
        return "[" + number + "] " + chunk.product() + ": " + chunk.title() + "\n" + neutralise(chunk.content());
    }

    /** Document text must not be able to close our <context> block. A first defence only; Chapter 23 goes further. */
    static String neutralise(String text) {
        return text.replaceAll("(?i)<(/?)context>", "&lt;$1context&gt;");
    }

    static int estimateTokens(String text) {
        return (text.length() + 3) / 4;
    }
}
