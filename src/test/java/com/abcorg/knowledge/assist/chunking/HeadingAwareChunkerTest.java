package com.abcorg.knowledge.assist.chunking;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HeadingAwareChunkerTest {

    // 200 tokens = 800 characters per chunk, 40 tokens = 160 characters of overlap
    private final HeadingAwareChunker chunker = new HeadingAwareChunker(new ChunkerSettings(200, 40));

    @Test
    void shortSectionBecomesOneChunkWithBreadcrumb() {
        List<Chunk> chunks = chunker.chunk("spring-boot:actuator", List.of(
                new Section(List.of("Actuator", "Endpoints"), "Call /actuator/health to check status.")));

        assertEquals(1, chunks.size());
        assertTrue(chunks.get(0).text().startsWith("Actuator > Endpoints\n\n"));
        assertEquals("spring-boot:actuator#0", chunks.get(0).chunkId());
    }

    @Test
    void chunksNeverCrossSectionBoundaries() {
        List<Chunk> chunks = chunker.chunk("doc", List.of(
                new Section(List.of("A"), "alpha text here."),
                new Section(List.of("B"), "beta text here.")));

        assertEquals(2, chunks.size());
        assertFalse(chunks.get(0).text().contains("beta"));
        assertEquals(List.of("B"), chunks.get(1).headingPath());
    }

    @Test
    void everyChunkFitsTheTokenBudget() {
        // No sentence punctuation, so the chunker must hard-split a single very long sentence.
        String body = words(3000);

        List<Chunk> chunks = chunker.chunk("doc", List.of(new Section(List.of("Big"), body)));

        assertTrue(chunks.size() > 5);
        for (Chunk c : chunks) {
            assertTrue(c.approxTokens() <= 200, "chunk too big: " + c.approxTokens());
        }
    }

    @Test
    void consecutiveChunksInASectionShareOverlap() {
        HeadingAwareChunker withOverlap = new HeadingAwareChunker(new ChunkerSettings(200, 60));
        StringBuilder body = new StringBuilder();
        for (int i = 1; i <= 12; i++) {
            body.append(marker(i)).append(' ').append("x".repeat(180)).append("\n\n");
        }

        List<Chunk> chunks = withOverlap.chunk("doc", List.of(new Section(List.of("S"), body.toString())));

        assertTrue(chunks.size() >= 3);
        assertTrue(chunks.get(0).text().contains(marker(4)));
        assertTrue(chunks.get(1).text().contains(marker(4)), "overlap paragraph missing from next chunk");
        assertTrue(chunks.get(1).text().contains(marker(5)));
    }

    @Test
    void fencedCodeBlockStaysWholeWhenItFits() {
        String body = "Intro sentence.\n\n```java\nvar x = 1;\nvar y = 2;\n```\n\nOutro sentence.";

        List<Chunk> chunks = chunker.chunk("doc", List.of(new Section(List.of("Code"), body)));

        assertTrue(chunks.stream().anyMatch(c ->
                c.text().contains("```java\nvar x = 1;\nvar y = 2;\n```")));
    }

    @Test
    void chunkIdsAreDeterministic() {
        List<Section> sections = List.of(new Section(List.of("A"), words(400)));

        List<String> first = chunker.chunk("doc", sections).stream().map(Chunk::chunkId).toList();
        List<String> second = chunker.chunk("doc", sections).stream().map(Chunk::chunkId).toList();

        assertFalse(first.isEmpty());
        assertEquals(first, second);
    }

    @Test
    void emptySectionProducesNoChunks() {
        List<Chunk> chunks = chunker.chunk("doc", List.of(new Section(List.of("Empty"), "   \n\n  ")));

        assertTrue(chunks.isEmpty());
    }

    @Test
    void rejectsOverlapLargerThanHalfTheChunk() {
        assertThrows(IllegalArgumentException.class, () -> new ChunkerSettings(200, 150));
    }

    @Test
    void statsCountChunksAndBucketSizes() {
        List<Chunk> chunks = chunker.chunk("doc", List.of(new Section(List.of("A"), "short text.")));

        ChunkStats stats = ChunkStats.of(chunks);

        assertEquals(1, stats.count());
        assertEquals(Map.of("0000-0099", 1), stats.histogram());
    }

    private static String marker(int i) {
        return String.format("[[P%02d]]", i);
    }

    private static String words(int n) {
        return IntStream.range(0, n).mapToObj(i -> "word" + i).collect(Collectors.joining(" "));
    }
}

