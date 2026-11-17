package com.abcorg.knowledge.assist.ingest;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** Enforces the Chapter 6 promise: a local-only product never goes into a non-local index. */
@Component
public class LocalOnlyGuard {

    private static final Set<String> LOCAL_HOSTS = Set.of("localhost", "127.0.0.1", "::1", "[::1]");

    public void check(List<SourceDocument> docs, Map<String, ProductMeta> products, String elasticsearchBaseUrl) {
        String host = URI.create(elasticsearchBaseUrl).getHost();
        if (host != null && LOCAL_HOSTS.contains(host)) {
            return;
        }
        Set<String> restricted = docs.stream()
                .map(SourceDocument::product)
                .filter(p -> products.get(p) != null && products.get(p).localOnly())
                .collect(Collectors.toCollection(java.util.TreeSet::new));
        if (!restricted.isEmpty()) {
            throw new IllegalStateException("Refusing to index local-only products " + restricted
                    + " into non-local Elasticsearch host '" + host + "'. See docs licence notes (Chapter 3).");
        }
    }
}

