package com.abcorg.knowledge.assist.ingest;

import com.abcorg.knowledge.assist.config.IngestProperties;
import com.abcorg.knowledge.assist.config.SearchProperties;
import com.abcorg.knowledge.assist.exception.IngestException;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class BulkIndexer {

    private static final MediaType NDJSON = MediaType.parseMediaType("application/x-ndjson");
    private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE = new ParameterizedTypeReference<>() {
    };
    /**
     * Item statuses that can succeed later.
     */
    private static final Set<Integer> RETRYABLE_ITEM = Set.of(429, 500, 502, 503, 504);

    private final RestClient es;
    private final SearchProperties search;
    private final IngestProperties props;
    private final JsonMapper json;

    public BulkIndexer(RestClient.Builder builder, SearchProperties search, IngestProperties props, JsonMapper json) {
        this.es = builder
                .baseUrl(search.baseUrl())
                .build();
        this.search = search;
        this.props = props;
        this.json = json;
    }

    /**
     * Convenience for tests and small jobs: all batches, then one refresh.
     */
    public IndexReport index(List<IndexCommand> commands) {
        IndexReport report = new IndexReport();
        for (List<IndexCommand> slice : Batches.partition(commands, props.batchSize())) {
            indexBatch(slice, report);
        }
        refresh();
        return report;
    }

    /**
     * Sends one batch. Retries only the items that can still succeed.
     */
    public void indexBatch(List<IndexCommand> batch, IndexReport report) {
        List<IndexCommand> pending = batch;
        for (int attempt = 1; !pending.isEmpty(); attempt++) {
            List<Map<String, Object>> items = sendWithRetry(pending);
            if (items.size() != pending.size()) {
                throw new IngestException(
                        "Bulk response had " + items.size() + " items for " + pending.size() + " actions");
            }

            List<IndexCommand> retry = new ArrayList<>();
            for (int i = 0; i < items.size(); i++) {
                // Each item looks like {"index": {"_id": "...", "status": 201, "error": {...}}}
                @SuppressWarnings("unchecked") Map<String, Object> result = (Map<String, Object>)items
                        .get(i)
                        .values()
                        .iterator()
                        .next();
                IndexCommand command = pending.get(i);               // bulk responses keep request order
                Object error = result.get("error");
                int status = ((Number)result.get("status")).intValue();

                if (error == null) {
                    report.indexed++;
                } else if (RETRYABLE_ITEM.contains(status) && attempt < props.maxAttempts()) {
                    retry.add(command);
                } else {
                    report.failed.add(command.id() + ": " + error);
                }
            }
            if (!retry.isEmpty()) {
                Retry.pause(props
                        .initialBackoff()
                        .multipliedBy(1L << (attempt - 1)));
            }
            pending = retry;
        }
    }

    public void refresh() {
        es
                .post()
                .uri("/{alias}/_refresh", search.alias())
                .retrieve()
                .toBodilessEntity();
    }

    private List<Map<String, Object>> sendWithRetry(List<IndexCommand> commands) {
        byte[] body = toNdjson(commands).getBytes(StandardCharsets.UTF_8);
        Map<String, Object> response =
                Retry.run("bulk " + commands.size() + " docs", props.maxAttempts(), props.initialBackoff(),
                        BulkIndexer::isRetryableRequest, () -> es
                                .post()
                                .uri("/_bulk")
                                .contentType(NDJSON)
                                .body(body)
                                .retrieve()
                                .body(MAP_TYPE));
        @SuppressWarnings("unchecked") List<Map<String, Object>> items =
                (List<Map<String, Object>>)response.get("items");
        return items;
    }

    /**
     * Whole-request failures: network problems, 5xx and 429 are worth another try. A 400 is not.
     */
    private static boolean isRetryableRequest(RuntimeException e) {
        return e instanceof ResourceAccessException || e instanceof HttpServerErrorException
                || e instanceof HttpClientErrorException.TooManyRequests;
    }

    private String toNdjson(List<IndexCommand> commands) {
        StringBuilder sb = new StringBuilder();
        for (IndexCommand c : commands) {
            // Writing to the alias keeps this code unchanged when Chapter 19 swaps the index behind it.
            sb
                    .append(json.writeValueAsString(Map.of("index", Map.of("_index", search.alias(), "_id", c.id()))))
                    .append('\n');
            sb
                    .append(json.writeValueAsString(c.source()))
                    .append('\n');
        }
        return sb.toString();
    }
}
