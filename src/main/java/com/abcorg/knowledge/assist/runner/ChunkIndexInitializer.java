package com.abcorg.knowledge.assist.runner;

import com.abcorg.knowledge.assist.config.SearchProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Component
public class ChunkIndexInitializer implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(ChunkIndexInitializer.class);
    private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE = new ParameterizedTypeReference<>() {
    };

    private final RestClient es;
    private final SearchProperties props;

    public ChunkIndexInitializer(RestClient.Builder builder, SearchProperties props) {
        this.es = builder
                .baseUrl(props.baseUrl())
                .build();
        this.props = props;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        ensureIndex();
    }

    /**
     * Idempotent: safe to call on every startup, from any number of instances.
     */
    public void ensureIndex() throws IOException {
        if (aliasExists()) {
            verifyMapping();
            log.info("Alias {} exists; mapping matches config ({} dims, {})", props.alias(), props.embeddingDims(),
                    props.embeddingModel());
            return;
        }
        String indexName = indexName();
        createIndexIfMissing(indexName);
        addAlias(indexName);
        log.info("Alias {} now points to {}", props.alias(), indexName);
    }

    String indexName() {
        return props.alias() + "_v" + props.mappingVersion();
    }

    private boolean aliasExists() {
        try {
            es
                    .head()
                    .uri("/_alias/{name}", props.alias())
                    .retrieve()
                    .toBodilessEntity();
            return true;
        } catch (HttpClientErrorException.NotFound e) {
            return false;
        }
    }

    private void createIndexIfMissing(String indexName) throws IOException {
        String body = loadMapping();
        try {
            es
                    .put()
                    .uri("/{index}", indexName)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
            log.info("Created index {}", indexName);
        } catch (HttpClientErrorException.BadRequest e) {
            // Another instance created it first. That is fine.
            if (e
                    .getResponseBodyAsString()
                    .contains("resource_already_exists_exception")) {
                log.info("Index {} already exists (created by another instance)", indexName);
            } else {
                throw e;
            }
        }
    }

    private void addAlias(String indexName) {
        // One request, one atomic action. Chapter 19 uses remove + add the same way.
        String body = """
                {"actions":[{"add":{"index":"%s","alias":"%s"}}]}
                """.formatted(indexName, props.alias());
        es
                .post()
                .uri("/_aliases")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toBodilessEntity();
    }

    @SuppressWarnings("unchecked")
    private void verifyMapping() {
        Map<String, Object> response = es
                .get()
                .uri("/{alias}/_mapping", props.alias())
                .retrieve()
                .body(MAP_TYPE);

        for (Object indexBody : response.values()) {
            Map<String, Object> mappings = (Map<String, Object>)((Map<String, Object>)indexBody).get("mappings");
            Map<String, Object> meta = (Map<String, Object>)mappings.get("_meta");

            int actualDims = ((Number)meta.get("embedding_dims")).intValue();
            String actualModel = (String)meta.get("embedding_model");

            if (actualDims != props.embeddingDims() || !props
                    .embeddingModel()
                    .equals(actualModel)) {
                throw new IllegalStateException(
                        "Index behind alias '" + props.alias() + "' was built for model " + actualModel + " with "
                                + actualDims + " dims, but config says " + props.embeddingModel() + " with "
                                + props.embeddingDims() + " dims. "
                                + "Create a new index version and re-index (see Chapter 19).");
            }
        }
    }

    private String loadMapping() throws IOException {
        String template = new ClassPathResource("es/chunks-mapping.json").getContentAsString(StandardCharsets.UTF_8);
        return template
                .replace("__DIMS__", String.valueOf(props.embeddingDims()))
                .replace("__MODEL__", props.embeddingModel())
                .replace("__MAPPING_VERSION__", String.valueOf(props.mappingVersion()));
    }
}
