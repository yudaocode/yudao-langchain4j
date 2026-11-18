package dev.langchain4j.store.embedding.coherence;

import com.tangosol.net.Session;
import dev.langchain4j.store.coherence.CoherenceTestCluster;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIT;
import dev.langchain4j.store.embedding.EmbeddingStoreWithFilteringIT;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.extension.RegisterExtension;

/**
 * An extension of the LangChain4j {@link EmbeddingStoreIT} tests
 * that use Coherence as an {@link EmbeddingStore}.
 */
class CoherenceEmbeddingStoreIT extends EmbeddingStoreWithFilteringIT {

    @RegisterExtension
    static CoherenceTestCluster cluster = new CoherenceTestCluster();

    static Session session;

    static EmbeddingModel model = new AllMiniLmL6V2QuantizedEmbeddingModel();

    static CoherenceEmbeddingStore embeddingStore;

    @BeforeAll
    static void beforeAll() {
        session = cluster.session();
        embeddingStore = CoherenceEmbeddingStore.builder().session(session).build();
    }

    @Override
    protected EmbeddingStore<TextSegment> embeddingStore() {
        return embeddingStore;
    }

    @Override
    protected EmbeddingModel embeddingModel() {
        return model;
    }

    @Override
    protected void clearStore() {
        embeddingStore.removeAll();
    }
}
