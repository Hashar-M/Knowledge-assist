package com.abcorg.knowledge.assist.runner;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.cluster.HealthResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class ElasticsearchStartupCheck implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(ElasticsearchStartupCheck.class);

    private final ElasticsearchClient client;

    ElasticsearchStartupCheck(ElasticsearchClient client) {
        this.client = client;
    }

    @Override
    public void run(ApplicationArguments args) throws IOException {
        HealthResponse health = client.cluster().health();
        log.info("Elasticsearch cluster='{}' status={} nodes={}",
                health.clusterName(), health.status(), health.numberOfNodes());
    }
}
