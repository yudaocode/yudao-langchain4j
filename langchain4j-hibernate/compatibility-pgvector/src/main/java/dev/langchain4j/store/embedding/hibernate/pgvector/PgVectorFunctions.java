package dev.langchain4j.store.embedding.hibernate.pgvector;

import org.hibernate.boot.MetadataBuilder;
import org.hibernate.boot.spi.MetadataBuilderContributor;
import org.hibernate.dialect.function.SQLFunctionTemplate;
import org.hibernate.type.StandardBasicTypes;

/**
 * Registers SQL renderers for PostgreSQL vector distance in Hibernate 5.
 *
 * @author 芋道源码
 */
public final class PgVectorFunctions implements MetadataBuilderContributor {
    @Override
    public void contribute(MetadataBuilder metadata) {
        // Each template parameter appears once for Hibernate 5's positional JDBC binding.
        metadata.applySqlFunction("lc_pg_metadata_text", new SQLFunctionTemplate(StandardBasicTypes.STRING,
                jsonScalar("string")));
        metadata.applySqlFunction("lc_pg_metadata_number", new SQLFunctionTemplate(StandardBasicTypes.BIG_DECIMAL,
                "cast(" + jsonScalar("number") + " as numeric)"));
        for (PgVectorType.Storage storage : PgVectorType.Storage.values()) {
            register(metadata, storage, "cosine", "<=>");
            register(metadata, storage, "l2", "<->");
            register(metadata, storage, "negative_inner_product", "<#>");
            register(metadata, storage, "l1", "<+>");
        }
    }

    private String jsonScalar(String jsonType) {
        return "(jsonb_path_query_first(jsonb_extract_path(cast(?1 as jsonb), ?2), "
                + "cast(('$ ' || chr(63) || ' (@.type() == \"" + jsonType + "\")') as jsonpath)) #>> '{}')";
    }

    private void register(MetadataBuilder metadata, PgVectorType.Storage storage, String metric, String operator) {
        String sql = "(?1 " + operator + " cast(?2 as " + storage.sqlType + "))";
        metadata.applySqlFunction(storage.functionPrefix + metric, new SQLFunctionTemplate(StandardBasicTypes.DOUBLE, sql));
    }
}
