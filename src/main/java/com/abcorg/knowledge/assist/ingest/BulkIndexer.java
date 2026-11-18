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

/**
 * Elasticsearch bulk indexer with retry logic and detailed error handling.
 * 
 * This component:
 * - Sends chunks to Elasticsearch in batches using NDJSON format (Newline-Delimited JSON)
 * - Handles both request-level failures (network, 5xx) and item-level failures (429, timeout)
 * - Retries individual failed documents with exponential backoff
 * - Tracks success/failure counts and detailed error messages
 * 
 * Example Flow:
 * 
 * INPUT: 64 chunks with embeddings
 * --------
 * IndexCommand 1: {id: "spring-boot:actuator/endpoints.adoc#0", source: {...chunk data...}}
 * IndexCommand 2: {id: "spring-boot:actuator/endpoints.adoc#1", source: {...chunk data...}}
 * ... (62 more)
 * 
 * STAGE 1 - Convert to NDJSON Format:
 * Elasticsearch bulk API expects newline-delimited JSON (NDJSON), 2 lines per document:
 * 
 * {"index":{"_index":"chunks","_id":"spring-boot:actuator/endpoints.adoc#0"}}
 * {"text":"Actuator > Web Exposure > Endpoints\n\n...content...","embedding":[0.012,-0.045,...],"product":"spring-boot",...}
 * {"index":{"_index":"chunks","_id":"spring-boot:actuator/endpoints.adoc#1"}}
 * {"text":"Actuator > Web Exposure > Endpoints\n\n...content...","embedding":[0.001,-0.012,...],"product":"spring-boot",...}
 * ...
 * 
 * STAGE 2 - Send Bulk Request:
 * POST http://localhost:9200/_bulk
 * Content-Type: application/x-ndjson
 * [binary NDJSON body]
 * 
 * STAGE 3 - Parse Response:
 * Elasticsearch responds with status for each document:
 * {
 *   "items": [
 *     {"index":{"_id":"spring-boot:actuator/endpoints.adoc#0","status":201}},     // Success
 *     {"index":{"_id":"spring-boot:actuator/endpoints.adoc#1","status":201}},     // Success
 *     {"index":{"_id":"spring-boot:actuator/endpoints.adoc#2","status":429,"error":{"type":"too_many_requests"}}},  // Retry
 *     {"index":{"_id":"spring-boot:actuator/endpoints.adoc#3","status":400,"error":{"type":"mapper_parsing_exception"}}},  // Fail
 *   ]
 * }
 * 
 * STAGE 4 - Classify Results:
 * - 201/200 (Created/OK): Success, increment report.indexed
 * - 429/500/502/503/504 (Retriable): Add to retry list
 * - 400/others (Permanent): Add to report.failed
 * 
 * STAGE 5 - Exponential Backoff & Retry:
 * For retriable failures, retry with exponential backoff:
 * - Attempt 1 failure: wait 500ms (initial-backoff)
 * - Attempt 2 failure: wait 1000ms (500ms * 2^1)
 * - Attempt 3 failure: wait 2000ms (500ms * 2^2)
 * - Max 3 attempts (configured in application.yaml)
 * 
 * STAGE 6 - Final Refresh:
 * POST http://localhost:9200/chunks/_refresh
 * Makes all indexed documents immediately searchable (without refresh, brief delay)
 * 
 * Example Result:
 * - Indexed: 62 documents
 * - Failed: 2 documents (with error messages)
 * - Prompt tokens: 19,200
 */
@Component
public class BulkIndexer {

    private static final MediaType NDJSON = MediaType.parseMediaType("application/x-ndjson");
    private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE = new ParameterizedTypeReference<>() {
    };
    
    /**
     * HTTP status codes that indicate a retriable failure at the item level.
     * These are transient issues that might succeed on retry:
     * - 429: Too Many Requests (rate limit)
     * - 500: Internal Server Error
     * - 502: Bad Gateway
     * - 503: Service Unavailable
     * - 504: Gateway Timeout
     */
    private static final Set<Integer> RETRYABLE_ITEM = Set.of(429, 500, 502, 503, 504);

