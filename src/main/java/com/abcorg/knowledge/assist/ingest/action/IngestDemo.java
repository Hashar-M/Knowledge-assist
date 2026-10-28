package com.abcorg.knowledge.assist.ingest.action;

import com.abcorg.knowledge.assist.ingest.DocumentLoader;
import com.abcorg.knowledge.assist.ingest.ManifestReader;
import com.abcorg.knowledge.assist.ingest.ProductMeta;
import com.abcorg.knowledge.assist.ingest.SourceDocument;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/** Prints a per-product summary of the starter subset. Run from the repository root. */
public final class IngestDemo {

    public static void main(String[] args) throws Exception {
        Path data = Path.of("data");
        Map<String, ProductMeta> products = ManifestReader.read(data.resolve("manifest.yaml"));
        DocumentLoader loader = new DocumentLoader(data.resolve("starter"), products);
        List<SourceDocument> docs = loader.loadAll(data.resolve("starter-files.txt"));

        System.out.printf("loaded %d documents%n", docs.size());
        docs.stream()
                .collect(Collectors.groupingBy(SourceDocument::product, TreeMap::new, Collectors.toList()))
                .forEach((product, list) -> System.out.printf(
                        "  %-14s docs=%d chars=%,d warnings=%d%n",
                        product,
                        list.size(),
                        list.stream().mapToInt(d -> d.text().length()).sum(),
                        list.stream().mapToInt(d -> d.warnings().size()).sum()));
    }
}

