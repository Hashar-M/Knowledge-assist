package com.abcorg.knowledge.assist.ingest;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import com.abcorg.knowledge.assist.config.IngestProperties;
import org.springframework.stereotype.Component;

/**
 * Lazy-loading corpus of starter documents.
 * 
 * This component:
 * - Reads the manifest.yaml to discover configured products (spring-boot, kafka, etc.)
 * - Loads only the files listed in starter-files.txt
 * - Provides document metadata without preloading everything into memory
 * 
 * The "lazy" design means:
 * - Manifest is loaded only when first needed (via products() call)
 * - In a web-only scenario (no ingestion), documents are never loaded
 * - Useful for applications that might not use ingestion at startup
 * 
 * Example flow:
 * 1. IngestCommand calls corpus.load()
 * 2. This triggers products() if not yet cached
 * 3. products() reads data/manifest.yaml to get product configurations
 * 4. load() uses DocumentLoader to read each file listed in starter-files.txt
 * 5. Each file is parsed into a SourceDocument with full metadata
 */
@Component
public class StarterCorpus {

    private final Path dataDir;
    
    // Cache for manifest to avoid re-reading it multiple times
    // Maps product name -> ProductMeta (repo URL, commit SHA, docs root, license, etc.)
    private Map<String, ProductMeta> products;

    public StarterCorpus(IngestProperties props) {
        // Data directory configured in application.yaml (default: "data/")
        // Contains:
        // - manifest.yaml: product configurations
        // - starter-files.txt: list of files to index
        // - starter/: cloned/cached source documents
        this.dataDir = Path.of(props.dataDir());
    }

    /**
     * Lazily loads and caches the product manifest.
     * Throws if any configured product is not pinned with a specific commit SHA.
     * 
     * @return map of product names to their metadata (license, repo, commit, etc.)
     * @throws IOException if manifest.yaml cannot be read
     */
    public synchronized Map<String, ProductMeta> products() throws IOException {
        if (products == null) {
            // Read and parse manifest.yaml
            // This file defines all available products and their licensing
            // Example entry:
            // - name: spring-boot
            //   repo: https://github.com/spring-projects/spring-boot.git
            //   commit: "d6e1b249d025ec2fc5c4ff87f451c62682c958fa"  (required for reproducibility)
            //   docs_root: documentation/spring-boot-docs/src/docs/antora/modules/reference/pages
            //   license: Apache-2.0
            //   local_only: false
            products = ManifestReader.read(dataDir.resolve("manifest.yaml"));
        }
        return products;
    }

    /**
     * Loads all source documents.
     * 
     * @return List of SourceDocument objects (one per file in starter-files.txt)
     * @throws IOException if any file cannot be read
     */
    public List<SourceDocument> load() throws IOException {
        // DocumentLoader reads files listed in starter-files.txt
        // Each file is parsed based on its extension:
        // - .adoc files: AsciiDoc parser
        // - .md files: Markdown parser
        // - .html files: HTML parser
        // 
        // Result is a list of SourceDocument objects containing:
        // - Full text content
        // - Extracted headings and their hierarchy
        // - Metadata (product, license, source URL, etc.)
        // - Content hash for change detection
        return new DocumentLoader(dataDir.resolve("starter"), products())
                .loadAll(dataDir.resolve("starter-files.txt"));
    }
}