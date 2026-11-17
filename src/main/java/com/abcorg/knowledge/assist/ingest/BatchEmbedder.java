package com.abcorg.knowledge.assist.ingest;

import com.abcorg.knowledge.assist.config.IngestProperties;
import com.abcorg.knowledge.assist.config.SearchProperties;
import com.abcorg.knowledge.assist.exception.IngestException;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class BatchEmbedder {

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
     * Embeds one batch. Returns one vector per text, in the same order, after checking them against the index.
     */
    public Embedded embed(List<String> texts) {
        EmbeddingResponse response =
                Retry.run("embed " + texts.size() + " texts", props.maxAttempts(), props.initialBackoff(),
                        BatchEmbedder::isRetryable, () -> model.embedForResponse(texts));

        List<float[]> vectors = response
                .getResults()
                .stream()
                .map(Embedding::getOutput)
                .toList();
        if (vectors.size() != texts.size()) {
            throw new IngestException(
                    "Provider returned " + vectors.size() + " vectors for " + texts.size() + " texts");
        }
        for (float[] v : vectors) {
            if (v.length != search.embeddingDims()) {
                throw new IngestException(
                        "Model returned " + v.length + " dimensions but the index expects " + search.embeddingDims()
                                + ". Nothing was indexed. See Chapter 19 for a new index version.");
            }
        }

        // Same dimensions is not the same model: ada-002 and 3-small both return 1536.
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
     * Conservative: retry unknown runtime failures, never our own validation errors.
     */
    private static boolean isRetryable(RuntimeException e) {
        return !(e instanceof IllegalArgumentException) && !(e instanceof IngestException);
    }
}

