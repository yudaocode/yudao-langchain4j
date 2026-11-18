package dev.langchain4j.store.embedding.hibernate.milvus;

import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.filter.comparison.IsEqualTo;
import dev.langchain4j.store.embedding.filter.comparison.IsGreaterThan;
import dev.langchain4j.store.embedding.filter.comparison.IsGreaterThanOrEqualTo;
import dev.langchain4j.store.embedding.filter.comparison.IsIn;
import dev.langchain4j.store.embedding.filter.comparison.IsLessThan;
import dev.langchain4j.store.embedding.filter.comparison.IsLessThanOrEqualTo;
import dev.langchain4j.store.embedding.filter.comparison.IsNotEqualTo;
import dev.langchain4j.store.embedding.filter.comparison.IsNotIn;
import dev.langchain4j.store.embedding.filter.logical.And;
import dev.langchain4j.store.embedding.filter.logical.Not;
import dev.langchain4j.store.embedding.filter.logical.Or;
import java.util.Collection;
import java.util.StringJoiner;
import java.util.function.Function;

/**
 * Compiles LangChain4j filters over mapped scalar properties.
 *
 * @author 芋道源码
 */
final class MilvusEntityFilter {

    private final Function<String, MilvusEntityMapping.Field> fieldLookup;

    MilvusEntityFilter(Function<String, MilvusEntityMapping.Field> fieldLookup) {
        this.fieldLookup = fieldLookup;
    }

    String expression(Filter filter) {
        if (filter instanceof And) {
            And and = (And) filter;
            return "(" + expression(and.left()) + " and " + expression(and.right()) + ")";
        }
        if (filter instanceof Or) {
            Or or = (Or) filter;
            return "(" + expression(or.left()) + " or " + expression(or.right()) + ")";
        }
        if (filter instanceof Not) {
            return "not (" + expression(((Not) filter).expression()) + ")";
        }
        if (filter instanceof IsEqualTo) {
            IsEqualTo f = (IsEqualTo) filter;
            return compare(f.key(), "==", f.comparisonValue());
        }
        if (filter instanceof IsNotEqualTo) {
            IsNotEqualTo f = (IsNotEqualTo) filter;
            return compare(f.key(), "!=", f.comparisonValue());
        }
        if (filter instanceof IsGreaterThan) {
            IsGreaterThan f = (IsGreaterThan) filter;
            return compare(f.key(), ">", f.comparisonValue());
        }
        if (filter instanceof IsGreaterThanOrEqualTo) {
            IsGreaterThanOrEqualTo f = (IsGreaterThanOrEqualTo) filter;
            return compare(f.key(), ">=", f.comparisonValue());
        }
        if (filter instanceof IsLessThan) {
            IsLessThan f = (IsLessThan) filter;
            return compare(f.key(), "<", f.comparisonValue());
        }
        if (filter instanceof IsLessThanOrEqualTo) {
            IsLessThanOrEqualTo f = (IsLessThanOrEqualTo) filter;
            return compare(f.key(), "<=", f.comparisonValue());
        }
        if (filter instanceof IsIn) {
            IsIn f = (IsIn) filter;
            return membership(f.key(), "in", f.comparisonValues());
        }
        if (filter instanceof IsNotIn) {
            IsNotIn f = (IsNotIn) filter;
            return membership(f.key(), "not in", f.comparisonValues());
        }
        throw new IllegalArgumentException("Unsupported Milvus filter: " + filter.getClass().getName());
    }

    private String compare(String key, String op, Object value) {
        MilvusEntityMapping.Field field = fieldLookup.apply(key);
        return field.column + " " + op + " " + MilvusEntityMapping.JSON.toJson(field.value(value));
    }

    private String membership(String key, String op, Collection<?> values) {
        MilvusEntityMapping.Field field = fieldLookup.apply(key);
        StringJoiner list = new StringJoiner(", ", "[", "]");
        for (Object value : values) {
            list.add(MilvusEntityMapping.JSON.toJson(field.value(value)));
        }
        return field.column + " " + op + " " + list;
    }
}
