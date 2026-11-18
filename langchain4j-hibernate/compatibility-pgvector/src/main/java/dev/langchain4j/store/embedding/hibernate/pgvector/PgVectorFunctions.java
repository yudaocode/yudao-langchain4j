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
        for (PgVectorType.Storage storage : PgVectorType.Storage.values()) {
            register(metadata, storage, "cosine", "<=>");
            register(metadata, storage, "l2", "<->");
            register(metadata, storage, "negative_inner_product", "<#>");
            register(metadata, storage, "l1", "<+>");
        }
    }

    private void register(MetadataBuilder metadata, PgVectorType.Storage storage, String metric, String operator) {
        String sql = "(?1 " + operator + " cast(?2 as " + storage.sqlType + "))";
        metadata.applySqlFunction(storage.functionPrefix + metric, new SQLFunctionTemplate(StandardBasicTypes.DOUBLE, sql));
    }
}
