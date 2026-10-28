package com.abcorg.knowledge.assist.ingest;

import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ManifestReader {

    private ManifestReader() {}

    @SuppressWarnings("unchecked")
    public static Map<String, ProductMeta> read(Path manifest) throws IOException {
        try (InputStream in = Files.newInputStream(manifest)) {
            Map<String, Object> root = new Yaml().load(in);
            List<Map<String, Object>> products = (List<Map<String, Object>>) root.get("products");

            Map<String, ProductMeta> result = new LinkedHashMap<>();
            for (Map<String, Object> p : products) {
                String name = (String) p.get("name");
                String commit = (String) p.getOrDefault("commit", "");
                if (commit == null || commit.isBlank()) {
                    throw new IllegalStateException(name + " is not pinned: run scripts/fetch-docs.sh");
                }
                result.put(name, new ProductMeta(
                        name,
                        (String) p.get("license"),
                        (String) p.get("source_url_template"),
                        commit,
                        Boolean.TRUE.equals(p.get("local_only"))));
            }
            return result;
        }
    }
}
