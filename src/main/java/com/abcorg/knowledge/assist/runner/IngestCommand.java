package com.abcorg.knowledge.assist.runner;

import com.abcorg.knowledge.assist.ingest.IngestionService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Runs only with --spring.profiles.active=ingest, then the application exits.
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
        initializer.ensureIndex();          // idempotent (Chapter 5); guarantees the alias exists before we write
        service.ingest(args.containsOption("dry-run"));
    }
}

