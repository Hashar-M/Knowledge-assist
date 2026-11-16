package com.abcorg.knowledge.assist.ingest;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.bulk.BulkResponseItem;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.abcorg.knowledge.assist.config.IngestProperties;
import org.springframework.stereotype.Component;

@Component
public class BulkIndexer {

    /** Write alias from Chapter 5. Writing to the alias means a future index swap needs no code change. */
    public static final String TARGET = "chunks";

    /** Item statuses that can succeed on a later attempt. */
    private static final Set<Integer> RETRYABLE = Set.of(429, 500, 502, 503, 504);

    private final ElasticsearchClient es;
    private final IngestProperties props;

    public BulkIndexer(ElasticsearchClient es, IngestProperties props) {
        this.es = es;
        this.props = props;
    }

    public IndexReport index(List<IndexCommand> commands) {
        IndexReport report = new IndexReport();
        for (List<IndexCommand> slice : Batches.partition(commands, props.batchSize())) {
            indexSlice(slice, report);
        }
        refresh();
        return report;
    }

    private void indexSlice(List<IndexCommand> slice, IndexReport report) {
        List<IndexCommand> pending = slice;
        for (int attempt = 1; !pending.isEmpty(); attempt++) {
            Map<String, IndexCommand> byId = new HashMap<>();
            for (IndexCommand c : pending) {
                byId.put(c.id(), c);
            }

            final List<IndexCommand> toSend = pending;
            BulkResponse response = Retry.run(
                    "bulk index " + toSend.size() + " docs",
                    props.maxAttempts(), props.initialBackoff(),
                    () -> sendBulk(toSend));

            List<IndexCommand> retry = new ArrayList<>();
            for (BulkResponseItem item : response.items()) {
                if (item.error() == null) {
                    report.indexed++;
                } else if (RETRYABLE.contains(item.status()) && attempt < props.maxAttempts()) {
                    retry.add(byId.get(item.id()));
                } else {
                    report.failed.add(item.id() + ": " + item.error().reason());
                }
            }
            if (!retry.isEmpty()) {
                Retry.pause(props.initialBackoff().multipliedBy(1L << (attempt - 1)));
            }
            pending = retry;
        }
    }

    private BulkResponse sendBulk(List<IndexCommand> commands) {
        BulkRequest.Builder br = new BulkRequest.Builder();
        for (IndexCommand c : commands) {
            br.operations(op -> op.index(idx -> idx
                    .index(TARGET)
                    .id(c.id())
                    .document(c.source())));
        }
        try {
            return es.bulk(br.build());
        } catch (IOException e) {
            throw new UncheckedIOException(e);   // lets Retry.run catch it
        }
    }

    private void refresh() {
        try {
            es.indices().refresh(r -> r.index(TARGET));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
