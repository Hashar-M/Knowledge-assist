package com.abcorg.knowledge.assist.chunking;

import java.util.ArrayList;
import java.util.List;

/**
 * Heading-aware chunker with token limits and overlap.
 * 
 * This chunker respects document structure by ensuring:
 * - Chunks never cross section boundaries (keeps context together)
 * - Each chunk starts with a breadcrumb of its heading path
 * - Chunk size stays within token budget (default: 500 tokens)
 * - Consecutive chunks overlap (default: 50 tokens) to preserve context
 * 
 * Example Flow:
 * 
 * INPUT: Section from Spring Boot documentation
 * --------
 * Heading Path: ["Actuator", "Web Exposure", "Endpoints"]
 * Body Text: "HTTP endpoints that expose application metrics..."
 *            "Common endpoints include /actuator/health..."
 *            "Custom endpoints can be created..." (350+ tokens)
 * 
 * STAGE 1 - Tokenize heading path into breadcrumb:
 * "Actuator > Web Exposure > Endpoints" (28 chars)
 * This counts toward the token budget and appears at the start of every chunk from this section
 * 
 * STAGE 2 - Split body into logical blocks:
 * Split by paragraphs/sentences to avoid splitting mid-sentence
 * Block 1: "HTTP endpoints that expose application metrics..."
 * Block 2: "Common endpoints include /actuator/health..."
 * Block 3: "Custom endpoints can be created..."
 * 
 * STAGE 3 - Pack blocks into chunks (greedy algorithm):
 * With budget of 500 tokens (minus breadcrumb = ~472 chars):
 * 
 * Chunk 1: "Actuator > Web Exposure > Endpoints\n\n
 *          HTTP endpoints that expose application metrics...
 *          Common endpoints include /actuator/health..."
 *          (480 chars total, fits in budget)
 * 
 * Chunk 2: "Actuator > Web Exposure > Endpoints\n\n
 *          [OVERLAP: "Common endpoints include /actuator/health..."]\n\n
 *          Custom endpoints can be created..."
 *          (The last block of Chunk 1 is repeated here as overlap)
 * 
 * OUTPUT: List of Chunk objects with:
 * - chunkId: "spring-boot:actuator/endpoints.adoc#0"
 * - docId: "spring-boot:actuator/endpoints.adoc"
 * - ordinal: 0, 1, 2... (position in document)
 * - headingPath: ["Actuator", "Web Exposure", "Endpoints"]
 * - text: Full text with breadcrumb + content
 * - tokenEstimate: Estimated tokens for this chunk
 * 
 * Benefits of overlap:
 * - Preserves context when searching across chunk boundaries
 * - Helps LLM understand chunk relationships
 * - Similar to sliding windows in signal processing
 */
public final class HeadingAwareChunker {

    private static final String SEP = "\n\n";

    private final ChunkerSettings settings;

    public HeadingAwareChunker(ChunkerSettings settings) {
        this.settings = settings;
    }

    /**
     * Chunks a list of sections from a single document.
     * 
     * Each section's chunks are independent (not shared), but all inherit
     * the same heading breadcrumb from their section.
     * 
     * @param docId unique document identifier
     * @param sections logical sections from the document (each with a heading path)
     * @return ordered list of chunks, each with breadcrumb prefix
     */
    public List<Chunk> chunk(String docId, List<Section> sections) {
        List<Chunk> out = new ArrayList<>();
        int ordinal = 0;

        for (Section section : sections) {
            // Create breadcrumb: "Heading1 > Heading2 > Heading3"
            // This provides semantic context for search results and RAG
            String crumb = String.join(" > ", section.headingPath());
            
            // Prefix all chunks from this section with the breadcrumb
            // Format: "Heading1 > Heading2 > Heading3\n\n[content]"
            String prefix = crumb.isEmpty() ? "" : crumb + SEP;
            
            // Calculate remaining budget for actual content
            // Settings.maxChars is total limit (e.g., 2000 chars ≈ 500 tokens)
            // Subtract the breadcrumb length to get content budget
            int budget = settings.maxChars() - prefix.length();
            if (budget <= 0) {
                throw new IllegalArgumentException("Heading path too long for chunk size: " + crumb);
            }

            // Split section body into chunks, each with the breadcrumb prefix
            for (String body : pack(Blocks.split(section.body()), budget)) {
                String text = prefix + body;
                out.add(new Chunk(
                        docId + "#" + ordinal,                    // unique chunk ID
                        docId,                                     // parent document
                        ordinal,                                   // position in document
                        section.headingPath(),                     // heading hierarchy
                        text,                                      // full searchable text
                        TokenEstimator.estimate(text)));           // token count
                ordinal++;
            }
        }
        return out;
    }

