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

/**
 * Core orchestrator of the document ingestion pipeline.
 * 
 * This service coordinates the entire flow from raw documents to searchable indexed chunks:
 * 
 * PIPELINE STAGES:
 * 1. Document Loading:   Load raw source documents from configured repositories
 * 2. Section Splitting:  Parse documents into hierarchical sections using heading structure
 * 3. Chunking:          Split sections into token-bounded chunks with overlap
 * 4. Embedding:         Generate vector embeddings for each chunk using an LLM
 * 5. Indexing:          Batch upload chunks and embeddings to Elasticsearch
 * 
 * Example Flow for Spring Boot Documentation:
 * Input: spring-boot/actuator/endpoints.adoc (10KB file with nested headings)
 *   ↓
 * Step 1 - Loaded as SourceDocument with metadata (docId, product, license, etc.)
 *   ↓
 * Step 2 - Split into sections by heading hierarchy:
 *   - Section: "Actuator > Web Exposure > Endpoints"
 *   - Section: "Actuator > Web Exposure > Custom Endpoints"
 *   ↓
 * Step 3 - Each section chunked into ~500 token pieces with 50-token overlap:
 *   - Chunk 1: "Actuator > Web Exposure > Endpoints\n\n[content up to 500 tokens]"
 *   - Chunk 2: "[overlap from chunk 1]\n\n[new content]"
 *   ↓
 * Step 4 - Each chunk embedded (e.g., 1536-dim vector using text-embedding-3-small)
 *   ↓
 * Step 5 - Batches of chunks uploaded to Elasticsearch, with retry logic for failures
 */
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

    /**
     * Main ingestion method.
     * 
     * @param dryRun if true, runs all processing steps but skips actual indexing to Elasticsearch
     * @return IndexReport containing statistics (count of indexed documents, failures, token usage)
     * @throws IOException if file I/O fails
     */
    public IndexReport ingest(boolean dryRun) throws IOException {
        // ========== STAGE 1: LOAD SOURCE DOCUMENTS ==========
        // Load all configured source documents (e.g., spring-boot, kafka, kubernetes)
        // Each document contains the full text, headings, metadata, and licensing information
        List<SourceDocument> docs = corpus.load();
        
        // ========== SECURITY CHECK ==========
        // Enforce that local-only products (e.g., elasticsearch-reference marked as local_only: true)
        // are never indexed to non-local Elasticsearch instances. This protects license compliance.
        guard.check(docs, corpus.products(), search.baseUrl());

        // ========== STAGE 2: SPLIT INTO SECTIONS & CHUNK ==========
        // A section is a contiguous block of text under a specific heading hierarchy.
        // We track:
        // - Total sections found (useful for validation)
        // - Unmatched headings (potential parsing issues)
        // - All chunks created (the atomic units for embedding and indexing)
        // - ChunkJobs (pairing each chunk with its source document for context)
        
        List<ChunkJob> jobs = new ArrayList<>();
        List<Chunk> chunks = new ArrayList<>();
        int sections = 0;
        int unmatched = 0;
        
        for (SourceDocument doc : docs) {
            // Split document by heading hierarchy into sections
            // Example: If a document has headings "# Getting Started > ## Installation",
            // it creates sections that preserve this hierarchy for breadcrumb trails
            SectionSplitter.Result split = SectionSplitter.split(doc);
            sections += split.sections().size();
            unmatched += split.unmatchedHeadings();
            
            // Further split each section into chunks (respecting token limits and overlap)
            for (Chunk chunk : chunker.chunk(doc.docId(), split.sections())) {
                // Validate chunk IDs are unique (e.g., "spring-boot:actuator/endpoints.adoc#0")
                ChunkIds.requireValid(chunk.chunkId());
                
                // Pair chunk with source document for context during embedding and indexing
                jobs.add(new ChunkJob(doc, chunk));
                chunks.add(chunk);
            }
        }
        
        // Log pipeline statistics
        log.info("Documents: {}  Sections: {}  Chunks: {}  Unmatched headings: {}", docs.size(), sections, jobs.size(),
                unmatched);
        log.info("\n{}", ChunkStats
                .of(chunks)
                .toMarkdown());

        // ========== STAGE 3: COST ESTIMATION ==========
        // Calculate estimated token usage and embedding costs before processing
        // Using: (token count) * (price per million tokens)
        // Example: 10,000 chunks * 300 avg tokens * $0.02/1M = ~$0.06
        CostEstimate estimate = CostEstimate.of(jobs, props);
        log.info("Estimated tokens: {}  Estimated embedding cost: ${}", estimate.tokens(),
                String.format("%.4f", estimate.usd()));

        // ========== EARLY EXIT FOR DRY RUN ==========
        // If --dry-run flag was passed, stop here without touching Elasticsearch
        IndexReport report = new IndexReport();
        if (dryRun) {
            log.info("Dry run: nothing embedded, nothing indexed.");
            return report;
        }

        // ========== STAGE 4: BATCH EMBEDDING & INDEXING ==========
        // Process chunks in configurable batch sizes (default: 64 chunks per batch)
        // For each batch:
        // 1. Send all chunk texts to embedding service (e.g., OpenAI API)
        // 2. Receive back one vector per chunk (e.g., 1536 dimensions)
        // 3. Package chunks + embeddings as Elasticsearch bulk commands
        // 4. Send to Elasticsearch with retry logic for transient failures
        
        int batchNo = 0;
        List<List<ChunkJob>> batches = Batches.partition(jobs, props.batchSize());
        
        for (List<ChunkJob> batch : batches) {
            // Embed all texts in this batch
            // Returns vectors in the same order as input texts
            BatchEmbedder.Embedded embedded = embedder.embed(batch
                    .stream()
                    .map(ChunkJob::text)
                    .toList());
            
            // Track cumulative token usage for cost reporting
            report.promptTokens += embedded.promptTokens();

            // ========== BUILD INDEX COMMANDS ==========
            // Convert each (chunk, embedding) pair into an Elasticsearch bulk command
            // Each command includes:
            // - Chunk ID (unique identifier)
            // - Chunk text (searchable content with heading breadcrumb)
            // - Embedding vector (for semantic search)
            // - Metadata (product, source URL, license, etc.)
            List<IndexCommand> commands = new ArrayList<>(batch.size());
            for (int i = 0; i < batch.size(); i++) {
                ChunkJob job = batch.get(i);
                commands.add(new IndexCommand(job.id(), IndexDocuments.toSource(job, embedded
                        .vectors()
                        .get(i))));
            }
            
            // Send batch to Elasticsearch with automatic retry on transient failures
            // Failures are collected in report.failed for final error reporting
            indexer.indexBatch(commands, report);
            log.info("Batch {}/{} done (indexed so far: {})", ++batchNo, batches.size(), report.indexed);
        }

        // ========== STAGE 5: FINALIZE ==========
        // Refresh the index to make all indexed documents immediately searchable
        // (Without refresh, there's a brief delay before new documents appear in searches)
        indexer.refresh();

        // ========== RESULTS REPORTING ==========
        log.info("Indexed: {}  Failed: {}  Provider-reported prompt tokens: {} (estimate was {})", report.indexed,
                report.failed.size(), report.promptTokens, estimate.tokens());
        
        // Log each failure for debugging
        report.failed.forEach(f -> log.error("Rejected: {}", f));
        
        // If there were failures, throw an exception so the application exits with error
        if (!report.failed.isEmpty()) {
            throw new IngestException(report.failed.size() + " chunks were rejected; see log");
        }
        
        return report;
    }
}