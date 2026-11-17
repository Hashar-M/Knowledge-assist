package com.abcorg.knowledge.assist.ingest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;

import com.abcorg.knowledge.assist.config.IngestProperties;
import com.abcorg.knowledge.assist.config.SearchProperties;
import com.abcorg.knowledge.assist.exception.IngestException;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.embedding.EmbeddingResponseMetadata;

class BatchEmbedderTest {

    private final SearchProperties search = new SearchProperties("http://x", "chunks", 1, 3, "text-embedding-3-small");
    private final IngestProperties props = new IngestProperties(2, 2, Duration.ofMillis(1), 0.02, "data");

    private static EmbeddingResponse response(String model, float[]... vectors) {
        EmbeddingResponse r = mock(EmbeddingResponse.class);
        List<Embedding> results = java.util.Arrays.stream(vectors).map(v -> {
            Embedding e = mock(Embedding.class);
            when(e.getOutput()).thenReturn(v);
            return e;
        }).toList();
        when(r.getResults()).thenReturn(results);
        EmbeddingResponseMetadata meta = new EmbeddingResponseMetadata();
        meta.setModel(model);
        when(r.getMetadata()).thenReturn(meta);
        return r;
    }

    private BatchEmbedder embedderReturning(EmbeddingResponse r) {
        EmbeddingModel model = mock(EmbeddingModel.class);
        when(model.embedForResponse(anyList())).thenReturn(r);
        return new BatchEmbedder(model, search, props);
    }

    @Test
    void returnsVectorsInOrder() {
        var r = response("text-embedding-3-small", new float[]{1, 0, 0}, new float[]{0, 1, 0});

        var out = embedderReturning(r).embed(List.of("a", "b"));

        assertThat(out.vectors()).hasSize(2);
        assertThat(out.vectors().get(1)[1]).isEqualTo(1f);
    }

    @Test
    void rejectsWrongDimensions() {
        var r = response("text-embedding-3-small", new float[]{1, 0});

        assertThatThrownBy(() -> embedderReturning(r).embed(List.of("a")))
                .isInstanceOf(IngestException.class).hasMessageContaining("dimensions");
    }

    @Test
    void rejectsADifferentModelEvenWithSameDimensions() {
        var r = response("text-embedding-ada-002", new float[]{1, 0, 0});

        assertThatThrownBy(() -> embedderReturning(r).embed(List.of("a")))
                .isInstanceOf(IngestException.class).hasMessageContaining("ada-002");
    }
}