    /**
     * Packs blocks (paragraphs/sentences) into chunks with overlap.
     * 
     * Algorithm:
     * 1. Fit each block into the budget (split if too big)
     * 2. Greedily add blocks to current chunk
     * 3. When adding a block would overflow:
     *    - Emit current chunk
     *    - Start new chunk with tail of previous chunk (overlap)
     *    - Add the overflowing block
     * 
     * @param blocks logical units (paragraphs, sentences, code lines)
     * @param budget max characters for one packed chunk
     * @return list of packed strings, each ≤ budget chars
     */
    private List<String> pack(List<Blocks.Block> blocks, int budget) {
        // First, fit all blocks (split long blocks that exceed budget)
        List<String> pieces = new ArrayList<>();
        for (Blocks.Block block : blocks) {
            pieces.addAll(fit(block, budget));
        }

        // Then pack pieces greedily into chunks with overlap
        List<String> out = new ArrayList<>();
        List<String> current = new ArrayList<>();
        
        for (String piece : pieces) {
            // Check if adding this piece would overflow budget
            boolean wouldOverflow = !current.isEmpty()
                    && String.join(SEP, current).length() + SEP.length() + piece.length() > budget;
            
            if (wouldOverflow) {
                // Emit current chunk and start a new one with overlap tail
                out.add(String.join(SEP, current));
                
                // Tail becomes the overlap: trailing pieces that will repeat in next chunk
                // This preserves context across chunk boundaries
                current = tail(current, budget - SEP.length() - piece.length());
            }
            current.add(piece);
        }
        
        // Don't forget the last chunk
        if (!current.isEmpty()) {
            out.add(String.join(SEP, current));
        }
        return out;
    }

    /**
     * Extracts trailing pieces for overlap in next chunk.
     * 
     * When transitioning from one chunk to the next, we want to repeat
     * some content to maintain context. This method extracts up to
     * `limit` chars of trailing content, working backwards from the end.
     * 
     * @param pieces list of content pieces (from current chunk)
     * @param limit max chars to extract (space budget in next chunk for the new piece)
     * @return trailing pieces that fit within limit and overlap setting
     */
    private List<String> tail(List<String> pieces, int limit) {
        // Don't exceed the configured overlap or the budget
        int max = Math.min(limit, settings.overlapChars());
        
        List<String> tail = new ArrayList<>();
        int len = 0;
        
        // Work backwards through pieces, collecting as much as fits
        for (int i = pieces.size() - 1; i >= 0; i--) {
            String p = pieces.get(i);
            int added = tail.isEmpty() ? p.length() : SEP.length() + p.length();
            if (len + added > max) {
                break;
            }
            tail.add(0, p);  // Add to front (we're working backwards)
            len += added;
        }
        return tail;
    }

    /**
     * Fits a block into the size budget.
     * 
     * - If block ≤ budget: return as-is
     * - If block > budget: split intelligently
     *   - Code blocks: split by newlines
     *   - Text blocks: split by sentence boundaries (regex: [.!?] followed by space)
     * - If a single unit > budget: hard split at budget boundary (last resort)
     * 
     * @param block unit to fit
     * @param budget max characters
     * @return list of pieces, each ≤ budget
     */
    private List<String> fit(Blocks.Block block, int budget) {
        // Best case: block fits
        if (block.text().length() <= budget) {
            return List.of(block.text());
        }

        // Split by appropriate boundary: newlines for code, sentences for text
        String[] units = block.code()
                ? block.text().split("\n", -1)
                : block.text().split("(?<=[.!?])\\s+");  // Positive lookbehind for sentence end
        
        String joiner = block.code() ? "\n" : " ";

        // Greedily pack units
        List<String> pieces = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (String unit : units) {
            // If unit itself is > budget, hard-split it
            for (String part : hardSplit(unit, budget)) {
                if (cur.length() > 0 && cur.length() + joiner.length() + part.length() > budget) {
                    // Adding this part would overflow, emit current
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

    /**
     * Last resort: hard-split a string that exceeds budget even as a single unit.
     * This can happen with very long words or one huge code line.
     * 
     * @param unit string to split
     * @param budget max chars per piece
     * @return list of pieces
     */
    private static List<String> hardSplit(String unit, int budget) {
        List<String> parts = new ArrayList<>();
        for (int i = 0; i < unit.length(); i += budget) {
            parts.add(unit.substring(i, Math.min(unit.length(), i + budget)));
        }
        return parts;
    }
}
