package dev.langchain4j.store.embedding.infinispan;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreWithRemovalIT;
import dev.langchain4j.store.infinispan.InfinispanServer;
import org.infinispan.client.hotrod.configuration.ConfigurationBuilder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.BeforeEach;

class InfinispanEmbeddingStoreRemovalIT extends EmbeddingStoreWithRemovalIT {

    static final InfinispanServer infinispan = new InfinispanServer();
    static final EmbeddingModel embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();
    static InfinispanEmbeddingStore embeddingStore;

    @BeforeAll
    static void beforeAll() {
        infinispan.start();
        ConfigurationBuilder builder = new ConfigurationBuilder();
        infinispan.configure(builder);
        builder.socketTimeout(5000);
        builder.maxRetries(10);
        embeddingStore = InfinispanEmbeddingStore.builder()
                .cacheName("my-cache")
                .dimension(embeddingModel.dimension())
                .infinispanConfigBuilder(builder)
                .build();
    }

    @AfterAll
    static void afterAll() {
        if (embeddingStore != null) {
            embeddingStore.getRemoteCache().getRemoteCacheManager().stop();
        }
        infinispan.stop();
    }

    @BeforeEach
    void clearStore() {
        embeddingStore.clearCache();
    }

    @Override
    protected EmbeddingStore<TextSegment> embeddingStore() {
        return embeddingStore;
    }

    @Override
    protected EmbeddingModel embeddingModel() {
        return embeddingModel;
    }
}
