package dev.langchain4j.store.embedding.hibernate;

import dev.langchain4j.store.embedding.hibernate.pgvector.PgVectorFunctions;
import dev.langchain4j.store.embedding.hibernate.pgvector.PgVectorType;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;
import javax.sql.DataSource;
import org.hibernate.SessionFactory;
import org.hibernate.boot.MetadataBuilder;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.dialect.PostgreSQL10Dialect;

/**
 * Configures a Hibernate 5 entity mapping and its PostgreSQL schema.
 *
 * @author 芋道源码
 */
final class PostgresStoreSchema {

    private PostgresStoreSchema() {}

    static SessionFactory build(DataSource source, String table, int dimension, boolean createTable, boolean dropTable,
            boolean createIndex, DistanceFunction distance, String indexType, String indexOptions) {
        String[] identifier = table.split("\\.", -1);
        if (identifier.length > 2) {
            throw new IllegalArgumentException("table must be a PostgreSQL table or schema.table");
        }
        for (String part : identifier) {
            if (!part.matches("[a-z_][a-z_0-9]{0,62}")) {
                throw new IllegalArgumentException("Invalid PostgreSQL table identifier: " + part);
            }
        }
        if (dimension <= 0 || dimension > 16000) {
            throw new IllegalArgumentException("dimension must be between 1 and 16000");
        }
        String index = createIndex ? index(table, distance, indexType, indexOptions) : null;
        execute(source, table, dimension, createTable, dropTable, index);
        StandardServiceRegistry registry = new StandardServiceRegistryBuilder()
                .applySetting(AvailableSettings.DATASOURCE, source)
                .applySetting(AvailableSettings.DIALECT, PostgreSQL10Dialect.class)
                .applySetting(AvailableSettings.HBM2DDL_AUTO, "none")
                .build();
        try {
            String mapping = mapping(identifier);
            MetadataBuilder metadata = new MetadataSources(registry)
                    .addInputStream(new ByteArrayInputStream(mapping.getBytes(StandardCharsets.UTF_8)))
                    .getMetadataBuilder();
            new PgVectorFunctions().contribute(metadata);
            return metadata.build().buildSessionFactory();
        } catch (RuntimeException e) {
            StandardServiceRegistryBuilder.destroy(registry);
            throw e;
        }
    }

    private static void execute(DataSource source, String table, int dimension, boolean createTable, boolean dropTable,
            String index) {
        try (Connection connection = source.getConnection()) {
            if (!"PostgreSQL".equals(connection.getMetaData().getDatabaseProductName())) {
                throw new IllegalArgumentException("Schema initialization requires a PostgreSQL DataSource");
            }
            if (!dropTable && !createTable && index == null) {
                return;
            }
            boolean autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try (Statement statement = connection.createStatement()) {
                if (createTable) {
                    statement.execute("create extension if not exists vector");
                }
                if (dropTable) {
                    statement.execute("drop table if exists " + table);
                }
                if (createTable) {
                    statement.execute("create table if not exists " + table
                            + " (id uuid primary key, text text, embedding vector(" + dimension + ") not null, metadata jsonb)");
                }
                if (index != null) {
                    statement.execute(index);
                }
                connection.commit();
            } catch (SQLException e) {
                try {
                    connection.rollback();
                } catch (SQLException rollback) {
                    e.addSuppressed(rollback);
                }
                throw e;
            } finally {
                connection.setAutoCommit(autoCommit);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Unable to initialize the PostgreSQL embedding schema", e);
        }
    }

    private static String index(String table, DistanceFunction distance, String type, String options) {
        String method = type == null ? "ivfflat" : type.toLowerCase(Locale.ROOT);
        if (!method.equals("ivfflat") && !method.equals("hnsw")) {
            throw new IllegalArgumentException("PostgreSQL vector index type must be ivfflat or hnsw");
        }
        if (options != null && !options.trim().isEmpty()
                && !options.matches("\\s*(lists|m|ef_construction)\\s*=\\s*[1-9][0-9]*"
                        + "(\\s*,\\s*(lists|m|ef_construction)\\s*=\\s*[1-9][0-9]*)*\\s*")) {
            throw new IllegalArgumentException("Invalid PostgreSQL vector index options");
        }
        String name = table.substring(table.lastIndexOf('.') + 1) + "_" + method + "_index";
        if (name.length() > 63) {
            name = name.substring(0, 54) + "_" + String.format("%08x", name.hashCode());
        }
        String operatorClass;
        switch (distance) {
            case COSINE: operatorClass = "vector_cosine_ops"; break;
            case EUCLIDEAN:
            case EUCLIDEAN_SQUARED: operatorClass = "vector_l2_ops"; break;
            case MANHATTAN: operatorClass = "vector_l1_ops"; break;
            case INNER_PRODUCT:
            case NEGATIVE_INNER_PRODUCT: operatorClass = "vector_ip_ops"; break;
            default: throw new IllegalArgumentException("Unsupported dense PostgreSQL index distance: " + distance);
        }
        return "create index if not exists " + name + " on " + table + " using " + method
                + " (embedding " + operatorClass + ")"
                + (options == null || options.trim().isEmpty() ? "" : " with (" + options + ")");

    }

    private static String mapping(String[] name) {
        String table = name[name.length - 1];
        String schema = name.length == 2 ? " schema=\"" + name[0] + "\"" : "";
        return "<?xml version=\"1.0\"?>"
                + "<!DOCTYPE hibernate-mapping PUBLIC \"-//Hibernate/Hibernate Mapping DTD 3.0//EN\" "
                + "\"http://www.hibernate.org/dtd/hibernate-mapping-3.0.dtd\">"
                + "<hibernate-mapping default-access=\"field\"><class name=\"" + EmbeddingEntity.class.getName()
                + "\" table=\"" + table + "\"" + schema + ">"
                + "<id name=\"id\" type=\"pg-uuid\"/>"
                + "<property name=\"text\" type=\"string\"/>"
                + "<property name=\"embedding\" type=\"" + PgVectorType.class.getName() + "\"/>"
                + "<property name=\"metadata\" type=\"" + JsonbMetadataType.class.getName() + "\"/>"
                + "</class></hibernate-mapping>";
    }
}