    private final RestClient es;
    private final SearchProperties search;
    private final IngestProperties props;
    private final JsonMapper json;

    public BulkIndexer(RestClient.Builder builder, SearchProperties search, IngestProperties props, JsonMapper json) {
        // Create REST client configured to talk to Elasticsearch
        this.es = builder
                .baseUrl(search.baseUrl())
                .build();
        this.search = search;
        this.props = props;
        this.json = json;
    }

    /**
     * Convenience method: index all commands, retrying failed items automatically.
     * This is a simple wrapper around indexBatch that handles all batching internally.
     * 
     * @param commands list of documents to index
     * @return report with counts and error details
     */
    public IndexReport index(List<IndexCommand> commands) {
        IndexReport report = new IndexReport();
        // Partition into batches and index each batch
        for (List<IndexCommand> slice : Batches.partition(commands, props.batchSize())) {
            indexBatch(slice, report);
        }
        // Make all indexed documents immediately searchable
        refresh();
        return report;
    }

    /**
     * Sends one batch with automatic retry of failed items.
     * 
     * Algorithm:
     * 1. Send batch to Elasticsearch
     * 2. For each document in response:
     *    - Status 2xx: increment report.indexed
     *    - Retriable status: add to pending retry list
     *    - Permanent failure: add to report.failed
     * 3. If retries needed and attempts remain:
     *    - Wait with exponential backoff
     *    - Retry only the failed documents (not the whole batch)
     * 4. Repeat until no retries or max attempts reached
     * 
     * @param batch list of documents to index
     * @param report output: accumulates success/failure counts
     */
    public void indexBatch(List<IndexCommand> batch, IndexReport report) {
        List<IndexCommand> pending = batch;
        
        // Retry loop: keep trying failed items until success or max attempts
        for (int attempt = 1; !pending.isEmpty(); attempt++) {
            // Send this batch (or retry batch) to Elasticsearch
            List<Map<String, Object>> items = sendWithRetry(pending);
            
            // ========== VALIDATE RESPONSE STRUCTURE ==========
            if (items.size() != pending.size()) {
                throw new IngestException(
                        "Bulk response had " + items.size() + " items for " + pending.size() + " actions");
            }

            // ========== CLASSIFY RESULTS ==========
            List<IndexCommand> retry = new ArrayList<>();
            
            for (int i = 0; i < items.size(); i++) {
                // Each item in response looks like:
                // {"index": {"_id": "...", "status": 201, "error": null}}
                // or
                // {"index": {"_id": "...", "status": 429, "error": {...}}}
                @SuppressWarnings("unchecked") Map<String, Object> result = (Map<String, Object>)items
                        .get(i)
                        .values()
                        .iterator()
                        .next();
                
                IndexCommand command = pending.get(i);               // bulk responses keep request order
                Object error = result.get("error");
                int status = ((Number)result.get("status")).intValue();

                if (error == null) {
                    // ========== SUCCESS ==========
                    // Document was successfully indexed (status 201 = created, 200 = updated)
                    report.indexed++;
                } else if (RETRYABLE_ITEM.contains(status) && attempt < props.maxAttempts()) {
                    // ========== RETRIABLE FAILURE ==========
                    // This item can be retried (transient error)
                    // Add it to retry list to send again after backoff
                    retry.add(command);
                } else {
                    // ========== PERMANENT FAILURE ==========
                    // Either non-retriable error (4xx), or max retries reached
                    // Add to report and move on
                    report.failed.add(command.id() + ": " + error);
                }
            }
            
            // ========== EXPONENTIAL BACKOFF ==========
            if (!retry.isEmpty()) {
                // Exponential backoff formula: initial * (2 ^ (attempt - 1))
                // Attempt 1 failure: 500ms
                // Attempt 2 failure: 1000ms
                // Attempt 3 failure: 2000ms
                Retry.pause(props
                        .initialBackoff()
                        .multipliedBy(1L << (attempt - 1)));
            }
            
            // Continue with retry list in next iteration
            pending = retry;
        }
    }

