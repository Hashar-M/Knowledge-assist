package com.abcorg.knowledge.assist.ingest;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import com.abcorg.knowledge.assist.config.IngestProperties;
import org.springframework.stereotype.Component;

/** Loads the starter corpus using the Chapter 6 classes. Lazy, so a web-only run never needs a pinned manifest. */
@Component
public class StarterCorpus {

    private final Path dataDir;
    private Map<String, ProductMeta> products;

    public StarterCorpus(IngestProperties props) {
        this.dataDir = Path.of(props.dataDir());
    }

    public synchronized Map<String, ProductMeta> products() throws IOException {
        if (products == null) {
            products = ManifestReader.read(dataDir.resolve("manifest.yaml"));   // throws if a product is not pinned
        }
        return products;
    }

    public List<SourceDocument> load() throws IOException {
        return new DocumentLoader(dataDir.resolve("starter"), products())
                .loadAll(dataDir.resolve("starter-files.txt"));
    }
}

