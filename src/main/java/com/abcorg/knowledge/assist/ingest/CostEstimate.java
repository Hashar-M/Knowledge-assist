package com.abcorg.knowledge.assist.ingest;

import com.abcorg.knowledge.assist.config.IngestProperties;

import java.util.List;

public record CostEstimate(long chunks, long tokens, double usd) {

    /** Uses Chapter 7's token estimate (chars / 4). The real count is reported after the run. */
    public static CostEstimate of(List<ChunkJob> jobs, IngestProperties p) {
        long tokens = jobs.stream().mapToLong(j -> j.chunk().approxTokens()).sum();
        return new CostEstimate(jobs.size(), tokens, tokens / 1_000_000.0 * p.pricePerMillionTokens());
    }
}
