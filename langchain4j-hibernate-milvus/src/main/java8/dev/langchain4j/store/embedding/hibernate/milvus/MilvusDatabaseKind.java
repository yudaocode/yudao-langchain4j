package dev.langchain4j.store.embedding.hibernate.milvus;

import dev.langchain4j.store.embedding.hibernate.DatabaseKind;
import dev.langchain4j.store.embedding.hibernate.DistanceFunction;
import dev.langchain4j.store.embedding.hibernate.HibernateEmbeddingStore;
import java.util.Collections;
import java.util.Map;

/**
 * The database kind descriptor for Milvus. This Java 8 artifact uses
 * {@link HibernateMilvusStore} for actual Milvus operations.
 *
 * @see HibernateEmbeddingStore.BaseBuilder#databaseKind(DatabaseKind)
 */
public final class MilvusDatabaseKind implements DatabaseKind {

    public static final DatabaseKind INSTANCE = new MilvusDatabaseKind();

    private MilvusDatabaseKind() {}

    @Override
    public String createIndexDDL(
            final DistanceFunction distanceFunction,
            final String indexType,
            final String table,
            final String embeddingColumn,
            final String indexOptions) {
        // Milvus creates the vector index together with the collection, see tableIndexOptions()
        return null;
    }

    /**
     * Returns the options of the vector index of the collection.
     * <p>
     * A Milvus collection always has a vector index, and a search must use the metric of that index. So the metric of
     * the distance function is always returned, also without {@code createIndex(true)}. The index type and the index
     * options are only added with {@code createIndex(true)}, like for the relational databases.
     *
     * @throws IllegalArgumentException if Milvus does not support the distance function
     */
    @Override
    public String tableIndexOptions(
            final boolean createIndex,
            final DistanceFunction distanceFunction,
            final String indexType,
            final String table,
            final String embeddingColumn,
            final String indexOptions) {
        final String metric = "metric=" + metric(distanceFunction);
        if (!createIndex) {
            return metric;
        }
        return metric + (indexType == null ? "" : (",type=" + indexType))
                + (indexOptions == null ? "" : ("," + indexOptions));
    }

    private String metric(DistanceFunction distanceFunction) {
        return switch (distanceFunction) {
            case COSINE -> "cosine";
            case INNER_PRODUCT, NEGATIVE_INNER_PRODUCT -> "ip";
            case EUCLIDEAN -> "l2";
            default ->
                throw new IllegalArgumentException("Milvus does not support the distance function: " + distanceFunction);
        };
    }

    @Override
    public String getSetupSql() {
        return null; // Milvus needs no extension or feature flag to be enabled before vectors can be used
    }

    /**
     * Returns the Milvus session consistency level, so that a search sees all embeddings that this application added
     * or removed before it. Without a consistency level, Milvus uses the default level of the collection, and a search
     * right after {@code add(...)} can miss the new embeddings.
     *
     * @return the session consistency level as a Hibernate database query hint
     */
    @Override
    public Map<String, Object> defaultQueryHints() {
        return Collections.<String, Object>singletonMap("hibernate.query.database", "consistencyLevel=SESSION");
    }

    @Override
    public String createJdbcUrl(final String host, final int port, final String database) {
        final StringBuilder builder = new StringBuilder();
        builder.append("jdbc:milvus://");
        builder.append(host);
        if (port > 0) {
            builder.append(":");
            builder.append(port);
        }
        builder.append("/");
        builder.append(database);
        return builder.toString();
    }

    @Override
    public boolean isJdbcUrl(final String jdbcUrl) {
        return jdbcUrl.startsWith("jdbc:milvus://");
    }
}
