package cn.iocoder.ai.compatibility;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.opensearch.OpenSearchEmbeddingStore;
import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Timeout;
import org.opensearch.client.json.jackson.JacksonJsonpMapper;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.transport.httpclient5.ApacheHttpClient5Transport;
import org.opensearch.client.transport.httpclient5.ApacheHttpClient5TransportBuilder;
import org.apache.hc.core5.http.HttpHost;

/**
 * Exercises the installed Java8 store under the native Boot2 parent's dependency management.
 *
 * @author 芋道源码
 */
@Timeout(30)
class OpenSearchBoot2Test {

    @ParameterizedTest
    @ValueSource(floats = { 0.5f, 1.0f })
    void installedStoreUsesOneSdkProviderAndQueriesWithTheBoot2RuntimeDependencies(float boost) throws Exception {
        assertEquals("1.8", System.getProperty("java.specification.version"));
        List<URL> origins = Collections.list(OpenSearchClient.class.getClassLoader()
                .getResources("org/opensearch/client/opensearch/OpenSearchClient.class"));
        assertEquals(1, origins.size(), origins.toString());
        assertTrue(origins.get(0).toString().contains("spring-ai-opensearch-client-compat"));
        assertEquals("3.11.0", OpenSearchClient.class.getPackage().getImplementationVersion());
        assertEquals("2.21.4", com.fasterxml.jackson.core.json.PackageVersion.VERSION.toString());
        ObjectMapper json = new ObjectMapper();
        AtomicReference<JsonNode> request = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            try {
                request.set(json.readTree(exchange.getRequestBody()));
                String response = "{\"took\":1,\"timed_out\":false,\"_shards\":{\"total\":1,\"successful\":1,\"failed\":0},"
                        + "\"hits\":{\"total\":{\"value\":1,\"relation\":\"eq\"},\"max_score\":0.9,\"hits\":[{"
                        + "\"_index\":\"compat\",\"_id\":\"doc-1\",\"_score\":0.9,\"_source\":{\"text\":\"预算你好\","
                        + "\"metadata\":{\"route\":\"jdk8\"},\"vector\":[0.25,-0.5,1.0]}}]}}";
                byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
                exchange.sendResponseHeaders(200, bytes.length);
                try (java.io.OutputStream output = exchange.getResponseBody()) {
                    output.write(bytes);
                }
            } finally {
                exchange.close();
            }
        });
        server.start();
        try (ApacheHttpClient5Transport transport = ApacheHttpClient5TransportBuilder
                .builder(new HttpHost("http", "127.0.0.1", server.getAddress().getPort()))
                .setMapper(new JacksonJsonpMapper()).build()) {
            OpenSearchEmbeddingStore store = OpenSearchEmbeddingStore.builder()
                    .openSearchClient(new OpenSearchClient(transport)).indexName("compat").scoreBoost(boost).build();
            EmbeddingSearchResult<TextSegment> result = store.search(EmbeddingSearchRequest.builder()
                    .queryEmbedding(Embedding.from(new float[] { 0.25f, -0.5f, 1.0f }))
                    .maxResults(2).minScore(0.5).build());
            assertEquals(1, result.matches().size());
            assertEquals("预算你好", result.matches().get(0).embedded().text());
            assertEquals("jdk8", result.matches().get(0).embedded().metadata().getString("route"));
            JsonNode script = request.get().path("query").path("script_score").path("script");
            assertEquals("knn", script.path("lang").asText());
            assertEquals(boost, request.get().path("query").path("script_score").path("boost").floatValue());
            assertEquals(3, script.path("params").path("query_value").size());
            assertEquals(0.25f, script.path("params").path("query_value").get(0).floatValue());
        } finally {
            server.stop(0);
        }
    }
}
