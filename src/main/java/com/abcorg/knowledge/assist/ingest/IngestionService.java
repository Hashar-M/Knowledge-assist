package com.abcorg.knowledge.assist.ingest;
import java.util.ArrayList;
import java.util.List;

import com.abcorg.knowledge.assist.config.IngestProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import com.abcorg.knowledge.assist.chunking.Chunk;                    // adapt: your Chapter 7 package
import com.abcorg.knowledge.assist.chunking.HeadingAwareChunker;      // adapt

@Service
public class IngestionService {

    private static final Logger log = LoggerFactory.getLogger(IngestionService.class);

    private final StarterCorpus corpus;
    private final HeadingAwareChunker chunker;
    private final BatchEmbedder embedder;
    private final BulkIndexer indexer;
    private final IngestProperties props;

    public IngestionService(StarterCorpus corpus, HeadingAwareChunker chunker,
            BatchEmbedder embedder, BulkIndexer indexer, IngestProperties props) {
        this.corpus = corpus;
        this.chunker = chunker;
        this.embedder = embedder;
        this.indexer = indexer;
        this.props = props;
    }

    public void ingest(boolean dryRun) {
        List<ChunkJob> jobs = new ArrayList<>();
        for (SourceDocument doc : corpus.load()) {
            for (Chunk chunk : chunker.chunk(doc)) {
                jobs.add(new ChunkJob(ChunkIds.of(doc.docId(), chunk.ordinal()), chunk.text(), doc));
            }
        }

        CostEstimate estimate = CostEstimate.of(jobs, props);
        log.info("Chunks: {}  Estimated tokens: {}  Estimated embedding cost: ${}",
                estimate.chunks(), estimate.tokens(), String.format("%.4f", estimate.usd()));

        if (dryRun) {
            log.info("Dry run: nothing embedded, nothing indexed.");
            return;
        }

        embedder.verifyDimensions();
        List<String> texts = jobs.stream().map(ChunkJob::text).toList();
        List<float[]> vectors = embedder.embedAll(texts);

        List<IndexCommand> commands = new ArrayList<>(jobs.size());
        for (int i = 0; i < jobs.size(); i++) {
            ChunkJob job = jobs.get(i);
            commands.add(new IndexCommand(job.id(), IndexDocuments.toSource(job, vectors.get(i))));
        }

        IndexReport report = indexer.index(commands);
        log.info("Indexed: {}  Failed: {}", report.indexed, report.failed.size());
        report.failed.forEach(f -> log.error("Rejected: {}", f));
        if (!report.failed.isEmpty()) {
            throw new IngestException(report.failed.size() + " chunks were rejected; see log");
        }
    }
}

