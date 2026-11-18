package dev.langchain4j.store.embedding.hibernate;

import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.hibernate.pgvector.PgVectorFunctions;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.annotations.Parameter;
import org.hibernate.annotations.Type;
import org.hibernate.boot.MetadataBuilder;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.cfg.AvailableSettings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.postgresql.ds.PGSimpleDataSource;

/**
 * @author 芋道源码
 */
@EnabledIfEnvironmentVariable(named = "HIBERNATE_VECTOR_JDBC", matches = ".+")
class HibernateEmbeddingStorePostgresIT {
    private static final Embedding SAME = Embedding.from(new float[] {1, 0, 0});
    private static final Embedding SIMILAR = Embedding.from(new float[] {0.6f, 0.8f, 0});

    @Test
    void storesSearchesAndRemovesCustomMetadataThroughOriginalInterface() throws Exception {
        PGSimpleDataSource source = source();
        try (Connection conn = source.getConnection(); Statement sql = conn.createStatement()) {
            sql.execute("create schema if not exists lc_vector_test");
        }
        String table = "lc_vector_test.vectors";
        try (HibernateEmbeddingStore<EmbeddingEntity> first = HibernateEmbeddingStore.dynamicDatasourceBuilder()
                .dataSource(source).dimension(3).table(table).createTable(true)
                .createIndex(true).indexType("hnsw").indexOptions("m=8,ef_construction=24").build();
             HibernateEmbeddingStore<EmbeddingEntity> reopened = HibernateEmbeddingStore.dynamicBuilder()
                     .jdbcUrl(System.getenv("HIBERNATE_VECTOR_JDBC")).user("compat").password("fixture-only")
                     .dimension(3).table(table).build()) {
            EmbeddingStore<TextSegment> store = first;
            String key = "tag'quoted";
            String firstId = store.add(SAME, TextSegment.from("中文含%_单词",
                    Metadata.from(key, "中文").put("rank", 5)));
            String otherId = store.add(SAME, TextSegment.from("English document", Metadata.from("rank", "English")));
            String smallId = store.add(SIMILAR, TextSegment.from("第二条",
                    Metadata.from(key, "中文").put("rank", 3)));
            Filter filter = metadataKey(key).isEqualTo("中文").and(metadataKey("rank").isGreaterThanOrEqualTo(4));
            List<EmbeddingMatch<TextSegment>> results = reopened.search(query(filter, 0.9)).matches();
            assertEquals(1, results.size());
            assertEquals(firstId, results.get(0).embeddingId());
            assertEquals("中文含%_单词", results.get(0).embedded().text());
            assertEquals(5, results.get(0).embedded().metadata().getInteger("rank"));
            assertArrayEquals(SAME.vector(), results.get(0).embedding().vector());
            assertEquals(1, store.search(query(metadataKey(key).isNotIn("中文"), 0)).matches().size());

            store.addAll(Collections.singletonList(firstId), Collections.singletonList(SIMILAR),
                    Collections.singletonList(TextSegment.from("跟进", new Metadata().put("rank", 10))));
            assertEquals(0, store.search(query(filter, 0)).matches().size());
            store.removeAll(metadataKey("rank").isLessThan(4));
            assertEquals(0, reopened.search(query(metadataKey(key).isEqualTo("中文"), 0)).matches().size());
            store.remove(otherId);
            assertEquals(firstId, reopened.search(query(null, 0)).matches().get(0).embeddingId());
            assertFalse(reopened.search(query(null, 0)).matches().stream()
                    .anyMatch(hit -> smallId.equals(hit.embeddingId())));
            store.removeAll();
            assertTrue(reopened.search(query(null, 0)).matches().isEmpty());
        }
        try (Connection conn = source.getConnection(); Statement sql = conn.createStatement();
             ResultSet result = sql.executeQuery("select indexdef from pg_indexes where schemaname='lc_vector_test' "
                     + "and indexname='vectors_hnsw_index'")) {
            assertTrue(result.next());
            assertTrue(result.getString(1).contains("USING hnsw"));
        }
    }

