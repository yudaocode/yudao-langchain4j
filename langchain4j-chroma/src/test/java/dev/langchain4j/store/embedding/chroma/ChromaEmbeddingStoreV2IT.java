package dev.langchain4j.store.embedding.chroma;

import static dev.langchain4j.internal.Utils.randomUUID;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreWithFilteringIT;
import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.filter.comparison.IsGreaterThan;
import dev.langchain4j.store.embedding.filter.comparison.IsGreaterThanOrEqualTo;
import dev.langchain4j.store.embedding.filter.comparison.IsLessThan;
import dev.langchain4j.store.embedding.filter.comparison.IsLessThanOrEqualTo;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.testcontainers.chromadb.ChromaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class ChromaEmbeddingStoreV2IT extends EmbeddingStoreWithFilteringIT {

    @Container
    private static final ChromaDBContainer chroma =
            new ChromaDBContainer("chromadb/chroma:1.1.0").withExposedPorts(8000);

    EmbeddingStore<TextSegment> embeddingStore = exactVectorStore();

    // Chroma 1.1 cosine vector round-trips can differ by one ULP. Exercise inherited exact
    // vector contracts on L2; cosine score contracts remain in DistanceMetricIT.
    private static ChromaEmbeddingStore exactVectorStore() {
        String endpoint = chroma.getEndpoint();
        ChromaEmbeddingStore.builder()
                .apiVersion(ChromaApiVersion.V2)
                .baseUrl(endpoint)
                .collectionName(randomUUID())
                .build();
        String name = randomUUID();
        try {
            new ChromaHttpClient(endpoint, Duration.ofSeconds(10), false, false)
                    .post(
                            "api/v2/tenants/default/databases/default/collections",
                            Map.of("name", name, "metadata", Map.of("hnsw:space", "l2")),
                            Collection.class);
        } catch (IOException error) {
            throw new IllegalStateException("Cannot create exact-vector test collection", error);
        }
        return ChromaEmbeddingStore.builder()
                .apiVersion(ChromaApiVersion.V2)
                .baseUrl(endpoint)
                .collectionName(name)
                .build();
    }

    EmbeddingModel embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();

    @Override
    protected EmbeddingStore<TextSegment> embeddingStore() {
        return embeddingStore;
    }

    @Override
    protected void assertScore(EmbeddingMatch<TextSegment> match, double expectedCosineScore) {
        // Unit vectors have squared L2 distance 2 - 2*cosine. The inherited suite
        // expresses expected scores as (1 + cosine)/2; retain its 1% tolerance.
        super.assertScore(match, 1.0 / (5.0 - 4.0 * expectedCosineScore));
    }

    @Override
    protected EmbeddingModel embeddingModel() {
        return embeddingModel;
    }

    @Override
    @ParameterizedTest
    @MethodSource("should_filter_by_metadata_chroma")
    protected void should_filter_by_metadata(
            Filter metadataFilter, List<Metadata> matchingMetadatas, List<Metadata> notMatchingMetadatas) {
        super.should_filter_by_metadata(metadataFilter, matchingMetadatas, notMatchingMetadatas);
    }

    // in chroma compare filter only works with numbers
    protected static Stream<Arguments> should_filter_by_metadata_chroma() {
        return EmbeddingStoreWithFilteringIT.should_filter_by_metadata().filter(arguments -> {
            Filter filter = (Filter) arguments.get()[0];
            if (filter instanceof IsLessThan) {
                return ((IsLessThan) filter).comparisonValue() instanceof Number;
            } else if (filter instanceof IsLessThanOrEqualTo) {
                return ((IsLessThanOrEqualTo) filter).comparisonValue() instanceof Number;
            } else if (filter instanceof IsGreaterThan) {
                return ((IsGreaterThan) filter).comparisonValue() instanceof Number;
            } else if (filter instanceof IsGreaterThanOrEqualTo) {
                return ((IsGreaterThanOrEqualTo) filter).comparisonValue() instanceof Number;
            } else {
                return true;
            }
        });
    }
}
