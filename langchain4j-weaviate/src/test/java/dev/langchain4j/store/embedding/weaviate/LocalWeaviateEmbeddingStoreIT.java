package dev.langchain4j.store.embedding.weaviate;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIT;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.weaviate.WeaviateContainer;

import java.util.Arrays;
import java.util.List;

import static dev.langchain4j.internal.Utils.randomUUID;

@Testcontainers
class LocalWeaviateEmbeddingStoreIT extends EmbeddingStoreIT {

    static WeaviateContainer weaviateContainer() {
        String image = System.getProperty("weaviate.image", "cr.weaviate.io/semitechnologies/weaviate:1.40.5");
        return new WeaviateContainer(DockerImageName.parse(image).asCompatibleSubstituteFor("semitechnologies/weaviate"))
                .withEnv("QUERY_DEFAULTS_LIMIT", "25")
                .withEnv("DEFAULT_VECTORIZER_MODULE", "none")
                .withEnv("CLUSTER_HOSTNAME", "node1")
                .withEnv("AUTHENTICATION_ANONYMOUS_ACCESS_ENABLED", "true")
                .withEnv("PERSISTENCE_DATA_PATH", "/var/lib/weaviate")
                .withTmpFs(java.util.Collections.singletonMap("/var/lib/weaviate", "rw,size=512m"));
    }

    @Container
    static WeaviateContainer weaviate = weaviateContainer();

    static final List<String> METADATA_KEYS = Arrays.asList(
            "string_empty",
            "string_space",
            "string_abc",
            "uuid",
            "integer_min",
            "integer_minus_1",
            "integer_0",
            "integer_1",
            "integer_max",
            "long_min",
            "long_minus_1",
            "long_0",
            "long_1",
            "long_1746714878034235396",
            "long_max",
            "float_min",
            "float_minus_1",
            "float_0",
            "float_1",
            "float_123",
            "float_max",
            "double_minus_1",
            "double_0",
            "double_1",
            "double_123");

    private final EmbeddingStore<TextSegment> embeddingStore = WeaviateEmbeddingStore.builder()
            .scheme("http")
            .host(weaviate.getHost())
            .port(weaviate.getFirstMappedPort())
            .objectClass("Test" + randomUUID().replace("-", ""))
            .metadataKeys(METADATA_KEYS)
            .build();

    EmbeddingModel embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();

    @Override
    protected EmbeddingStore<TextSegment> embeddingStore() {
        return embeddingStore;
    }

    @Override
    protected EmbeddingModel embeddingModel() {
        return embeddingModel;
    }

    @Override
    protected void clearStore() {
        embeddingStore.removeAll();
    }
}