    @Test
    void rollsBackEntireBatchOnInvalidVector() {
        try (HibernateEmbeddingStore<EmbeddingEntity> store = HibernateEmbeddingStore.dynamicDatasourceBuilder()
                .dataSource(source()).dimension(3).table("lc_transaction_vectors").createTable(true).build()) {
            Embedding invalid = Embedding.from(new float[] {1, 2});
            assertThrows(RuntimeException.class, () -> store.addAll(Arrays.asList(SAME, invalid)));
            assertTrue(store.search(query(null, 0)).matches().isEmpty());
        }
    }

    @Test
    void storesTypedHalfvecEntitiesAndKeepsTheSuppliedFactoryOpen() {
        StandardServiceRegistry registry = new StandardServiceRegistryBuilder()
                .applySetting(AvailableSettings.DATASOURCE, source())
                .applySetting(AvailableSettings.HBM2DDL_AUTO, "create-drop")
                .build();
        try {
            MetadataBuilder metadata = new MetadataSources(registry).addAnnotatedClass(Document.class).getMetadataBuilder();
            new PgVectorFunctions().contribute(metadata);
            try (SessionFactory factory = metadata.build().buildSessionFactory()) {
                String id;
                try (HibernateEmbeddingStore<Document> store = HibernateEmbeddingStore.builder(Document.class)
                        .sessionFactory(factory).build()) {
                    id = store.add(SAME, TextSegment.from("半精度中文", Metadata.from("lang", "zh").put("rank", 6)));
                    String id2 = UUID.randomUUID().toString();
                    store.add(id2, SIMILAR);
                    assertEquals(2, store.query(SAME, null).size());
                    EmbeddingMatch<TextSegment> hit = store.search(query(metadataKey("lang").isEqualTo("zh")
                            .and(metadataKey("rank").isGreaterThan(3)), 0.7)).matches().get(0);
                    assertEquals(id, hit.embeddingId());
                    assertEquals("半精度中文", hit.embedded().text());
                    assertEquals("zh", hit.embedded().metadata().getString("lang"));
                    assertEquals(6, hit.embedded().metadata().getInteger("rank"));
                    assertEquals(1.0, hit.score(), 1e-6);
                    store.remove(id2);
                    assertEquals(1, store.query(SAME, (root, cb) -> cb.equal(root.get("lang"), "zh")).size());
                }
                assertFalse(factory.isClosed());
                try (Session session = factory.openSession()) {
                    assertNotNull(session.find(Document.class, UUID.fromString(id)));
                }
            }
        } finally {
            StandardServiceRegistryBuilder.destroy(registry);
        }
    }

    private static PGSimpleDataSource source() {
        PGSimpleDataSource source = new PGSimpleDataSource();
        source.setUrl(System.getenv("HIBERNATE_VECTOR_JDBC"));
        source.setUser("compat");
        source.setPassword("fixture-only");
        return source;
    }

    private static EmbeddingSearchRequest query(Filter filter, double minScore) {
        return EmbeddingSearchRequest.builder().queryEmbedding(SAME).filter(filter)
                .maxResults(10).minScore(minScore).build();
    }

    /** @author 芋道源码 */
    @Entity
    @Table(name = "lc_typed_halfvec")
    static class Document {
        @Id
        @Type(type = "pg-uuid")
        UUID id;

        @EmbeddingVector
        @Type(type = "dev.langchain4j.store.embedding.hibernate.pgvector.PgVectorType",
                parameters = @Parameter(name = "storage", value = "halfvec"))
        @Column(columnDefinition = "halfvec(3)")
        float[] vector;

        @EmbeddedText
        String body;

        @UnmappedMetadata
        @Type(type = "dev.langchain4j.store.embedding.hibernate.JsonbMetadataType")
        @Column(columnDefinition = "jsonb")
        String metadata;

        @MetadataAttribute
        String lang;
    }
}
