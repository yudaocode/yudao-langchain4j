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
        metadata.applySqlFunction("lc_cosine", distance("<=>"));
        metadata.applySqlFunction("lc_l2", distance("<->"));
        metadata.applySqlFunction("lc_negative_inner_product", distance("<#>"));
        metadata.applySqlFunction("lc_l1", distance("<+>"));
    }

    private SQLFunctionTemplate distance(String operator) {
        return new SQLFunctionTemplate(StandardBasicTypes.DOUBLE, "(?1 " + operator + " cast(?2 as vector))");
    }
}