    /**
     * Refreshes the index to make all documents immediately searchable.
     * Without refresh, there's a brief delay (default 1s) before new documents appear.
     */
    public void refresh() {
        es
                .post()
                .uri("/{alias}/_refresh", search.alias())
                .retrieve()
                .toBodilessEntity();
    }

    /**
     * Sends a batch to Elasticsearch bulk API with whole-request retry logic.
     * 
     * This handles request-level failures (network, Elasticsearch unavailable, 5xx).
     * Item-level failures are handled in indexBatch().
     * 
     * @param commands batch to send
     * @return list of item responses (one per command, in same order)
     * @throws IngestException if max retries exceeded or response invalid
     */
    private List<Map<String, Object>> sendWithRetry(List<IndexCommand> commands) {
        // Convert commands to NDJSON format
        byte[] body = toNdjson(commands).getBytes(StandardCharsets.UTF_8);
        
        // Send with automatic whole-request retry on transient failures
        // Max 3 attempts, exponential backoff starting at 500ms
        // Retryable: network errors, 5xx, 429
        // Not retryable: 400, other 4xx
        Map<String, Object> response =
                Retry.run("bulk " + commands.size() + " docs", props.maxAttempts(), props.initialBackoff(),
                        BulkIndexer::isRetryableRequest, () -> es
                                .post()
                                .uri("/_bulk")
                                .contentType(NDJSON)
                                .body(body)
                                .retrieve()
                                .body(MAP_TYPE));
        
        // Extract items from response
        @SuppressWarnings("unchecked") List<Map<String, Object>> items =
                (List<Map<String, Object>>)response.get("items");
        return items;
    }

    /**
     * Determines if a request-level failure should be retried.
     * 
     * We retry on:
     * - Network problems (ResourceAccessException): server unreachable
     * - 5xx errors: temporary Elasticsearch issues
     * - 429 rate limit: backoff and retry
     * 
     * We DON'T retry on:
     * - 400/4xx (except 429): our request was malformed or invalid
     * - Permanent client errors
     * 
     * @param e exception that occurred
     * @return true if worth retrying, false if permanent failure
     */
    private static boolean isRetryableRequest(RuntimeException e) {
        return e instanceof ResourceAccessException || e instanceof HttpServerErrorException
                || e instanceof HttpClientErrorException.TooManyRequests;
    }

    /**
     * Converts a list of IndexCommand objects to NDJSON format.
     * 
     * NDJSON (Newline-Delimited JSON) is Elasticsearch bulk API format:
     * - Two lines per document
     * - Line 1: action metadata (index, _id, etc.)
     * - Line 2: document source (the actual data to index)
     * - Each line is complete JSON followed by newline
     * 
     * Example output:
     * {"index":{"_index":"chunks","_id":"doc#0"}}
     * {"text":"...","embedding":[...],"product":"..."}
     * {"index":{"_index":"chunks","_id":"doc#1"}}
     * {"text":"...","embedding":[...],"product":"..."}
     * 
     * Note: Writing to the alias keeps this code unchanged when upgrading
     * the index version (Chapter 19 swaps a new index behind the alias).
     * 
     * @param commands documents to index
     * @return NDJSON-formatted string
     */
    private String toNdjson(List<IndexCommand> commands) {
        StringBuilder sb = new StringBuilder();
        for (IndexCommand c : commands) {
            // Line 1: Action metadata
            // Uses the alias so this code works across index versions
            sb
                    .append(json.writeValueAsString(Map.of("index", Map.of("_index", search.alias(), "_id", c.id()))))
                    .append('\n');
            
            // Line 2: Document source (the actual data)
            sb
                    .append(json.writeValueAsString(c.source()))
                    .append('\n');
        }
        return sb.toString();
    }
}
