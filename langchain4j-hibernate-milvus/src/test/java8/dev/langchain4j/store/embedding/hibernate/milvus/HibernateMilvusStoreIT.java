package dev.langchain4j.store.embedding.hibernate.milvus;

import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.filter.logical.Not;
import io.milvus.v2.client.ConnectConfig;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.common.IndexParam;
import io.milvus.v2.service.collection.request.CreateCollectionReq;
import io.milvus.v2.service.collection.request.DescribeCollectionReq;
import io.milvus.v2.service.collection.request.DropCollectionReq;
import io.milvus.v2.service.index.request.CreateIndexReq;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.annotations.Type;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.cfg.AvailableSettings;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * @author 芋道源码
 */
@EnabledIfEnvironmentVariable(named = "MILVUS_FIXTURE_URI", matches = ".+")
class HibernateMilvusStoreIT {

    private static final float[] EXACT = {1, 0, 0};
    private static final float[] NEAR = {0.6f, 0.8f, 0};
    private static final String QUOTED = "芋道 \"quoted\" \\ text";
    private MilvusClientV2 client;
    private StandardServiceRegistry registry;
    private SessionFactory factory;
    private HibernateMilvusStore<Document> store;
    private String collection;

    @BeforeEach
    void setUp() {
        assertTrue(System.getProperty("java.version").startsWith("1.8."));
        registry = new StandardServiceRegistryBuilder()
                .applySetting(AvailableSettings.DIALECT, "org.hibernate.dialect.PostgreSQL10Dialect")
                .applySetting("hibernate.temp.use_jdbc_metadata_defaults", "false").build();
        factory = new MetadataSources(registry).addAnnotatedClass(Document.class).buildMetadata().buildSessionFactory();
        client = new MilvusClientV2(ConnectConfig.builder().uri(System.getenv("MILVUS_FIXTURE_URI")).build());
        collection = "lc_document_" + UUID.randomUUID().toString().replace("-", "");
        store = new HibernateMilvusStore<>(client, factory, Document.class, collection, "vector", 3);
        store.initializeSchema(false);
    }

    @AfterEach
    void tearDown() {
        try {
            if (client != null) {
                try {
                    client.dropCollection(DropCollectionReq.builder().collectionName(collection).build());
                } finally {
                    client.close();
                }
            }
        } finally {
            if (factory != null) factory.close();
            if (registry != null) StandardServiceRegistryBuilder.destroy(registry);
        }
    }

    @Test
    void filtersBeforeLimitAndReturnsDetachedHibernateEntity() {
        store.upsert(new Document(1L, "en", 12, EXACT));
        store.upsert(new Document(2L, QUOTED, 5, NEAR));
        Filter filter = metadataKey("label").isEqualTo(QUOTED).and(metadataKey("rank").isGreaterThan(4));
        List<EmbeddingMatch<Document>> matches = store.search(search(filter, 0.75)).matches();
        assertEquals(1, matches.size());
        EmbeddingMatch<Document> match = matches.get(0);
        assertEquals("2", match.embeddingId());
        assertEquals(0.8, match.score(), 0.0001);
        assertArrayEquals(NEAR, match.embedding().vector());
        assertArrayEquals(NEAR, match.embedded().vector);
        assertEquals(QUOTED, match.embedded().label);
        assertEquals(5, match.embedded().rank);
        try (Session session = factory.openSession()) {
            assertFalse(session.contains(match.embedded()));
        }
        assertTrue(store.search(search(filter, 0.85)).matches().isEmpty());
    }

    @Test
    void queryUpsertAndRemoveUseMappedIdAndScalarMembership() {
        store.upsert(new Document(3L, "old", 5, NEAR));
        store.upsert(new Document(3L, "new", 9, EXACT));
        store.upsert(new Document(4L, "other", 9, NEAR));
        Filter filter = metadataKey("id").isIn(3L, 4L).and(new Not(metadataKey("label").isEqualTo("other")));
        List<Document> matches = store.query(filter, 10);
        assertEquals(1, matches.size());
        assertEquals(3L, matches.get(0).id);
        assertEquals("new", matches.get(0).label);
        assertArrayEquals(EXACT, matches.get(0).vector);
        store.remove(3L);
        assertTrue(store.query(filter, 10).isEmpty());
    }

    @Test
    void schemaIsIdempotentAndRejectsAnIncompatibleVectorBeforeWriting() {
        store.upsert(new Document(1L, "preserved", 5, EXACT));
        store.initializeSchema(false);
        HibernateMilvusStore<Document> mismatch =
                new HibernateMilvusStore<>(client, factory, Document.class, collection, "vector", 4);
        assertThrows(IllegalStateException.class, () -> mismatch.initializeSchema(false));
        assertThrows(IllegalArgumentException.class, () -> store.upsert(new Document(2L, "bad", 5, new float[3])));
        assertThrows(IllegalArgumentException.class, () -> store.query(metadataKey("unknown").isEqualTo("x"), 1));
        assertEquals("preserved", store.query(metadataKey("id").isEqualTo(1L), 1).get(0).label);
    }

    @Test
    void rejectsAnExistingIndexWithADifferentMetric() {
        String name = collection + "_l2";
        CreateCollectionReq.CollectionSchema schema = client.describeCollection(
                DescribeCollectionReq.builder().collectionName(collection).build()).getCollectionSchema();
        try {
            client.createCollection(CreateCollectionReq.builder().collectionName(name).collectionSchema(schema).build());
            IndexParam l2 = IndexParam.builder().fieldName("doc_vector").indexName("doc_vector")
                    .metricType(IndexParam.MetricType.L2).indexType(IndexParam.IndexType.FLAT).build();
            client.createIndex(CreateIndexReq.builder().collectionName(name).sync(false)
                    .indexParams(Collections.singletonList(l2)).build());
            HibernateMilvusStore<Document> existing =
                    new HibernateMilvusStore<>(client, factory, Document.class, name, "vector", 3);
            assertThrows(IllegalStateException.class, () -> existing.initializeSchema(false));
        } finally {
            client.dropCollection(DropCollectionReq.builder().collectionName(name).build());
        }
    }

    private static EmbeddingSearchRequest search(Filter filter, double minScore) {
        return EmbeddingSearchRequest.builder().queryEmbedding(Embedding.from(EXACT))
                .filter(filter).maxResults(1).minScore(minScore).build();
    }

    /** @author 芋道源码 */
    @Entity
    static class Document {
        @Id
        @Column(name = "doc_id")
        Long id;

        @Column(name = "doc_label")
        String label;

        @Column(name = "doc_rank")
        int rank;

        @Type(type = "dev.langchain4j.store.embedding.hibernate.pgvector.PgVectorType")
        @Column(name = "doc_vector", columnDefinition = "vector(3)")
        float[] vector;

        protected Document() {}

        Document(Long id, String label, int rank, float[] vector) {
            this.id = id;
            this.label = label;
            this.rank = rank;
            this.vector = vector;
        }
    }
}
