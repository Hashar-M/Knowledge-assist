package com.abcorg.knowledge.assist.runner;

import com.abcorg.knowledge.assist.ingest.IngestionService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Entry point for the document ingestion pipeline.
 * 
 * This command is executed when the application starts with the "ingest" profile active.
 * It orchestrates a two-stage process:
 * 1. Index Initialization: Ensures Elasticsearch index exists with proper mappings
 * 2. Ingestion: Loads documents, chunks them, embeds them, and indexes them
 * 
 * Flow:
 * - Spring Boot calls run() method on application startup
 * - Initializer creates/ensures the Elasticsearch index and alias exist
 * - Service performs the full ingestion pipeline (see IngestionService for details)
 * - Application exits after completion
 * 
 * Example usage:
 * - Full ingestion: mvn spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=ingest"
 * - Dry run (no indexing): mvn spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=ingest --dry-run"
 * 
 * The dry-run flag prevents any writes to Elasticsearch, useful for testing the pipeline
 * and estimating costs before actually indexing documents.
 */
@Component
//@Profile("ingest")
public class IngestCommand implements ApplicationRunner {

    private final ChunkIndexInitializer initializer;
    private final IngestionService service;

    public IngestCommand(ChunkIndexInitializer initializer, IngestionService service) {
        this.initializer = initializer;
        this.service = service;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        // Step 1: Ensure the Elasticsearch index exists
        // This is idempotent and creates the index with proper mappings if it doesn't exist.
        // It also establishes the alias that will be used for all document operations.
        // Without this, the bulk indexer would fail when trying to write documents.
        initializer.ensureIndex();

        // Step 2: Execute the full ingestion pipeline
        // This is where all the work happens:
        // - Load source documents from configured repositories
        // - Split documents into sections (using heading hierarchy)
        // - Chunk sections with token awareness and overlap
        // - Embed chunks using the configured embedding model
        // - Batch and retry indexing with Elasticsearch
        // - Report results (success count, failures, token usage)
        // 
        // The dry-run flag determines whether documents are actually indexed.
        // If --dry-run is passed, the service calculates costs but doesn't index anything.
        service.ingest(args.containsOption("dry-run"));
    }
}