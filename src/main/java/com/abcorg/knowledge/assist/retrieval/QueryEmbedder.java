package com.abcorg.knowledge.assist.retrieval;

import com.abcorg.knowledge.assist.config.SearchProperties;
import com.abcorg.knowledge.assist.exception.UpstreamUnavailableException;
import java.util.List;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.stereotype.Component;

/**
 * Embeds one question with the SAME model that embedded the chunks (Chapter 8).
 * No retry loop of our own: a person is waiting, and Spring AI and the SDK already retry below us.
 */
@Component
public class QueryEmbedder {

    /** vector: the question's embedding. promptTokens: what the provider counted. model: the model name it reports. */
    public record QueryEmbedding(float[] vector, long promptTokens, String model) {
    }

    private final EmbeddingModel model;
    private final SearchProperties search;

    public QueryEmbedder(EmbeddingModel model, SearchProperties search) {
        this.model = model;
        this.search = search;
    }

    public QueryEmbedding embed(String question) {
        EmbeddingResponse response;
        try {
            response = model.embedForResponse(List.of(question));
        } catch (RuntimeException e) {
            throw new UpstreamUnavailableException("Embedding provider call failed", e);
        }
        if (response.getResults().size() != 1) {
            throw new IllegalStateException("Expected 1 embedding but got " + response.getResults().size());
        }

        float[] vector = response.getResults().get(0).getOutput();
        // Same two checks as Chapter 8's BatchEmbedder. A mismatch is OUR configuration bug (HTTP 500), not an outage.
        if (vector.length != search.embeddingDims()) {
            throw new IllegalStateException("Model returned " + vector.length + " dimensions but the index expects "
                    + search.embeddingDims());
        }
        String used = response.getMetadata() == null ? null : response.getMetadata().getModel();
        if (used != null && !used.isBlank() && !used.startsWith(search.embeddingModel())) {
            throw new IllegalStateException("Question embedded with '" + used + "' but the index was built with '"
                    + search.embeddingModel() + "'. Check spring.ai.openai.embedding.model.");
        }

        var usage = response.getMetadata() == null ? null : response.getMetadata().getUsage();
        Integer promptTokens = usage == null ? null : usage.getPromptTokens();
        return new QueryEmbedding(vector, promptTokens == null ? 0 : promptTokens,
                used == null || used.isBlank() ? search.embeddingModel() : used);
    }
}
