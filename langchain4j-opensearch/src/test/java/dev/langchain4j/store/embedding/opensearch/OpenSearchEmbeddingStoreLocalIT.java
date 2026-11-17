package dev.langchain4j.store.embedding.opensearch;

import static dev.langchain4j.internal.Utils.randomUUID;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreWithFilteringIT;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.AfterAll;
import org.apache.hc.core5.http.HttpHost;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.json.jackson.JacksonJsonpMapper;
import org.opensearch.client.transport.httpclient5.ApacheHttpClient5TransportBuilder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.testcontainers.junit.jupiter.Container;

class OpenSearchEmbeddingStoreLocalIT extends EmbeddingStoreWithFilteringIT {

    /**
     * To run the tests locally, you don't need to have OpenSearch up-and-running. This implementation
     * uses TestContainers (https://testcontainers.com) and the built-in support for OpenSearch. Thus,
     * if you just execute the tests then a container will be spun up automatically for you.
     */
    @Container
    static Java8OpenSearchContainer opensearch = new Java8OpenSearchContainer();

    private EmbeddingStore<TextSegment> embeddingStore;
    private static OpenSearchClient client;

    private final EmbeddingModel embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();

    @BeforeAll
    static void startOpenSearch() throws Exception {
        opensearch.start();
        client = new OpenSearchClient(ApacheHttpClient5TransportBuilder
                .builder(HttpHost.create(opensearch.getHttpHostAddress()))
                .setMapper(new JacksonJsonpMapper()).build());
    }

    @AfterAll
    static void closeClient() throws Exception {
        if (client != null) {
            client._transport().close();
        }
    }

    @BeforeEach
    void createEmbeddingStore() {
        embeddingStore = OpenSearchEmbeddingStore.builder()
                .openSearchClient(client)
                .indexName(randomUUID())
                .scoreBoost(Float.parseFloat(System.getProperty("opensearch.test.score-boost", "1.0")))
                .build();
    }

    @AfterEach
    void removeDataStore() {
        embeddingStore.removeAll();
    }

    @Override
    protected EmbeddingStore<TextSegment> embeddingStore() {
        return embeddingStore;
    }

    @Override
    protected EmbeddingModel embeddingModel() {
        return embeddingModel;
    }

    @Override
    protected void ensureStoreIsEmpty() {
        // Not needed since we create a new index for each test
    }
}
