package com.abcorg.knowledge.assist.ingest;

import com.abcorg.knowledge.assist.config.IngestProperties;
import com.abcorg.knowledge.assist.config.SearchProperties;
import com.abcorg.knowledge.assist.exception.IngestException;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Batch embedder for converting text chunks into semantic vectors.
 * 
 * This component:
 * - Takes multiple chunk texts in a batch
 * - Sends them to the embedding service (e.g., OpenAI API)
 * - Returns one vector per text (same order as input)
 * - Validates vector dimensions and model consistency
 * - Implements retry logic for transient failures
 * 
 * Example Flow:
 * 
 * INPUT: 64 chunk texts
 * --------
 * Chunk text 1: "Actuator > Web Exposure > Endpoints\n\nHTTP endpoints expose metrics..."
 * Chunk text 2: "Actuator > Web Exposure > Endpoints\n\nCommon endpoints include /health..."
 * ... (62 more chunks)
 * 
 * STAGE 1 - Send to Embedding Service:
 * API Call: POST https://api.openai.com/v1/embeddings
 * {
 *   "model": "text-embedding-3-small",
 *   "input": ["Actuator > Web Exposure...", "HTTP endpoints...", ...]
 * }
 * 
 * STAGE 2 - Receive Vectors:
 * Response contains 64 vectors, each with 1536 dimensions
 * [
 *   {embedding: [0.012, -0.045, 0.078, ... (1536 dims)]}
 *   {embedding: [0.001, -0.012, 0.034, ... (1536 dims)]}
 *   ...
 * ]
 * 
 * STAGE 3 - Validation:
 * - Ensure we got 64 vectors for 64 texts
 * - Ensure each vector has 1536 dimensions (matches index expectation)
 * - Ensure model used matches the configured model (ada-002 vs 3-small)
 * - Extract token usage for cost tracking
 * 
 * OUTPUT: Embedded record with:
 * - vectors: list of float[] arrays (same order as input)
 * - promptTokens: total tokens used by the embedding service
 * 
 * Cost Calculation Example:
 * - 64 chunks * ~300 average tokens per chunk = 19,200 prompt tokens
 * - Price: $0.02 per million tokens
 * - Cost: 19,200 / 1,000,000 * 0.02 = $0.000384 per batch
 * - 10,000 total chunks = 156 batches ≈ $0.06 total
 */
@Component
public class BatchEmbedder {

    /**
     * Result of embedding a batch of texts.
     * @param vectors list of embedding vectors (float arrays), one per input text
     * @param promptTokens total tokens used for this batch (for cost tracking)
     */
    public record Embedded(List<float[]> vectors, long promptTokens) {
    }

    private final EmbeddingModel model;
    private final SearchProperties search;
    private final IngestProperties props;

    public BatchEmbedder(EmbeddingModel model, SearchProperties search, IngestProperties props) {
        this.model = model;
        this.search = search;
        this.props = props;
    }

    /**
     * Embeds one batch of texts.
     * 
     * Returns one vector per text, in the same order as the input.
     * Validates:
     * - Vector count matches text count
     * - Vector dimensions match index expectations (e.g., 1536 for text-embedding-3-small)
     * - Embedding model matches configured model (dimension alone isn't enough)
     * 
     * Retries on transient failures (network issues, rate limits, 5xx errors)
     * but never on validation errors (bad model, wrong dimensions).
     * 
     * @param texts list of chunk texts to embed
     * @return Embedded record with vectors and token usage
     * @throws IngestException if validation fails or max retries exceeded
     */
    public Embedded embed(List<String> texts) {
        // Call embedding service with automatic retry on transient failures
        // Retries: max 3 attempts, exponential backoff starting at 500ms
        // Retryable: network errors (ResourceAccessException), 5xx errors, 429 rate limits
        // Not retryable: bad input (IllegalArgumentException), our validation errors (IngestException)
        EmbeddingResponse response =
                Retry.run("embed " + texts.size() + " texts", props.maxAttempts(), props.initialBackoff(),
                        BatchEmbedder::isRetryable, () -> model.embedForResponse(texts));

        // Extract vectors from response (one per text)
        List<float[]> vectors = response
                .getResults()
                .stream()
                .map(Embedding::getOutput)
                .toList();
        
        // ========== VALIDATION 1: Vector Count ==========
        // Ensure we received exactly one vector per text
        if (vectors.size() != texts.size()) {
            throw new IngestException(
                    "Provider returned " + vectors.size() + " vectors for " + texts.size() + " texts");
        }

        // ========== VALIDATION 2: Vector Dimensions ==========
        // Each vector must match the index's expected dimension
        // Example: text-embedding-3-small always returns 1536 dimensions
        // If the index was created for 384 dimensions (from text-embedding-3-large),
        // this will fail and prevent silent data corruption
        for (float[] v : vectors) {
            if (v.length != search.embeddingDims()) {
                throw new IngestException(
                        "Model returned " + v.length + " dimensions but the index expects " + search.embeddingDims()
                                + ". Nothing was indexed. See Chapter 19 for a new index version.");
            }
        }

        // ========== VALIDATION 3: Model Consistency ==========
        // Dimension is not enough to verify model (e.g., ada-002 and text-embedding-3-small both use 1536)
        // This check ensures the embeddings match the model used to build the index
        String used = response.getMetadata() == null
                ? null
                : response
                        .getMetadata()
                        .getModel();
        if (used != null && !used.isBlank() && !used.startsWith(search.embeddingModel())) {
            throw new IngestException(
                    "Embedding model used was '" + used + "' but the index was built for '" + search.embeddingModel()
                            + "'. Check spring.ai.openai.embedding.model.");
        }

        // ========== EXTRACT TOKEN USAGE ==========
        // Get token usage from provider for cost calculation
        // Used to track actual vs. estimated costs
        var usage = response.getMetadata() == null
                ? null
                : response
                        .getMetadata()
                        .getUsage();
        Integer prompt = usage == null
                ? null
                : usage.getPromptTokens();
        
        return new Embedded(vectors, prompt == null
                ? 0
                : prompt);
    }

    /**
     * Determines if a failure is worth retrying.
     * 
     * Conservative approach: retry unknown runtime failures, never our own validation errors.
     * 
     * @param e exception that occurred
     * @return true if worth retrying, false if permanent failure
     */
    private static boolean isRetryable(RuntimeException e) {
        // Don't retry if it's our validation error or bad input
        return !(e instanceof IllegalArgumentException) && !(e instanceof IngestException);
    }
}