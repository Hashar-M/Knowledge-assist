package com.abcorg.knowledge.assist.ingest;

import java.util.ArrayList;
import java.util.List;

import com.abcorg.knowledge.assist.config.IngestProperties;
import com.abcorg.knowledge.assist.exception.IngestException;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Component;

@Component
public class BatchEmbedder {

    private final EmbeddingModel model;
    private final IngestProperties props;

    public BatchEmbedder(EmbeddingModel model, IngestProperties props) {
        this.model = model;
        this.props = props;
    }

    /** Fail fast if the model's output size does not match the index mapping (Chapter 5). */
    public void verifyDimensions() {
        int actual = model.dimensions();
        if (actual != props.expectedDimensions()) {
            throw new IllegalStateException("Embedding model returns " + actual
                    + " dimensions but the index expects " + props.expectedDimensions()
                    + ". Do not index. Create a new index version (Chapter 19).");
        }
    }

    /** Returns one vector per input text, in the same order. */
    public List<float[]> embedAll(List<String> texts) {
        List<float[]> out = new ArrayList<>(texts.size());
        int batchNo = 0;
        for (List<String> batch : Batches.partition(texts, props.batchSize())) {
            batchNo++;
            final int n = batchNo;
            List<float[]> vectors = Retry.run(
                    "embed batch " + n + " (" + batch.size() + " texts)",
                    props.maxAttempts(), props.initialBackoff(),
                    () -> model.embed(batch));
            if (vectors.size() != batch.size()) {
                throw new IngestException("Provider returned " + vectors.size()
                        + " vectors for " + batch.size() + " texts");
            }
            out.addAll(vectors);
        }
        return out;
    }
}
