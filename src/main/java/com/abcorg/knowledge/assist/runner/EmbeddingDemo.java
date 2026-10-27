package com.abcorg.knowledge.assist.runner;

import com.abcorg.knowledge.assist.util.Similarity;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.IntStream;

@Component
@ConditionalOnProperty(name = "app.run-embedding-demo", havingValue = "true")
public class EmbeddingDemo implements CommandLineRunner {

    private static final List<String> SENTENCES = List.of(
            "Kafka consumers track their position with offsets.",                              // 0
            "A Kafka consumer group shares topic partitions among its members.",               // 1
            "Spring Boot auto-configures a DataSource from application properties.",            // 2
            "Kubernetes restarts a container when its liveness probe fails.",                  // 3
            "Liveness probes tell Kubernetes whether a container is still alive.",             // 4
            "Elasticsearch stores documents in indices that are split into shards.",            // 5
            "A dense_vector field holds embeddings for approximate nearest neighbour search.", // 6
            "Redis keeps its data in memory for very fast reads.",                             // 7
            "Spring AI lets you swap chat and embedding providers behind common interfaces.",  // 8
            "We had a picnic on the river bank and watched the ducks.",                         // 9 (ambiguity test)
            "The bank approved my mortgage application last week."
    );

//    private static final String QUERY = "How do I restart a failing pod?";
//    private static final String QUERY = "Where can I open a bank account?";
//    private static final String QUERY = "Where do ducks gather near water?";
    private static final String QUERY = "how can i go to nearest bank form my picnic spot for money?";

    private final EmbeddingModel embeddingModel;

    public EmbeddingDemo(EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    @Override
    public void run(String... args) {
        // One batched call for all stored sentences (cheaper than one call per sentence).
        List<float[]> sentenceVectors = embeddingModel.embed(SENTENCES);
        // One call for the query.
        float[] queryVector = embeddingModel.embed(QUERY);

        System.out.printf("Model dimension: %d%n", queryVector.length);
        System.out.printf("First 5 numbers of query vector: %s%n%n", preview(queryVector));

        record Scored(int index, double score) {}

        List<Scored> ranked = IntStream
                .range(0, SENTENCES.size())
                .mapToObj(i -> new Scored(i, Similarity.cosine(queryVector, sentenceVectors.get(i))))
                .sorted(Comparator
                        .comparingDouble(Scored::score).reversed())
                .toList();

        System.out.println("Query: " + QUERY);
        System.out.println("Rank  Score   Sentence");
        int rank = 1;
        for (Scored s : ranked) {
            System.out.printf(Locale.ROOT, "%4d  %.3f   %s%n", rank++, s.score(), SENTENCES.get(s.index()));
        }
    }

    private static String preview(float[] v) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < 5; i++) {
            if (i > 0) sb.append(", ");
            sb.append(String.format(Locale.ROOT, "%.4f", v[i]));
        }
        return sb.append(", ...]").toString();
    }
}
