package cn.iocoder.langchain4j.compatibility;

import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.hibernate.DatabaseKind;
import dev.langchain4j.store.embedding.hibernate.EmbeddedText;
import dev.langchain4j.store.embedding.hibernate.EmbeddingVector;
import dev.langchain4j.store.embedding.hibernate.HibernateEmbeddingStore;
import dev.langchain4j.store.embedding.hibernate.MetadataAttribute;
import dev.langchain4j.store.embedding.hibernate.UnmappedMetadata;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.annotations.Array;
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
 * Exercises PostgreSQL and pgvector through the installed Hibernate adapter and a real Boot 3 JPA context.
 *
 * @author 芋道源码
 */
class HibernatePgvectorBoot3Test {

    private static String jdbcUrl;
    private static ConfigurableApplicationContext context;
    private static EntityManager em;
    private static SessionFactory sessionFactory;
    private static TransactionTemplate transactions;
    private static EmbeddingStore<TextSegment> store;

    @BeforeAll
    static void openBootContext() {
        String address = Objects.requireNonNull(
                System.getProperty("postgres.address"), "Set -Dpostgres.address to host:port");
        jdbcUrl = "jdbc:postgresql://" + address + "/fixture";
        assertThat(SpringBootVersion.getVersion()).isEqualTo(System.getProperty("compat.boot.version"));
        assertThat(System.getProperty("java.specification.version"))
                .isEqualTo(System.getProperty("compat.java.version"));
        context = new SpringApplicationBuilder(Application.class)
                .web(WebApplicationType.NONE)
                .run(
                        "--spring.datasource.url=" + jdbcUrl,
                        "--spring.datasource.username=fixture",
                        "--spring.datasource.password=fixture_local",
                        "--spring.jpa.hibernate.ddl-auto=create-drop");
        EntityManagerFactory factory = context.getBean(EntityManagerFactory.class);
        sessionFactory = factory.unwrap(SessionFactory.class);
        em = SharedEntityManagerCreator.createSharedEntityManager(factory);
        transactions = new TransactionTemplate(context.getBean(JpaTransactionManager.class));
        store = HibernateEmbeddingStore.builder(Document.class)
                .sessionFactory(sessionFactory)
                .databaseKind(DatabaseKind.POSTGRESQL)
                .build();
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
            Document document = new Document("commit", "芋道's pgvector", "zh", new float[] {1, 0, 0});
            em.persist(document);
            assertThat(em.contains(document)).isTrue();
        });

        assertThat(textFor("commit")).containsExactly("芋道's pgvector");
    }

    @Test
    void runsFilteredVectorHqlAndTheInstalledEmbeddingStore() {
        transactions.executeWithoutResult(status -> {
            em.persist(new Document("vector-in", "inside", "vector-zh", new float[] {0.8f, 0.6f, 0}));
            em.persist(new Document("vector-out", "outside", "vector-en", new float[] {1, 0, 0}));
        });
        List<Object[]> matches = em.createQuery(
                        "select d.id, cosine_distance(d.vector, :query) as distance "
                                + "from PgvectorDocument d where d.locale = :locale order by distance",
                        Object[].class)
                .setParameter("query", new float[] {1, 0, 0})
                .setParameter("locale", "vector-zh")
                .setMaxResults(1)
                .getResultList();

        assertThat(matches).singleElement().satisfies(row -> {
            assertThat(row[0]).isEqualTo("vector-in");
            assertThat(((Number) row[1]).doubleValue()).isCloseTo(0.2, within(0.001));
        });

        EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                .queryEmbedding(Embedding.from(new float[] {1, 0, 0}))
                .maxResults(10)
                .build();
        assertThat(store.search(request).matches())
                .extracting(EmbeddingMatch::embeddingId)
                .contains("vector-in", "vector-out");
    }

    @Test
    void filtersAndRemovesMetadataBeforeRanking() {
        UUID tenant = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
        store.addAll(
                List.of("metadata-in", "metadata-out", "metadata-missing"),
                List.of(
                        Embedding.from(new float[] {0.8f, 0.6f, 0}),
                        Embedding.from(new float[] {1, 0, 0}),
                        Embedding.from(new float[] {1, 0, 0})),
                List.of(
                        TextSegment.from("inside", new Metadata()
                                .put("locale", "metadata-zh")
                                .put("label", "芋道_100%")
                                .put("priority", 12)
                                .put("tenant", tenant)),
                        TextSegment.from("closer", new Metadata()
                                .put("locale", "metadata-zh")
                                .put("label", "芋道X100Y")
                                .put("priority", 2)),
                        TextSegment.from("missing", new Metadata().put("locale", "metadata-zh"))));

        Filter scope = metadataKey("locale").isEqualTo("metadata-zh");
        Filter present = metadataKey("priority").isGreaterThan(9)
                .and(metadataKey("priority").isLessThanOrEqualTo(12))
                .and(metadataKey("priority").isIn(10, 12))
                .and(metadataKey("label").containsString("_100%"))
                .and(metadataKey("tenant").isEqualTo(tenant));
        assertThat(matchingIds(scope.and(present), 1)).containsExactly("metadata-in");
        assertThat(matchingIds(scope.and(metadataKey("priority").isNotEqualTo(12)), 10))
                .containsExactlyInAnyOrder("metadata-out", "metadata-missing");
        assertThat(matchingIds(scope.and(metadataKey("priority").isNotIn(12, 2)), 10))
                .containsExactly("metadata-missing");

        store.removeAll(scope.and(present));
        assertThat(textFor("metadata-in")).isEmpty();
        assertThat(textFor("metadata-out")).containsExactly("closer");
        assertThat(textFor("metadata-missing")).containsExactly("missing");
    }

    private static List<String> matchingIds(Filter filter, int maxResults) {
        return store.search(EmbeddingSearchRequest.builder()
                        .queryEmbedding(Embedding.from(new float[] {1, 0, 0}))
                        .filter(filter)
                        .maxResults(maxResults)
                        .build())
                .matches().stream().map(EmbeddingMatch::embeddingId).toList();
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
    void rollsBackFlushedWriteOnTheActualDatabase() throws SQLException {
        transactions.executeWithoutResult(status -> {
            em.persist(new Document("flushed", "rolled back", "zh", new float[] {1, 0, 0}));
            em.flush();
            int rowsInTransaction = em.unwrap(Session.class)
                    .doReturningWork(connection -> databaseRowCount(connection, "flushed"));
            assertThat(rowsInTransaction).isEqualTo(1);
            status.setRollbackOnly();
        });

        try (Connection freshConnection = DriverManager.getConnection(jdbcUrl, "fixture", "fixture_local")) {
            assertThat(databaseRowCount(freshConnection, "flushed")).isZero();
        }
        assertThat(textFor("flushed")).isEmpty();
    }

    @Test
    void deletesMultipleEmbeddingIdsAndKeepsTheOthers() {
        transactions.executeWithoutResult(status -> {
            em.persist(new Document("delete-1", "first", "zh", new float[] {1, 0, 0}));
            em.persist(new Document("delete-2", "second", "zh", new float[] {0, 1, 0}));
            em.persist(new Document("keep", "kept", "zh", new float[] {0, 0, 1}));
        });

        store.removeAll(List.of("delete-1", "delete-2"));

        assertThat(textFor("delete-1")).isEmpty();
        assertThat(textFor("delete-2")).isEmpty();
        assertThat(textFor("keep")).containsExactly("kept");
    }

    private static int databaseRowCount(Connection connection, String id) throws SQLException {
        try (var statement = connection.prepareStatement(
                "select count(*) from lc4j_hibernate_pgvector_boot3 where id = ?")) {
            statement.setString(1, id);
            try (var result = statement.executeQuery()) {
                result.next();
                return result.getInt(1);
            }
        }
    }

    private static List<String> textFor(String id) {
        return em.createQuery("select d.text from PgvectorDocument d where d.id = :id", String.class)
                .setParameter("id", id)
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
    @Entity(name = "PgvectorDocument")
    @Table(name = "lc4j_hibernate_pgvector_boot3")
    public static class Document {

        @Id
        @Column(length = 64)
        private String id;

        @EmbeddedText
        @Column(length = 200)
        private String text;

        @MetadataAttribute
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
