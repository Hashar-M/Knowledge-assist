//package com.abcorg.knowledge.assist.runner;
//
//@Testcontainers
//public class ChunkIndexInitializerTest {
//    // Use the same pinned 9.x tag as docker-compose.yml (see docs/versions.md).
//    // Testcontainers changed its security defaults across versions. If the container
//    // expects HTTPS or a password, check the module README for the current setting to
//    // disable security for tests. Do not copy an old snippet blindly.
//    @Container
//    static ElasticsearchContainer es =
//            new ElasticsearchContainer("docker.elastic.co/elasticsearch/elasticsearch:9.0.0")
//                    .withEnv("xpack.security.enabled", "false");
//
//    private static final ParameterizedTypeReference<Map<String, Object>> MAP =
//            new ParameterizedTypeReference<>() {};
//
//    private SearchProperties props(int dims) {
//        return new SearchProperties("http://" + es.getHttpHostAddress(),
//                "chunks", 1, dims, "text-embedding-3-small");
//    }
//
//    private RestClient http() {
//        return RestClient.builder().baseUrl("http://" + es.getHttpHostAddress()).build();
//    }
//
//    @BeforeEach
//    void resetIndex() {
//        try {
//            http().delete().uri("/chunks_v1").retrieve().toBodilessEntity();
//        } catch (HttpClientErrorException.NotFound ignored) {
//            // Nothing to delete. Deleting the index also removes the alias.
//        }
//    }
//
//    @Test
//    void createsVersionedIndexBehindAlias() throws Exception {
//        new ChunkIndexInitializer(RestClient.builder(), props(1536)).ensureIndex();
//
//        Map<String, Object> aliases = http().get().uri("/_alias/chunks")
//                .retrieve().body(MAP);
//        assertThat(aliases).containsKey("chunks_v1");
//    }
//
//    @Test
//    void secondRunIsNoOp() throws Exception {
//        ChunkIndexInitializer init = new ChunkIndexInitializer(RestClient.builder(), props(1536));
//        init.ensureIndex();
//        init.ensureIndex(); // must not throw
//
//        Map<String, Object> aliases = http().get().uri("/_alias/chunks")
//                .retrieve().body(MAP);
//        assertThat(aliases).hasSize(1);
//    }
//
//    @Test
//    void failsFastWhenDimsDoNotMatchExistingIndex() throws Exception {
//        new ChunkIndexInitializer(RestClient.builder(), props(1536)).ensureIndex();
//
//        assertThatThrownBy(() ->
//                new ChunkIndexInitializer(RestClient.builder(), props(768)).ensureIndex())
//                .isInstanceOf(IllegalStateException.class)
//                .hasMessageContaining("1536 dims");
//    }
//
//    @Test
//    void keywordFieldMatchesExactProductName() throws Exception {
//        new ChunkIndexInitializer(RestClient.builder(), props(1536)).ensureIndex();
//
//        index("{\"doc_id\":\"d1\",\"product\":\"Spring Boot\",\"content\":\"hello\","
//                + "\"embedding\":" + vector(1536) + "}");
//
//        String query = "{\"query\":{\"term\":{\"product\":\"Spring Boot\"}}}";
//        Map<String, Object> resp = http().post().uri("/chunks/_search")
//                .contentType(MediaType.APPLICATION_JSON).body(query)
//                .retrieve().body(MAP);
//
//        @SuppressWarnings("unchecked")
//        Map<String, Object> total = (Map<String, Object>)
//                ((Map<String, Object>) resp.get("hits")).get("total");
//        assertThat(total.get("value")).isEqualTo(1);
//    }
//
//    @Test
//    void rejectsWrongVectorLength() throws Exception {
//        new ChunkIndexInitializer(RestClient.builder(), props(1536)).ensureIndex();
//
//        assertThatExceptionOfType(HttpClientErrorException.BadRequest.class).isThrownBy(() ->
//                index("{\"doc_id\":\"d2\",\"product\":\"Kafka\",\"content\":\"x\","
//                        + "\"embedding\":" + vector(768) + "}"));
//    }
//
//    private void index(String json) {
//        http().post().uri("/chunks/_doc?refresh=true")
//                .contentType(MediaType.APPLICATION_JSON).body(json)
//                .retrieve().toBodilessEntity();
//    }
//
//    /** A non-zero vector of the given length. Zero vectors are rejected by cosine similarity. */
//    private static String vector(int dims) {
//        return IntStream.range(0, dims)
//                .mapToObj(i -> i == 0 ? "1.0" : "0.0")
//                .collect(joining(",", "[", "]"));
//    }
//}
