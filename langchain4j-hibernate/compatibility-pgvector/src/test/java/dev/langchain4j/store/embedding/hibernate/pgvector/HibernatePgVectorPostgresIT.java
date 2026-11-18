package dev.langchain4j.store.embedding.hibernate.pgvector;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.store.embedding.hibernate.pgvector.HibernatePgVectorSearch.Match;
import dev.langchain4j.store.embedding.hibernate.pgvector.HibernatePgVectorSearch.Metric;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
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
import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * @author 芋道源码
 */
@EnabledIfEnvironmentVariable(named = "HIBERNATE_VECTOR_JDBC", matches = ".+")
class HibernatePgVectorPostgresIT {

    private static final Embedding REFERENCE = Embedding.from(new float[] {1, 0, 0});
    private static final AtomicReference<String> LAST_SQL = new AtomicReference<>();
    private static StandardServiceRegistry registry;
    private static SessionFactory factory;

    @BeforeAll
    static void startHibernate() {
        registry = new StandardServiceRegistryBuilder()
                .applySetting("hibernate.connection.url", System.getenv("HIBERNATE_VECTOR_JDBC"))
                .applySetting("hibernate.connection.username", "compat")
                .applySetting("hibernate.connection.password", "fixture-only")
                .applySetting("hibernate.dialect", "org.hibernate.dialect.PostgreSQL10Dialect")
                .applySetting("hibernate.hbm2ddl.auto", "create-drop")
                .applySetting("hibernate.session_factory.statement_inspector", (StatementInspector) sql -> {
                    LAST_SQL.set(sql);
                    return sql;
                })
                .build();
        MetadataBuilder metadata = new MetadataSources(registry).addAnnotatedClass(Item.class).getMetadataBuilder();
        new PgVectorFunctions().contribute(metadata);
        factory = metadata.build().buildSessionFactory();
        try (Session session = factory.openSession()) {
            session.beginTransaction();
            session.persist(new Item("a", "中文文档", "zh", new float[] {1, 0, 0}));
            session.persist(new Item("b", "English document", "en", new float[] {1, 0, 0}));
            session.persist(new Item("c", "较低分", "zh", new float[] {0.6f, 0.8f, 0}));
            session.persist(new Item("d", "零向量", "zh", new float[3]));
            session.persist(new Item("e", "空向量", "zh", null));
            session.getTransaction().commit();
        }
    }

    @AfterAll
    static void closeHibernate() {
        if (factory != null) {
            factory.close();
        }
        if (registry != null) {
            StandardServiceRegistryBuilder.destroy(registry);
        }
    }

    @Test
    void filtersBeforeApplyingLimitAndUsesCallerPersistenceContext() {
        try (Session session = factory.openSession()) {
            HibernatePgVectorSearch<Item> search = new HibernatePgVectorSearch<>(Item.class, "vector");
            List<Match<Item>> matches = search.search(session, REFERENCE, Metric.COSINE, 0.85, 1,
                    (root, criteria) -> criteria.equal(root.get("language"), "zh"));
            assertEquals(1, matches.size());
            assertEquals("中文文档", matches.get(0).entity().body);
            assertSame(session.find(Item.class, "a"), matches.get(0).entity());
            assertEquals(1.0, matches.get(0).score(), 1e-6);
            List<Match<Item>> withoutMinimum = search.search(session, REFERENCE, Metric.COSINE, 0, 10,
                    (root, criteria) -> criteria.equal(root.get("language"), "zh"));
            assertEquals(2, withoutMinimum.size());
            assertEquals("较低分", withoutMinimum.get(1).entity().body);
            assertEquals(0.8, withoutMinimum.get(1).score(), 1e-6);
        }
    }

    @Test
    void readsEveryMetricAndTracksInPlaceArrayChanges() {
        try (Session session = factory.openSession()) {
            HibernatePgVectorSearch<Item> search = new HibernatePgVectorSearch<>(Item.class, "vector");
            for (Metric metric : Metric.values()) {
                List<Match<Item>> matches = search.search(session, REFERENCE, metric, 0.98, 2,
                        (root, criteria) -> criteria.equal(root.get("id"), "a"));
                assertEquals(1, matches.size(), metric.name());
                assertEquals("a", matches.get(0).entity().id, metric.name());
                assertEquals(1.0, matches.get(0).score(), 1e-6, metric.name());
            }

            session.beginTransaction();
            session.find(Item.class, "a").vector[0] = 0.2f;
            session.flush();
            assertEquals(0, search.search(session, REFERENCE, Metric.INNER_PRODUCT, 0.85, 1,
                    (root, criteria) -> criteria.equal(root.get("id"), "a")).size());
            session.getTransaction().rollback();
            session.clear();
            assertArrayEquals(new float[] {1, 0, 0}, session.find(Item.class, "a").vector, 1e-6f);
        }
    }

    @Test
    void usesNativeHalfvecOperatorsWithAHalfvecIndex() {
        try (Session session = factory.openSession()) {
            session.beginTransaction();
            session.doWork(connection -> {
                try (java.sql.Statement statement = connection.createStatement()) {
                    statement.execute("create index lc_hibernate5_vector_half_idx on lc_hibernate5_vector_fixture "
                            + "using hnsw (half_vector halfvec_cosine_ops)");
                    statement.execute("set local enable_seqscan=off");
                }
            });
            HibernatePgVectorSearch<Item> search = new HibernatePgVectorSearch<>(Item.class, "halfVector");
            List<Match<Item>> matches = search.search(session, REFERENCE, Metric.COSINE, 0.75, 3,
                    (root, criteria) -> criteria.equal(root.get("language"), "zh"));
            assertEquals(2, matches.size());
            assertEquals("a", matches.get(0).entity().id);
            assertEquals("c", matches.get(1).entity().id);
            assertArrayEquals(new float[] {0.6f, 0.8f, 0}, matches.get(1).entity().halfVector, 5e-4f);
            assertTrue(LAST_SQL.get().contains("<=> cast(? as halfvec)"), LAST_SQL.get());
            session.getTransaction().rollback();
        }
    }

    /**
     * @author 芋道源码
     */
    @Entity
    @Table(name = "lc_hibernate5_vector_fixture")
    static class Item {
        @Id
        String id;
        String body;
        String language;

        @Type(type = "dev.langchain4j.store.embedding.hibernate.pgvector.PgVectorType")
        @Column(columnDefinition = "vector(3)")
        float[] vector;

        @Type(
                type = "dev.langchain4j.store.embedding.hibernate.pgvector.PgVectorType",
                parameters = @Parameter(name = "storage", value = "halfvec"))
        @Column(name = "half_vector", columnDefinition = "halfvec(3)")
        float[] halfVector;

        protected Item() {}

        Item(String id, String body, String language, float[] vector) {
            this.id = id;
            this.body = body;
            this.language = language;
            this.vector = vector;
            this.halfVector = vector;
        }
    }
}
