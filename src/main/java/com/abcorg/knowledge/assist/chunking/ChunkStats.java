package com.abcorg.knowledge.assist.chunking;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Counts and a histogram of chunk sizes, in estimated tokens, bucketed by 100. */
public record ChunkStats(int count, int min, int median, int max, Map<String, Integer> histogram) {

    public static ChunkStats of(List<Chunk> chunks) {
        List<Integer> sizes = chunks.stream().map(Chunk::approxTokens).sorted().toList();
        Map<String, Integer> hist = new TreeMap<>();
        for (int s : sizes) {
            hist.merge(bucket(s), 1, Integer::sum);
        }
        if (sizes.isEmpty()) {
            return new ChunkStats(0, 0, 0, 0, hist);
        }
        return new ChunkStats(
                sizes.size(),
                sizes.get(0),
                sizes.get(sizes.size() / 2),
                sizes.get(sizes.size() - 1),
                hist);
    }

    private static String bucket(int tokens) {
        int lower = (tokens / 100) * 100;
        return String.format("%04d-%04d", lower, lower + 99);
    }

    public String toMarkdown() {
        StringBuilder sb = new StringBuilder("# Chunk statistics\n\n");
        sb.append("- Chunks: ").append(count).append('\n');
        sb.append("- Tokens (min / median / max): ")
                .append(min).append(" / ").append(median).append(" / ").append(max).append("\n\n");
        sb.append("| Tokens | Chunks |\n|---|---|\n");
        histogram.forEach((range, n) -> sb.append("| ").append(range).append(" | ").append(n).append(" |\n"));
        return sb.toString();
    }
}

