package com.abcorg.knowledge.assist.ingest;

import com.abcorg.knowledge.assist.chunking.Chunk;
import com.abcorg.knowledge.assist.chunking.ChunkStats;
import com.abcorg.knowledge.assist.chunking.HeadingAwareChunker;
import com.abcorg.knowledge.assist.config.IngestProperties;
import com.abcorg.knowledge.assist.config.SearchProperties;
import com.abcorg.knowledge.assist.exception.IngestException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Service
public class IngestionService {

    private static final Logger log = LoggerFactory.getLogger(IngestionService.class);

    private final StarterCorpus corpus;
    private final HeadingAwareChunker chunker;
    private final BatchEmbedder embedder;
    private final BulkIndexer indexer;
    private final LocalOnlyGuard guard;
    private final SearchProperties search;
    private final IngestProperties props;

    public IngestionService(StarterCorpus corpus, HeadingAwareChunker chunker, BatchEmbedder embedder,
            BulkIndexer indexer, LocalOnlyGuard guard, SearchProperties search, IngestProperties props) {
        this.corpus = corpus;
        this.chunker = chunker;
        this.embedder = embedder;
        this.indexer = indexer;
        this.guard = guard;
        this.search = search;
        this.props = props;
    }

    public IndexReport ingest(boolean dryRun) throws IOException {
        List<SourceDocument> docs = corpus.load();
        guard.check(docs, corpus.products(), search.baseUrl());      // also runs in a dry run

        List<ChunkJob> jobs = new ArrayList<>();
        List<Chunk> chunks = new ArrayList<>();
        int sections = 0;
        int unmatched = 0;
        for (SourceDocument doc : docs) {
            SectionSplitter.Result split = SectionSplitter.split(doc);
            sections += split
                    .sections()
                    .size();
            unmatched += split.unmatchedHeadings();
            for (Chunk chunk : chunker.chunk(doc.docId(), split.sections())) {
                ChunkIds.requireValid(chunk.chunkId());
                jobs.add(new ChunkJob(doc, chunk));
                chunks.add(chunk);
            }
        }
        log.info("Documents: {}  Sections: {}  Chunks: {}  Unmatched headings: {}", docs.size(), sections, jobs.size(),
                unmatched);
        log.info("\n{}", ChunkStats
                .of(chunks)
                .toMarkdown());

        CostEstimate estimate = CostEstimate.of(jobs, props);
        log.info("Estimated tokens: {}  Estimated embedding cost: ${}", estimate.tokens(),
                String.format("%.4f", estimate.usd()));

        IndexReport report = new IndexReport();
        if (dryRun) {
            log.info("Dry run: nothing embedded, nothing indexed.");
            return report;
        }

        int batchNo = 0;
        List<List<ChunkJob>> batches = Batches.partition(jobs, props.batchSize());
        for (List<ChunkJob> batch : batches) {
            BatchEmbedder.Embedded embedded = embedder.embed(batch
                    .stream()
                    .map(ChunkJob::text)
                    .toList());
            report.promptTokens += embedded.promptTokens();

            List<IndexCommand> commands = new ArrayList<>(batch.size());
            for (int i = 0; i < batch.size(); i++) {
                ChunkJob job = batch.get(i);
                commands.add(new IndexCommand(job.id(), IndexDocuments.toSource(job, embedded
                        .vectors()
                        .get(i))));
            }
            indexer.indexBatch(commands, report);
            log.info("Batch {}/{} done (indexed so far: {})", ++batchNo, batches.size(), report.indexed);
        }
        indexer.refresh();

        log.info("Indexed: {}  Failed: {}  Provider-reported prompt tokens: {} (estimate was {})", report.indexed,
                report.failed.size(), report.promptTokens, estimate.tokens());
        report.failed.forEach(f -> log.error("Rejected: {}", f));
        if (!report.failed.isEmpty()) {
            throw new IngestException(report.failed.size() + " chunks were rejected; see log");
        }
        return report;
    }
}
