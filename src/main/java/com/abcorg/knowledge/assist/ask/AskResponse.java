package com.abcorg.knowledge.assist.ask;

import com.abcorg.knowledge.assist.retrieval.RetrievedChunk;
import java.util.List;

/** What POST /ask returns: the answer, the sources it was given (the receipts), and what it cost. */
public record AskResponse(String answer, List<Source> sources, Usage usage, Timings timings, Models models) {

    /** One source. rank is the number the model sees and may cite: [1], [2], ... */
    public record Source(int rank, String chunkId, String product, String title, String headingPath,
                         String sourceUrl, String license, double score, String snippet) {

        public static Source of(int rank, RetrievedChunk c, int snippetChars) {
            return new Source(rank, c.chunkId(), c.product(), c.title(), c.headingPath(),
                    c.sourceUrl(), c.license(), c.score(), snippet(c.content(), snippetChars));
        }

        /** Short excerpt for the API response. Never the whole chunk: see the licence note for Elasticsearch docs. */
        static String snippet(String text, int max) {
            String flat = text == null ? "" : text.replaceAll("\\s+", " ").trim();
            if (flat.length() <= max) {
                return flat;
            }
            int cut = flat.lastIndexOf(' ', max);
            if (cut < max / 2) {
                cut = max;
            }
            return flat.substring(0, cut).trim() + "…";
        }
    }

    public record Usage(int promptTokens, int completionTokens, int totalTokens) {
        public static final Usage NONE = new Usage(0, 0, 0);
    }

    public record Timings(long embedMs, long searchMs, long generateMs, long totalMs) {
    }

    public record Models(String chat, String embedding) {
    }
}
