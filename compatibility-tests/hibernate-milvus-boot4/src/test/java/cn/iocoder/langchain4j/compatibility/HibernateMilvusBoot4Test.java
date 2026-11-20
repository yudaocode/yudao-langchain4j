package cn.iocoder.langchain4j.compatibility;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.hibernate.EmbeddedText;
import dev.langchain4j.store.embedding.hibernate.EmbeddingVector;
import dev.langchain4j.store.embedding.hibernate.HibernateEmbeddingStore;
import dev.langchain4j.store.embedding.hibernate.UnmappedMetadata;
import dev.langchain4j.store.embedding.hibernate.milvus.MilvusDatabaseKind;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.annotations.Array;
import org.hibernate.jpa.HibernateHints;
import org.hibernate.milvus.MilvusDatabaseHints;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.SpringBootVersion;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Exercises Milvus through the installed Hibernate adapter and a real Boot 4 JPA context.
 *
 * @author 芋道源码
 */
class HibernateMilvusBoot4Test {

    private static final String STRONG = MilvusDatabaseHints.CONSISTENCY_LEVEL + "=STRONG";
    private static ConfigurableApplicationContext context;
    private static EntityManager em;
    private static SessionFactory sessionFactory;
    private static TransactionTemplate transactions;

    @BeforeAll
    static void openBootContext() {
        String address = Objects.requireNonNull(
                System.getProperty("milvus.address"), "Set -Dmilvus.address to host:port");
        assertThat(SpringBootVersion.getVersion()).isEqualTo(System.getProperty("compat.boot.version"));
        assertThat(System.getProperty("java.specification.version"))
                .isEqualTo(System.getProperty("compat.java.version"));
        context = new SpringApplicationBuilder(Application.class)
                .web(WebApplicationType.NONE)
                .run(
                        "--spring.datasource.url=jdbc:milvus://" + address + "/default",
                        "--spring.datasource.driver-class-name=org.hibernate.milvus.jdbc.internal.MilvusDriver",
                        "--spring.jpa.hibernate.ddl-auto=create-drop");
        EntityManagerFactory factory = context.getBean(EntityManagerFactory.class);
        sessionFactory = factory.unwrap(SessionFactory.class);
        em = SharedEntityManagerCreator.createSharedEntityManager(factory);
        transactions = new TransactionTemplate(context.getBean(JpaTransactionManager.class));
    }

    @AfterAll
    static void closeBootContext() {
        if (context != null) {
            context.close();
        }
    }

    @Test
    void commitsManagedEntityAndReadsItWithScalarHql() {
        transactions.executeWithoutResult(status -> {
            assertThat(status.isNewTransaction()).isTrue();
            Document document = new Document("commit", "芋道's Milvus", "zh", new float[] {1, 0, 0});
            em.persist(document);
            assertThat(em.contains(document)).isTrue();
        });

        assertThat(textFor("commit")).containsExactly("芋道's Milvus");
    }

    @Test
    void runsFilteredVectorHqlAndTheInstalledEmbeddingStore() {
        transactions.executeWithoutResult(status -> {
            em.persist(new Document("vector-in", "inside", "vector-zh", new float[] {0.8f, 0.6f, 0}));
            em.persist(new Document("vector-out", "outside", "vector-en", new float[] {1, 0, 0}));
        });
        List<Object[]> matches = em.createQuery(
                        "select d.id, cosine_distance(d.vector, :query) as distance "
                                + "from MilvusDocument d where d.locale = :locale order by distance",
                        Object[].class)
                .setParameter("query", new float[] {1, 0, 0})
                .setParameter("locale", "vector-zh")
                .setHint(HibernateHints.HINT_QUERY_DATABASE, STRONG)
                .setMaxResults(1)
                .getResultList();

        assertThat(matches).singleElement().satisfies(row -> {
            assertThat(row[0]).isEqualTo("vector-in");
            assertThat(((Number) row[1]).doubleValue()).isCloseTo(0.2, within(0.001));
        });

        HibernateEmbeddingStore<Document> store = HibernateEmbeddingStore.builder(Document.class)
                .sessionFactory(sessionFactory)
                .databaseKind(MilvusDatabaseKind.INSTANCE)
                .queryHints(Map.of(HibernateHints.HINT_QUERY_DATABASE, STRONG))
                .build();
        EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                .queryEmbedding(Embedding.from(new float[] {1, 0, 0}))
                .maxResults(10)
                .build();
        assertThat(store.search(request).matches())
                .extracting(EmbeddingMatch::embeddingId)
                .contains("vector-in", "vector-out");
    }

    @Test
    void rollbackBeforeFlushDiscardsPendingEntity() {
        transactions.executeWithoutResult(status -> {
            em.persist(new Document("unflushed", "pending", "zh", new float[] {1, 0, 0}));
            status.setRollbackOnly();
        });

        assertThat(textFor("unflushed")).isEmpty();
    }

    @Test
    void rollbackAfterFlushLeavesTheWriteVisible() {
        transactions.executeWithoutResult(status -> {
            boolean supported = em.unwrap(Session.class).doReturningWork(c -> c.getMetaData().supportsTransactions());
            assertThat(supported).isFalse();
            em.persist(new Document("flushed", "already sent", "zh", new float[] {1, 0, 0}));
            em.flush();
            status.setRollbackOnly();
        });

        assertThat(textFor("flushed")).containsExactly("already sent");
    }

    private static List<String> textFor(String id) {
        return em.createQuery("select d.text from MilvusDocument d where d.id = :id", String.class)
                .setParameter("id", id)
                .setHint(HibernateHints.HINT_QUERY_DATABASE, STRONG)
                .getResultList();
    }

    /**
     * Minimal application with Boot-managed JPA.
     *
     * @author 芋道源码
     */
    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class Application {}

    /**
     * Maps the scalar fields plus a three-dimensional cosine vector.
     *
     * @author 芋道源码
     */
    @Entity(name = "MilvusDocument")
    @Table(name = "lc4j_hibernate_milvus_boot4")
    public static class Document {

        @Id
        @Column(length = 64)
        private String id;

        @EmbeddedText
        @Column(length = 200)
        private String text;

        @Column(length = 16)
        private String locale;

        @EmbeddingVector
        @Array(length = 3)
        private float[] vector;

        @UnmappedMetadata
        private String metadata = "{}";

        protected Document() {}

        Document(String id, String text, String locale, float[] vector) {
            this.id = id;
            this.text = text;
            this.locale = locale;
            this.vector = vector;
        }
    }
}
